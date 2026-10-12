package sk.drabikp.gigsynchub.bandzone.portal.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A stand-in for Bandzone's band admin as its pages talk to it over HTTP (recorded from the real site,
 * 2026-10-10): the login form and its cookie; the pages' forms, whose widgets send the whole form back
 * through AJAX and get JSON — a picker's search {@code {items, create}}, a pick or an upload the form again
 * ({@code snippets}), a finished form {@code {flash, redirect}}; the poster uploader
 * ({@code /api.upload/default}); the new-concert wizard (with the "similar concerts?" step), the edit
 * form, the performers tab (its remove dialogs, its band search with another concert's profile-less band)
 * and the delete tab; the band page's gig list and a deleted concert's 404. Keeps what it is sent, so
 * the client's read-backs see what was stored.
 */
final class BandzoneHttpFixture implements AutoCloseable {

    static final String LOGIN = "tester@example.test";
    static final String PASSWORD = "secret-password";
    static final String SLUG = "testband";
    static final String BAND_NAME = "Test Band";

    private static final String LOGOUT = "<a href=\"/logout\">Odhlásit</a>";
    private static final Pattern CONCERT = Pattern.compile("/koncert/(\\d+)(-[a-z0-9-]*)?(/update)?");

    /** Bandzone's towns: id, name, description — three Hranice. */
    private static final List<String[]> TOWNS = List.of(
            new String[] {"4109", "Praha", ""},
            new String[] {"1603", "Hranice", "okres Přerov, kraj Olomoucký"},
            new String[] {"1604", "Hranice", "okres Cheb, kraj Karlovarský"},
            new String[] {"1605", "Hranice", "okres Rychnov nad Kněžnou, kraj Královéhradecký"});
    private static final Map<String, String> CLUBS = Map.of("125", "Lucerna Music Bar", "126", "Lucerna Velký sál");
    /** Band profiles; 999 is another concert's profile-less band (never to be linked). */
    private static final Map<String, String> BANDS = new LinkedHashMap<>(Map.of(
            "1", BAND_NAME, "35516", "Incertus Posterus", "999", "Stubby"));

    /** A concert as Bandzone stores it. */
    static final class Concert {
        final Map<String, String> fields = new ConcurrentHashMap<>();
        final List<String[]> performers = new CopyOnWriteArrayList<>();   // {gigBandId, bandId, name}
        volatile boolean cancelled;
        volatile boolean deleted;
        volatile String poster;

        String value(String name) {
            return fields.getOrDefault(name, "");
        }
    }

    private final HttpServer server;
    final Map<String, Concert> concerts = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> drafts = new ConcurrentHashMap<>();
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicInteger ids = new AtomicInteger(500);
    final AtomicInteger logins = new AtomicInteger();
    /** Every form sent: path, then its fields. */
    final List<Map.Entry<String, Map<String, List<String>>>> posts = new CopyOnWriteArrayList<>();
    /**
     * What step one leads to — all three shapes Bandzone has been seen to use:
     * the question, no question but still at the question's address (live 2026-10-11), or
     * straight to the info step.
     */
    enum AfterStepOne { QUESTION, INFO_AT_QUESTION_ADDRESS, INFO }

    /** Which of those this fixture plays. */
    volatile AfterStepOne afterStepOne = AfterStepOne.QUESTION;
    /** Whether a remove dialog asked through AJAX answers only "go there" (the concert's address changed). */
    volatile boolean dialogsRedirect;

    BandzoneHttpFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serve);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ---------------------------------------------------------------- dispatch

    private void serve(HttpExchange exchange) throws IOException {
        try {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            Map<String, List<String>> query = parse(uri.getRawQuery());
            boolean ajax = exchange.getRequestHeaders().containsKey("X-requested-with");
            if ("POST".equals(exchange.getRequestMethod())) {
                String type = exchange.getRequestHeaders().getFirst("Content-Type");
                byte[] body = exchange.getRequestBody().readAllBytes();
                if (path.equals("/api.upload/default")) {
                    upload(exchange, query, type, body);
                    return;
                }
                Map<String, List<String>> fields = parse(new String(body, StandardCharsets.UTF_8));
                posts.add(Map.entry(uri.toString(), fields));
                post(exchange, path, query, fields, ajax);
            } else {
                get(exchange, path, query, ajax);
            }
        } catch (RuntimeException e) {
            send(exchange, 500, "text/plain", e.toString());
        }
    }

    private void get(HttpExchange exchange, String path, Map<String, List<String>> query, boolean ajax) throws IOException {
        if (path.equals("/poster.png")) {
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        } else if (path.equals("/fan/tester/aktivity.html")) {
            page(exchange, loggedIn(exchange) ? LOGOUT : "");
        } else if (path.equals("/")) {
            page(exchange, loggedIn(exchange) ? LOGOUT : "<a href=\"/login.html\">Přihlásit</a>");
        } else if (path.equals("/login.html")) {
            page(exchange, loginForm());
        } else if (path.equals("/" + SLUG)) {
            page(exchange, "<meta property=\"og:title\" content=\"" + BAND_NAME + "\">" + gigList(first(query, "gy")));
        } else if (path.equals("/koncert/zalozit.html")) {
            requireLogin(exchange);
            page(exchange, stepOne(Map.of()));
        } else if (path.equals("/koncert/zalozit-podobne-koncerty.html")) {
            // Nothing similar to ask about: Bandzone stays at this address and serves the info step.
            page(exchange, afterStepOne == AfterStepOne.QUESTION
                    ? "<form action=\"/koncert/zalozit-podobne-koncerty.html?key=" + first(query, "key")
                    + "&amp;do=similarGigsForm-submit\" method=\"post\"><input type=\"submit\" name=\"new\" value=\"Ne, "
                    + "zakládám jiný koncert\"></form>"
                    : infoForm(first(query, "key")));
        } else if (path.equals("/koncert/zalozit-info.html")) {
            page(exchange, infoForm(first(query, "key")));
        } else {
            Matcher concert = CONCERT.matcher(path);
            if (!concert.matches() || !concerts.containsKey(concert.group(1)) || concerts.get(concert.group(1)).deleted) {
                send(exchange, 404, "text/html", "<html><body>Nenalezeno</body></html>");
                return;
            }
            String id = concert.group(1);
            if (concert.group(3) == null) {
                page(exchange, "<h1>" + concerts.get(id).value("name") + "</h1>");
                return;
            }
            requireLogin(exchange);
            String tab = first(query, "updateTabs-at");
            if ("editor-show".equals(first(query, "do"))) {
                String dialog = removeDialog(id, first(query, "ei"));
                if (ajax && dialogsRedirect) {
                    json(exchange, "{\"redirect\":" + quote(base(exchange) + "/koncert/" + id + "-moved/update?do=editor-show"
                            + "&updateTabs-at=updateBands&ef=gigBand&em=delete&ei=" + first(query, "ei")) + "}");
                } else if (ajax) {
                    json(exchange, "{\"snippets\":{\"snippet-updateTabs-tabContent\":" + quote(lineupTab(id) + dialog) + "}}");
                } else {
                    page(exchange, lineupTab(id) + dialog);
                }
            } else if ("updateBands".equals(tab)) {
                page(exchange, lineupTab(id));
            } else if ("profileDeleteForm".equals(tab)) {
                page(exchange, "<form action=\"/koncert/" + id + "/update?updateTabs-at=profileDeleteForm&amp;do=updateTabs-"
                        + "profileDeleteForm-submit\" method=\"post\"><input type=\"submit\" name=\"cancelGig\" value=\"Označit "
                        + "koncert jako zrušený\"><input type=\"submit\" name=\"delete\" value=\"Smazat profil akce\"></form>");
            } else {
                page(exchange, editForm(id, concerts.get(id).fields, concerts.get(id).poster));
            }
        }
    }

    private void post(HttpExchange exchange, String path, Map<String, List<String>> query, Map<String, List<String>> f,
                      boolean ajax) throws IOException {
        String signal = first(query, "do");
        if (signal.equals("loginForm-submit")) {
            if (LOGIN.equals(first(f, "login")) && PASSWORD.equals(first(f, "password")) && f.containsKey("doLogin")) {
                String session = "s" + ids.incrementAndGet();
                sessions.add(session);
                logins.incrementAndGet();
                exchange.getResponseHeaders().add("Set-Cookie", "PHPSESSID=" + session + "; Path=/; HttpOnly");
                redirect(exchange, "/fan/tester/aktivity.html");
            } else {
                page(exchange, "<ul class=\"error\"><li>Špatné heslo</li></ul>" + loginForm());
            }
            return;
        }
        requireLogin(exchange);
        if (!ajax) {
            throw new IllegalStateException("a form sent without AJAX: " + path);
        }
        Map<String, String> fields = single(f);
        switch (signal) {
            case "gigDatePlaceBandsForm-submit" -> stepOnePost(exchange, fields);
            case "similarGigsForm-submit" -> json(exchange, "{\"redirect\":" + quote("/koncert/zalozit-info.html?key="
                    + first(query, "key")) + "}");
            case "gigInfoForm-submit" -> create(exchange, first(query, "key"), fields);
            case "updateTabs-updateForm-submit" -> editPost(exchange, concertId(path), fields);
            case "addBandForm-submit" -> addBandPost(exchange, concertId(path), f);
            case "editor-form-submit" -> {
                Concert concert = concerts.get(concertId(path));
                concert.performers.removeIf(p -> p[0].equals(first(query, "ei")));
                json(exchange, "{\"flash\":[{\"message\":\"odstraněna\"}],\"redirect\":\"/koncert/x/update\"}");
            }
            case "updateTabs-profileDeleteForm-submit" -> {
                Concert concert = concerts.get(concertId(path));
                if (fields.containsKey("cancelGig") && fields.containsKey("delete")) {
                    throw new IllegalStateException("both cancel and delete pressed");
                }
                if (fields.containsKey("cancelGig")) {
                    concert.cancelled = true;
                } else if (fields.containsKey("delete")) {
                    concert.deleted = true;
                }
                json(exchange, "{\"redirect\":\"/" + SLUG + "\"}");
            }
            default -> throw new IllegalStateException("unknown form " + signal);
        }
    }

    // ---------------------------------------------------------------- the wizard

    private void stepOnePost(HttpExchange exchange, Map<String, String> fields) throws IOException {
        if (fields.containsKey("cityId__container[searchButton]")) {
            json(exchange, townItems(fields.get("cityId__container[textInput]")));
        } else if (fields.containsKey("cityId__container[selectButton]")) {
            json(exchange, snippet(stepOne(fields)));
        } else if (fields.containsKey("continue")) {
            if (fields.getOrDefault("cityId", "").isBlank() || fields.getOrDefault("start[date]", "").isBlank()) {
                json(exchange, snippet("<ul class=\"error\"><li>Vyplňte město</li></ul>" + stepOne(fields)));
                return;
            }
            String key = "k" + ids.incrementAndGet();
            drafts.put(key, new LinkedHashMap<>(fields));
            json(exchange, "{\"redirect\":" + quote(afterStepOne == AfterStepOne.INFO
                    ? "/koncert/zalozit-info.html?key=" + key
                    : "/koncert/zalozit-podobne-koncerty.html?key=" + key) + "}");
        } else {
            throw new IllegalStateException("step one without a button");
        }
    }

    private void create(HttpExchange exchange, String key, Map<String, String> fields) throws IOException {
        Map<String, String> draft = drafts.remove(key);
        if (draft == null || fields.getOrDefault("name", "").isBlank()) {
            json(exchange, snippet("<ul class=\"error\"><li>Vyplňte název</li></ul>" + infoForm(key)));
            return;
        }
        Concert concert = new Concert();
        draft.forEach((k, v) -> {
            if (!k.contains("Button") && !k.equals("continue") && !k.startsWith("bandIds")) {
                concert.fields.put(k, v);
            }
        });
        fields.forEach((k, v) -> {
            if (!k.equals("send")) {
                concert.fields.put(k, v);
            }
        });
        concert.performers.add(new String[] {String.valueOf(ids.incrementAndGet()), "1", BAND_NAME});
        String id = String.valueOf(ids.incrementAndGet());
        concerts.put(id, concert);
        json(exchange, "{\"flash\":[{\"message\":\"Koncert byl uložen.\"}],\"redirect\":" + quote(base(exchange)
                + "/koncert/" + id + "?_fid=a7f9") + "}");
    }

    // ---------------------------------------------------------------- the edit form

    private void editPost(HttpExchange exchange, String id, Map<String, String> fields) throws IOException {
        Concert concert = concerts.get(id);
        if (fields.containsKey("cityId__container[searchButton]")) {
            json(exchange, townItems(fields.get("cityId__container[textInput]")));
        } else if (fields.containsKey("venueId__container[searchButton]")) {
            String typed = fields.get("venueId__container[textInput]").toLowerCase();
            json(exchange, "{\"items\":[" + CLUBS.entrySet().stream().filter(c -> c.getValue().toLowerCase().startsWith(typed))
                    .map(c -> item(c.getKey(), c.getValue(), c.getValue(), "<div class=\"profileLink club\"><h4 class=\"title\">"
                            + c.getValue() + "</h4></div>")).collect(Collectors.joining(",")) + "],\"create\":null}");
        } else if (fields.containsKey("cityId__container[selectButton]") || fields.containsKey("venueId__container[selectButton]")
                || fields.containsKey("_FileUploadReloadSubmit_")) {
            json(exchange, snippet(editForm(id, fields, concert.poster)));
        } else if (fields.containsKey("send")) {
            if (fields.getOrDefault("cityId", "").isBlank()) {
                json(exchange, snippet("<ul class=\"error\"><li>Vyberte město</li></ul>" + editForm(id, fields, concert.poster)));
                return;
            }
            fields.forEach((k, v) -> {
                if (!k.contains("Button") && !k.equals("send")) {
                    concert.fields.put(k, v);
                }
            });
            json(exchange, "{\"flash\":[{\"message\":\"Změny v akci byly uloženy.\"}],\"redirect\":" + quote("/koncert/"
                    + id + "/update?updateTabs-at=updateForm") + "}");
        } else {
            throw new IllegalStateException("edit form without a button");
        }
    }

    private void upload(HttpExchange exchange, Map<String, List<String>> query, String type, byte[] body) throws IOException {
        String text = new String(body, StandardCharsets.ISO_8859_1);
        Matcher file = Pattern.compile("name=\"profileImage\\[uploader]\"; filename=\"([^\"]+)\"").matcher(text);
        if (type == null || !type.startsWith("multipart/form-data") || !file.find()) {
            send(exchange, 400, "text/plain", "no file");
            return;
        }
        String concertId = first(query, "_upload_id").replace("upload-", "");
        concerts.get(concertId).poster = file.group(1);
        send(exchange, 201, "application/json", "[{\"name\":" + quote(file.group(1)) + ",\"url\":\"x\",\"meta\":{}}]");
    }

    // ---------------------------------------------------------------- the performers tab

    private void addBandPost(HttpExchange exchange, String id, Map<String, List<String>> f) throws IOException {
        Map<String, String> fields = single(f);
        List<String> queued = f.entrySet().stream().filter(e -> e.getKey().startsWith("bandIds["))
                .map(e -> e.getValue().getFirst()).collect(Collectors.toCollection(ArrayList::new));
        if (fields.containsKey("bandIds__addCompleter__container[searchButton]")) {
            String typed = fields.get("bandIds__addCompleter__container[textInput]");
            json(exchange, "{\"items\":[" + BANDS.entrySet().stream()
                    .filter(b -> b.getValue().equalsIgnoreCase(typed) || (b.getKey().equals("999") && typed.startsWith("Stub")))
                    .map(b -> item(b.getKey(), b.getValue(), "rock / Praha", "<div class=\"profileLink band "
                            + (b.getKey().equals("999") ? "bandStub" : "") + "\"><h4 class=\"title\">" + b.getValue() + "</h4></div>"))
                    .collect(Collectors.joining(",")) + "],\"create\":" + item("", typed, "", "<div class=\"item createItem\">+</div>")
                    + "}");
        } else if (fields.containsKey("bandIds__addCompleter__container[selectButton]")) {
            queued.add(fields.get("bandIds__addCompleter"));
            json(exchange, snippet(addForm(id, queued, false)));
        } else if (fields.containsKey("bandIds__addCompleter__container[createItemButton]")) {
            json(exchange, snippet(addForm(id, queued, true)));
        } else if (fields.containsKey("bandIds__addCompleter__createItem[create]")) {
            String stubId = String.valueOf(ids.incrementAndGet());
            BANDS.put(stubId, fields.get("bandIds__addCompleter__createItem[name]"));
            queued.add(stubId);
            json(exchange, snippet(addForm(id, queued, false)));
        } else if (fields.containsKey("add")) {
            for (String band : queued) {
                concerts.get(id).performers.add(new String[] {String.valueOf(ids.incrementAndGet()), band, BANDS.get(band)});
            }
            json(exchange, "{\"redirect\":" + quote("/koncert/" + id + "/update?updateTabs-at=updateBands") + "}");
        } else {
            throw new IllegalStateException("add form without a button");
        }
    }

    // ---------------------------------------------------------------- pages

    private String loginForm() {
        return "<form action=\"/login.html?do=loginForm-submit\" method=\"post\"><input type=\"text\" name=\"login\">"
                + "<input type=\"password\" name=\"password\"><input type=\"checkbox\" name=\"permanent\">"
                + "<input type=\"submit\" name=\"doLogin\" value=\"Přihlásit se\"><input type=\"hidden\" name=\"bl\" value=\"\"></form>";
    }

    private String stepOne(Map<String, String> values) {
        boolean picked = !values.getOrDefault("cityId", "").isBlank();
        return "<form action=\"/koncert/zalozit.html?do=gigDatePlaceBandsForm-submit\" method=\"post\">"
                + "<input type=\"submit\" name=\"continue\" value=\"Pokračovat\">"
                + dates(values) + cityFields(values)
                + (picked ? "<input type=\"hidden\" name=\"venueId\" value=\"\"><input type=\"text\" name=\"venueId__container"
                + "[textInput]\" value=\"\">" : "")
                + "<input type=\"hidden\" name=\"bandIds[0]\" value=\"1\"></form>";
    }

    private String infoForm(String key) {
        return "<form action=\"/koncert/zalozit-info.html?key=" + key + "&amp;do=gigInfoForm-submit\" method=\"post\">"
                + infoFields(Map.of(), true) + "<input type=\"submit\" name=\"send\" value=\"Vytvořit\"></form>";
    }

    private String editForm(String id, Map<String, String> values, String poster) {
        return "<form action=\"/koncert/" + id + "/update?updateTabs-at=updateForm&amp;do=updateTabs-updateForm-submit\" "
                + "method=\"post\"><input type=\"submit\" name=\"send\" value=\"Uložit\">"
                + input("name", values) + dates(values) + cityFields(values)
                + "<input type=\"hidden\" name=\"venueId\" value=\"" + esc(values.getOrDefault("venueId", "")) + "\">"
                + input("venueId__container[textInput]", values)
                + "<input type=\"submit\" name=\"venueId__container[searchButton]\" value=\"Hledat\">"
                + "<input type=\"submit\" name=\"venueId__container[selectButton]\" value=\"Vybrat\" disabled>"
                + infoFields(values, false)
                + "<div class=\"fileupload\" data-url=\"/api.upload/default?_upload_id=upload-" + id + "\"></div>"
                + "<input type=\"submit\" name=\"_FileUploadReloadSubmit_\" value=\"\">"
                + "<input type=\"file\" name=\"profileImage[uploader]\">"
                + (poster == null ? "" : "<input type=\"text\" name=\"profileImage_cropper__cropContainer[cropW]\" value=\"58\">")
                + "<input type=\"hidden\" name=\"uploader_upload[id_1]\" value=\"upload-" + id + "\"></form>";
    }

    private String lineupTab(String id) {
        StringBuilder html = new StringBuilder("<section id=\"gigEditLineupBands\">");
        for (String[] performer : concerts.get(id).performers) {
            html.append("<div class=\"editable\" id=\"__editable-gigBand-all-").append(performer[0]).append("\">")
                    .append("<a class=\"delete ajax editorLink\" href=\"/koncert/").append(id)
                    .append("/update?updateTabs-at=updateBands&amp;do=editor-show&amp;ef=gigBand&amp;em=delete&amp;ei=")
                    .append(performer[0]).append("#snippet--x\">odstranit z koncertu</a>")
                    .append("<div class=\"profileLink band\"><h4 class=\"title\">").append(esc(performer[2])).append("</h4></div></div>");
        }
        return html.append("</section>").append(addForm(id, List.of(), false)).toString();
    }

    private String addForm(String id, List<String> queued, boolean creating) {
        StringBuilder html = new StringBuilder("<form action=\"/koncert/" + id
                + "/update?updateTabs-at=updateBands&amp;do=addBandForm-submit\" method=\"post\">"
                + "<input type=\"submit\" name=\"add\" value=\"Přidat vystupující kapely\">");
        for (int i = 0; i < queued.size(); i++) {
            html.append("<input type=\"hidden\" name=\"bandIds[").append(i).append("]\" value=\"").append(queued.get(i)).append("\">");
        }
        if (creating) {
            html.append("<input type=\"hidden\" name=\"bandIds__addCompleter\" value=\"__create__\">")
                    .append("<input type=\"text\" name=\"bandIds__addCompleter__createItem[name]\" value=\"\">")
                    .append("<input type=\"submit\" name=\"bandIds__addCompleter__createItem[create]\" value=\"Přidat\">");
        } else {
            html.append("<input type=\"hidden\" name=\"bandIds__addCompleter\" value=\"\">")
                    .append("<input type=\"text\" name=\"bandIds__addCompleter__container[textInput]\" value=\"\">")
                    .append("<input type=\"submit\" name=\"bandIds__addCompleter__container[searchButton]\" value=\"Hledat\">")
                    .append("<input type=\"submit\" name=\"bandIds__addCompleter__container[selectButton]\" value=\"Přidat\" disabled>")
                    .append("<input type=\"submit\" name=\"bandIds__addCompleter__container[createItemButton]\" value=\"Nová\" disabled>");
        }
        return html.append("</form>").toString();
    }

    private String removeDialog(String id, String gigBandId) {
        return "<form action=\"/koncert/" + id + "/update?updateTabs-at=updateBands&amp;do=editor-form-submit&amp;ef=gigBand"
                + "&amp;em=delete&amp;ei=" + gigBandId + "#snippet--x\" method=\"post\">"
                + "<input type=\"submit\" name=\"delete\" value=\"Odebrat\"></form>";
    }

    private String gigList(String year) {
        StringBuilder html = new StringBuilder();
        concerts.forEach((id, concert) -> {
            boolean planned = year.isEmpty();
            if (!concert.deleted && planned == !concert.value("start[date]").endsWith("2020")) {
                html.append("<article class=\"gig").append(concert.cancelled ? " gig--canceled" : "").append("\">")
                        .append("<a class=\"gig__link\" href=\"/koncert/").append(id).append("-x\">")
                        .append(esc(concert.value("name"))).append("</a></article>");
            }
        });
        return html.toString();
    }

    private static String dates(Map<String, String> values) {
        return input("start[date]", values) + input("start[time]", values) + input("end[date]", values)
                + input("end[time]", values);
    }

    private static String cityFields(Map<String, String> values) {
        return "<input type=\"hidden\" name=\"cityId\" value=\"" + esc(values.getOrDefault("cityId", "")) + "\">"
                + input("cityId__container[textInput]", values)
                + "<input type=\"submit\" name=\"cityId__container[searchButton]\" value=\"Hledat\">"
                + "<select name=\"cityId__container[select]\" disabled></select>"
                + "<input type=\"submit\" name=\"cityId__container[selectButton]\" value=\"Vybrat\" disabled>";
    }

    /** Admission, info and Facebook — and the name, where the form hasn't got it already (the wizard's info step). */
    private static String infoFields(Map<String, String> values, boolean withName) {
        String type = values.getOrDefault("entryType", "2");
        StringBuilder html = new StringBuilder(withName ? input("name", values) : "");
        for (String v : List.of("0", "1", "2")) {
            html.append("<input type=\"radio\" name=\"entryType\" value=\"").append(v).append("\"")
                    .append(v.equals(type) ? " checked" : "").append(">");
        }
        return html + input("entry", values)
                + "<textarea name=\"info\">\n" + esc(values.getOrDefault("info", "")) + "</textarea>"
                + input("facebookUrl", values);
    }

    private static String input(String name, Map<String, String> values) {
        return "<input type=\"text\" name=\"" + name + "\" value=\"" + esc(values.getOrDefault(name, "")) + "\">";
    }

    private String townItems(String typed) {
        String items = TOWNS.stream().filter(t -> t[1].equalsIgnoreCase(typed))
                .map(t -> item(t[0], t[1], t[1], "<div class=\"item\"><strong class=\"name\">" + t[1]
                        + "</strong> <span class=\"description\">" + t[2] + "</span></div>"))
                .collect(Collectors.joining(","));
        return "{\"items\":[" + items + "],\"create\":" + item("", typed, "", "<div class=\"item createItem\">+</div>") + "}";
    }

    // ---------------------------------------------------------------- plumbing

    private boolean loggedIn(HttpExchange exchange) {
        String cookies = String.join(";", exchange.getRequestHeaders().getOrDefault("Cookie", List.of()));
        return sessions.stream().anyMatch(s -> cookies.contains("PHPSESSID=" + s));
    }

    private void requireLogin(HttpExchange exchange) {
        if (!loggedIn(exchange)) {
            throw new IllegalStateException("not logged in");
        }
    }

    private static String concertId(String path) {
        Matcher m = CONCERT.matcher(path);
        if (!m.matches()) {
            throw new IllegalStateException("no concert in " + path);
        }
        return m.group(1);
    }

    private static String snippet(String html) {
        return "{\"snippets\":{\"snippet--form\":" + quote(html) + "}}";
    }

    private static String item(String id, String value, String title, String label) {
        return "{\"id\":" + quote(id) + ",\"value\":" + quote(value) + ",\"title\":" + quote(title) + ",\"label\":"
                + quote(label) + "}";
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private static String base(HttpExchange exchange) {
        return "http://127.0.0.1:" + exchange.getLocalAddress().getPort();
    }

    private static String first(Map<String, List<String>> map, String key) {
        List<String> values = map.get(key);
        return values == null || values.isEmpty() ? "" : values.getFirst();
    }

    private static Map<String, String> single(Map<String, List<String>> fields) {
        Map<String, String> one = new LinkedHashMap<>();
        fields.forEach((k, v) -> one.put(k, v.getLast()));
        return one;
    }

    private static Map<String, List<String>> parse(String encoded) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return fields;
        }
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            fields.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return fields;
    }

    private static void page(HttpExchange exchange, String body) throws IOException {
        send(exchange, 200, "text/html; charset=UTF-8", "<html><body>" + body + "</body></html>");
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        send(exchange, 200, "application/json", body);
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
