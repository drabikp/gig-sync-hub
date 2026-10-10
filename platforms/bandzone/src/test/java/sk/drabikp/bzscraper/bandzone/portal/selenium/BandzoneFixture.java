package sk.drabikp.bzscraper.bandzone.portal.selenium;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import sk.drabikp.bzscraper.browser.TestChromium;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A stand-in for Bandzone's band admin, for the page objects' tests: copies of its concert
 * forms ({@code src/test/resources/bandzone-admin}) that keep what is posted to them, so the
 * edit form shows the stored concert again, as Bandzone does, and the lineup tab its performing
 * bands (with the band search and its "band without a profile" stubs). The delete tab cancels or
 * deletes the concert; the band's gig list ({@code /testband?at=gig[&gy=<year>]}) shows the
 * planned or played concerts, a cancelled one marked as such; a concert's public page
 * ({@code /koncert/<id>}) leads to its full page, or answers "not found" once it is deleted (its
 * admin pages too). In a headless Chromium; skipped where none is installed.
 */
final class BandzoneFixture implements AutoCloseable {

    private static final Pattern CONCERT = Pattern.compile("/koncert/(\\d+)/update\\?(.*)");
    private static final Pattern BAND_PAGE = Pattern.compile("/testband(\\?at=gig(&gy=(\\d+))?)?");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy");
    /** On every page of the logged-in band admin. */
    private static final String LOGOUT = "<a href=\"/logout\">Odhlásit</a>";
    private static final List<String> FIELDS = List.of("start[date]", "start[time]", "end[date]", "end[time]", "cityId",
            "cityId__container[textInput]", "venueId", "venueId__container[textInput]", "name", "entry", "info",
            "facebookUrl", "entryType");

    private static final Pattern LINEUP_POST = Pattern.compile("/koncert/(\\d+)/update\\?do=(addBandForm-submit"
            + "|addBandForm-bandIds-addCompleter-createItem|gigBands-submit&em=delete&ei=(\\d+))");
    /** The band profiles Bandzone's search knows, in the order it suggests them. */
    private static final List<Map.Entry<String, String>> PROFILES = List.of(Map.entry("band-1", "Eufory"),
            Map.entry("band-12", "Snaefell Tribute"), Map.entry("band-11", "Snaefell"),
            Map.entry("band-13", "Incertus Posterus"), Map.entry("band-14", "Old Band"));

    /** A form the page posted: where, and its fields. */
    record Post(String path, Map<String, String> fields) {
    }

    /** One of a concert's performing bands: a Bandzone profile, or a stub (profile null) only on it. */
    record Performer(String id, String name, String profile) {
    }

    private final HttpServer server;
    /** The stored concerts by id, as their edit forms show them. */
    final Map<String, Map<String, String>> concerts = new ConcurrentHashMap<>();
    /** The concerts' performing bands by concert id, as their lineup tabs show them. */
    final Map<String, List<Performer>> lineups = new ConcurrentHashMap<>();
    private final Map<String, String> stubs = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger(500);
    final List<Post> posts = new CopyOnWriteArrayList<>();
    /** The concerts deleted (or never there): their pages are "not found". */
    final Set<String> deleted = ConcurrentHashMap.newKeySet();
    /** The concerts cancelled, as the band's gig list marks them. */
    final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    /** Whether the wizard asks "is it one of these similar concerts?" before its second step. */
    boolean similarConcerts;
    /** Whether the delete tab's cancel is (wrongly) ignored. */
    boolean cancelIgnored;
    final ChromeDriver driver;
    final BandzoneBrowser browser;

    BandzoneFixture() throws IOException {
        TestChromium.assumeInstalled();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serve);
        server.start();
        driver = TestChromium.start("1280,1024");
        browser = new BandzoneBrowser(driver, new WebDriverWait(driver, Duration.ofSeconds(10)),
                "http://127.0.0.1:" + server.getAddress().getPort(), "testband");
    }

    /** A stored concert with these field values (the rest empty). */
    void concert(String id, Map<String, String> fields) {
        concerts.put(id, new ConcurrentHashMap<>(fields));
    }

    /** Adds a performing band to a concert — its profile if the search knows the name, else a stub; its id. */
    String performer(String concertId, String name) {
        String profile = PROFILES.stream().filter(p -> p.getValue().equals(name)).map(Map.Entry::getKey)
                .findFirst().orElse(null);
        Performer performer = new Performer(String.valueOf(ids.incrementAndGet()), name, profile);
        lineups.computeIfAbsent(concertId, id -> new CopyOnWriteArrayList<>()).add(performer);
        return performer.id();
    }

    private void serve(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().toString();
        if ("POST".equals(exchange.getRequestMethod())) {
            Map<String, String> fields = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            posts.add(new Post(path, fields));
            handlePost(exchange, path, fields);
            return;
        }
        Matcher concert = CONCERT.matcher(path);
        Matcher bandPage = BAND_PAGE.matcher(path);
        if (path.equals("/widgets.js")) {
            send(exchange, "text/javascript", resource("widgets.js"));
        } else if (concert.matches() && deleted.contains(concert.group(1))) {
            send(exchange, 404, "text/html; charset=utf-8", "<!doctype html><html><body>" + LOGOUT
                    + "<h1>Stránka nenalezena</h1></body></html>");
        } else if (bandPage.matches()) {
            send(exchange, "text/html; charset=utf-8", bandPage(bandPage.group(3)));
        } else if (concert.matches() && concert.group(2).contains("profileDeleteForm")) {
            send(exchange, "text/html; charset=utf-8", resource("delete.html").replace("{{id}}", concert.group(1)));
        } else if (concert.matches() && concert.group(2).startsWith("do=addBandForm-bandIds-addCompleter-search")) {
            send(exchange, "application/json", search(form(concert.group(2)).get("term")));
        } else if (concert.matches() && concert.group(2).contains("updateBands")) {
            send(exchange, "text/html; charset=utf-8", lineupPage(concert.group(1)));
        } else if (concert.matches()) {
            send(exchange, "text/html; charset=utf-8", page("update.html", concert.group(1),
                    concerts.getOrDefault(concert.group(1), Map.of())));
        } else if (path.startsWith("/koncert/zalozit.html")) {
            send(exchange, "text/html; charset=utf-8", page("wizard1.html", "", Map.of()));
        } else if (path.matches("/koncert/\\d+")) {
            String id = path.substring("/koncert/".length());
            if (deleted.contains(id)) {
                send(exchange, 404, "text/html; charset=utf-8", "<!doctype html><html><body>Nenalezeno</body></html>");
            } else {
                redirect(exchange, "/koncert/" + id + "-koncert/");
            }
        } else if (path.matches("/koncert/\\d+-.*")) {
            send(exchange, "text/html; charset=utf-8", "<!doctype html><html><body>Koncert</body></html>");
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }
    }

    private void handlePost(HttpExchange exchange, String path, Map<String, String> fields) throws IOException {
        Matcher concert = Pattern.compile("/koncert/(\\d+)/update\\?do=(\\w+)-submit").matcher(path);
        Matcher lineup = LINEUP_POST.matcher(path);
        if (lineup.matches()) {
            lineupPost(exchange, lineup, fields);
        } else if (concert.matches() && concert.group(2).equals("updateForm")) {
            concerts.computeIfAbsent(concert.group(1), id -> new ConcurrentHashMap<>()).putAll(fields);
            redirect(exchange, "/koncert/" + concert.group(1) + "/update?updateTabs-at=updateForm");
        } else if (concert.matches() && concert.group(2).equals("profileDeleteForm")) {
            if (fields.containsKey("delete")) {
                concerts.remove(concert.group(1));
                deleted.add(concert.group(1));
            } else if (fields.containsKey("cancelGig") && !cancelIgnored) {
                cancelled.add(concert.group(1));
            }
            redirect(exchange, "/testband");
        } else if (concert.matches()) {
            redirect(exchange, "/" + "testband");
        } else if (path.endsWith("step=1") && similarConcerts) {
            send(exchange, "text/html; charset=utf-8", resource("similar.html"));
        } else if (path.endsWith("step=1") || path.endsWith("step=similar")) {
            send(exchange, "text/html; charset=utf-8", page("wizard2.html", "", Map.of()));
        } else {
            redirect(exchange, "/koncert/777-new-concert");
        }
    }

    /**
     * The lineup tab's posts: a performer removed, a stub created (answered with its id, as the AJAX
     * call is), or the queued bands added; the tab is shown again after a removal or an add.
     */
    private void lineupPost(HttpExchange exchange, Matcher post, Map<String, String> fields) throws IOException {
        List<Performer> performers = lineups.computeIfAbsent(post.group(1), id -> new CopyOnWriteArrayList<>());
        if (post.group(3) != null) {
            performers.removeIf(p -> p.id().equals(post.group(3)));
        } else if (post.group(2).endsWith("createItem")) {
            String stub = "stub-" + ids.incrementAndGet();
            stubs.put(stub, fields.get("bandIds__addCompleter__createItem[name]"));
            send(exchange, "text/plain", stub);
            return;
        } else {
            fields.forEach((field, band) -> {
                if (field.startsWith("bandIds[")) {
                    String profile = PROFILES.stream().filter(p -> p.getKey().equals(band)).map(Map.Entry::getValue)
                            .findFirst().orElse(null);
                    performers.add(new Performer(String.valueOf(ids.incrementAndGet()),
                            profile != null ? profile : stubs.get(band), profile != null ? band : null));
                }
            });
        }
        redirect(exchange, "/koncert/" + post.group(1) + "/update?updateTabs-at=updateBands");
    }

    /** The band search's suggestions: the profiles whose name contains the term, as [name, id] pairs. */
    private static String search(String term) {
        return PROFILES.stream()
                .filter(p -> p.getValue().toLowerCase(Locale.ROOT).contains(term.trim().toLowerCase(Locale.ROOT)))
                .map(p -> "[\"" + p.getValue() + "\",\"" + p.getKey() + "\"]")
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** The lineup tab with the concert's performers; a profile links to it, a stub is only a name. */
    private String lineupPage(String id) throws IOException {
        StringBuilder bands = new StringBuilder();
        for (Performer performer : lineups.getOrDefault(id, List.of())) {
            String name = "<h4>" + escape(performer.name()) + "</h4>";
            bands.append("  <div class=\"editable\" id=\"gigBand-editable-").append(performer.id()).append("\">")
                    .append(performer.profile() != null
                            ? "<a class=\"profileLink\" href=\"/" + performer.profile() + "\">" + name + "</a>"
                            : "<span class=\"profileLink\">" + name + "</span>")
                    .append(" <a href=\"#\" class=\"delete\">odstranit z koncertu</a></div>\n");
        }
        return resource("lineup.html").replace("{{BANDS}}", bands).replace("{{id}}", id);
    }

    /**
     * The band's gig list: the planned concerts (today or later), or those played in {@code year};
     * each an article linking to its concert, a cancelled one marked so.
     */
    private String bandPage(String year) {
        StringBuilder articles = new StringBuilder();
        concerts.forEach((id, fields) -> {
            LocalDate start = LocalDate.parse(fields.getOrDefault("start[date]", "1.1.2000"), DATE);
            boolean planned = !start.isBefore(LocalDate.now());
            if (year == null ? planned : !planned && start.getYear() == Integer.parseInt(year)) {
                articles.append("<article class=\"gig").append(cancelled.contains(id) ? " gig--canceled" : "")
                        .append("\"><a class=\"gig__link\" href=\"/koncert/").append(id).append("-koncert/\"></a>")
                        .append("<h3 class=\"gig__title\">").append(escape(fields.getOrDefault("name", "")))
                        .append("</h3></article>\n");
            }
        });
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>Testband</title></head><body>" + LOGOUT
                + "\n" + articles + "</body></html>";
    }

    /** A form page with the stored values filled in, as Bandzone renders it. */
    private String page(String name, String id, Map<String, String> stored) throws IOException {
        String html = resource(name).replace("{{FIELDS}}", resource("fields.html")).replace("{{INFO}}", resource("info.html"))
                .replace("{{id}}", id);
        for (String field : FIELDS) {
            html = html.replace("{{" + field + "}}", escape(stored.getOrDefault(field, "")));
        }
        for (String type : List.of("0", "1", "2")) {
            html = html.replace("{{entryType" + type + "}}", type.equals(stored.get("entryType")) ? "checked" : "");
        }
        return html;
    }

    private String resource(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/bandzone-admin/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> form(String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (!pair.isEmpty()) {
                String[] kv = pair.split("=", 2);
                fields.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
        }
        return fields;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private static void send(HttpExchange exchange, String type, String body) throws IOException {
        send(exchange, 200, type, body);
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.quit();
        }
        if (server != null) {
            server.stop(0);
        }
    }
}
