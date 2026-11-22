package sk.drabikp.gigsynchub.bandzone.portal.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import sk.drabikp.gigsynchub.gig.domain.TextFold;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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

/**
 * A stand-in for Bandzone as its pages talk to it since the redesign (recorded from the real site on the
 * test band, 2026-11-22): the sign-in (a form nonce, then the login with its honeypot, answered with the
 * {@code bz_session} cookie), the account ({@code /api/bff/account/me}, which also hands out the upload
 * service's cookie) and its bands, the wizard's searches (towns with district and region — three Hranice —,
 * clubs in a town, band profiles), the concert API (create, the edit form's PATCH, tickets, cover, lineup
 * and guests, status, delete — which answers success for a concert already gone, as Bandzone does), the
 * upload service (a job, processed after a poll), the edit page carrying the concert as page data, and the
 * public page's 404 once deleted. Keeps what it is sent, so the client's read-backs see what was stored.
 */
final class BandzoneHttpFixture implements AutoCloseable {

    static final String LOGIN = "tester@example.test";
    static final String PASSWORD = "secret-password";
    static final String SLUG = "testband";
    static final String BAND_NAME = "Test Band";
    static final long BAND_ID = 119016;

    private static final Pattern GIG = Pattern.compile("/api/gig/(\\d+)(/.*)?");
    private static final Pattern PAGE = Pattern.compile("/koncert/(\\d+)(-[a-z0-9-]*)?(/upravit)?");
    private static final Pattern UPLOAD_JOB = Pattern.compile("/api/upload/([\\w-]+)");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Bandzone's towns: id, name, district, region, country — three Hranice, a Czech Košice. */
    private static final List<String[]> TOWNS = List.of(
            new String[] {"4109", "Praha", "Praha", "Praha", "Česká Republika"},
            new String[] {"1603", "Hranice", "Přerov", "Olomoucký", "Česká Republika"},
            new String[] {"1605", "Hranice", "Cheb", "Karlovarský", "Česká Republika"},
            new String[] {"1604", "Hranice", "České Budějovice", "Jihočeský", "Česká Republika"},
            new String[] {"2329", "Košice", "Kutná Hora", "Středočeský", "Česká Republika"});
    /** Clubs: id → name, town. */
    private static final Map<Long, Object[]> CLUBS = Map.of(
            125L, new Object[] {"Lucerna Music Bar", 4109L},
            6604L, new Object[] {"Lucerna - Velký sál", 4109L},
            8838L, new Object[] {"Club Lucerna", 806L});
    /** Band profiles; "Stubby Kids" only starts like a lineup's "Stubby" (never to be linked). */
    private static final Map<Long, String> BANDS = new LinkedHashMap<>(Map.of(
            BAND_ID, BAND_NAME, 35516L, "Incertus Posterus", 777L, "Stubby Kids"));

    /** A concert as Bandzone stores it. */
    static final class Concert {
        final long id;
        volatile String title;
        volatile String description;
        volatile String dateFrom;
        volatile String dateUntil;
        volatile Long cityId;
        volatile Long venueId;
        volatile String venueText;
        volatile String facebookUrl;
        volatile String ticketsKind = "unknown";
        volatile JsonNode ticketRows = JSON.createArrayNode();
        volatile Long coverBlobId;
        volatile int statusId = 2;
        volatile boolean deleted;
        final Map<Long, String> bands = new ConcurrentHashMap<>();      // bandId → name, in no order
        final Map<Long, String> guests = new ConcurrentHashMap<>();     // guestId → name

        Concert(long id) {
            this.id = id;
        }

        List<String> performers() {
            List<String> names = new ArrayList<>(bands.values());
            names.addAll(guests.values());
            return names;
        }
    }

    private final HttpServer server;
    final Map<Long, Concert> concerts = new ConcurrentHashMap<>();
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    private final Set<String> nonces = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> jobs = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> polls = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger(1_000_500);
    private final AtomicInteger counter = new AtomicInteger();
    final AtomicInteger logins = new AtomicInteger();
    /** Every API call: method, path, body. */
    final List<String[]> calls = new CopyOnWriteArrayList<>();
    /** The bands the signed-in account manages (by default only the test band). */
    volatile List<Long> managed = List.of(BAND_ID);

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
            String method = exchange.getRequestMethod();
            byte[] body = exchange.getRequestBody().readAllBytes();
            Map<String, String> query = query(uri.getRawQuery());
            if (path.startsWith("/api/")) {
                calls.add(new String[] {method, path, new String(body, StandardCharsets.UTF_8)});
            }
            if (path.equals("/poster.png")) {
                send(exchange, 200, "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
            } else if (path.equals("/api/auth/form-nonce")) {
                String nonce = "nonce-" + counter.incrementAndGet();
                nonces.add(nonce);
                json(exchange, 200, Map.of("nonce", nonce));
            } else if (path.equals("/api/auth/login")) {
                login(exchange, JSON.readTree(body));
            } else if (path.equals("/api/bff/account/me")) {
                me(exchange);
            } else if (path.startsWith("/api/upload")) {
                upload(exchange, method, path);
            } else if (path.startsWith("/api/") && !signedIn(exchange)) {
                json(exchange, 401, Map.of("success", false, "error", "anonymous"));
            } else if (path.equals("/api/user/bands")) {
                bands(exchange);
            } else if (path.startsWith("/api/gig/search-")) {
                search(exchange, path, query);
            } else if (path.equals("/api/gig/create")) {
                create(exchange, JSON.readTree(body));
            } else if (GIG.matcher(path).matches()) {
                Matcher gig = GIG.matcher(path);
                gig.matches();
                concert(exchange, method, Long.parseLong(gig.group(1)), gig.group(2) == null ? "" : gig.group(2),
                        body.length == 0 ? JSON.createObjectNode() : JSON.readTree(body));
            } else if (PAGE.matcher(path).matches()) {
                Matcher page = PAGE.matcher(path);
                page.matches();
                page(exchange, Long.parseLong(page.group(1)), page.group(3) != null);
            } else {
                send(exchange, 404, "text/html", "<html><body>Nenalezeno</body></html>".getBytes(StandardCharsets.UTF_8));
            }
        } catch (RuntimeException e) {
            send(exchange, 500, "text/plain", String.valueOf(e).getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---------------------------------------------------------------- the account

    private void login(HttpExchange exchange, JsonNode form) throws IOException {
        boolean human = nonces.remove(form.path("formNonce").asString("")) && form.path("website").asString("x").isEmpty();
        if (!human || !LOGIN.equals(form.path("login").asString("")) || !PASSWORD.equals(form.path("password").asString(""))) {
            json(exchange, 200, Map.of("success", false, "error", "Nesprávné přihlašovací jméno nebo heslo."));
            return;
        }
        String session = "s" + counter.incrementAndGet();
        sessions.add(session);
        logins.incrementAndGet();
        exchange.getResponseHeaders().add("Set-Cookie", "bz_session=" + session
                + "; Max-Age=7776000; Path=/; HttpOnly; SameSite=Lax");
        json(exchange, 200, Map.of("success", true, "user", Map.of("fanId", 366392, "name", "tester")));
    }

    private void me(HttpExchange exchange) throws IOException {
        String session = session(exchange);
        if (session == null) {
            json(exchange, 200, Map.of("authenticated", false));
            return;
        }
        // the browser carries this one to the upload service (Domain=.bandzone.cz there; one host here)
        exchange.getResponseHeaders().add("Set-Cookie", "bz_upload=" + session + "; Path=/; HttpOnly; SameSite=Lax");
        json(exchange, 200, Map.of("authenticated", true, "account", Map.of("id", 366392, "displayName", "tester")));
    }

    private void bands(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> bands = new ArrayList<>();
        for (long id : managed) {
            bands.add(Map.of("id", id, "slug", id == BAND_ID ? SLUG : "other" + id, "name", BANDS.getOrDefault(id, "Other")));
        }
        json(exchange, 200, Map.of("success", true, "bands", bands, "hasMore", false, "isAdmin", false));
    }

    // ---------------------------------------------------------------- the wizard's searches

    private void search(HttpExchange exchange, String path, Map<String, String> query) throws IOException {
        String words = TextFold.fold(query.getOrDefault("query", ""));
        List<Map<String, Object>> found = new ArrayList<>();
        switch (path) {
            case "/api/gig/search-cities" -> {
                for (String[] town : TOWNS) {
                    if (TextFold.fold(town[1]).startsWith(words)) {
                        found.add(Map.of("cityId", Long.parseLong(town[0]), "name", town[1], "districtName", town[2],
                                "regionName", town[3], "countryCode", "CZ", "countryName", town[4]));
                    }
                }
            }
            case "/api/gig/search-venues" -> CLUBS.forEach((id, club) -> {
                if (TextFold.fold((String) club[0]).contains(words)) {    // any town's: the page marks the others
                    found.add(Map.of("venueId", id, "name", club[0], "cityId", club[1], "city", "?"));
                }
            });
            case "/api/gig/search-bands" -> BANDS.forEach((id, name) -> {
                if (TextFold.fold(name).contains(words)) {
                    found.add(Map.of("bandId", id, "name", name, "strBandId", "b" + id));
                }
            });
            default -> {
                json(exchange, 404, Map.of("error", "Not found"));
                return;
            }
        }
        json(exchange, 200, found);
    }

    // ---------------------------------------------------------------- the concert API

    private void create(HttpExchange exchange, JsonNode wizard) throws IOException {
        long cityId = wizard.path("cityId").asLong();
        if (TOWNS.stream().noneMatch(t -> Long.parseLong(t[0]) == cityId)) {
            json(exchange, 400, Map.of("success", false, "error", "Město není vybrané"));
            return;
        }
        if (!wizard.hasNonNull("venueId") && wizard.path("venueName").asString("").isBlank()) {
            json(exchange, 400, Map.of("success", false,
                    "error", "Zvolte klub z Bandzone, nebo označte akci jako 'bez klubu / soukromá'."));
            return;
        }
        if (wizard.path("bandIds").isEmpty() && wizard.path("guests").isEmpty()) {
            json(exchange, 400, Map.of("success", false, "code", "performers_required", "error", "Kdo hraje?"));
            return;
        }
        Concert concert = new Concert(ids.incrementAndGet());
        concert.title = wizard.path("name").asString();
        String startDate = wizard.path("startDate").asString();
        concert.dateFrom = startDate + "T" + wizard.path("startTime").asString("00:00") + ":00.000Z";
        if (wizard.hasNonNull("endTime") || wizard.hasNonNull("endDate")) {
            concert.dateUntil = wizard.path("endDate").asString(startDate) + "T" + wizard.path("endTime").asString("00:00")
                    + ":00.000Z";
        }
        concert.cityId = cityId;
        place(concert, wizard.hasNonNull("venueId") ? wizard.path("venueId").asLong() : null,
                wizard.path("venueName").asString(null));
        wizard.path("bandIds").forEach(id -> concert.bands.put(id.asLong(), BANDS.getOrDefault(id.asLong(), "?")));
        wizard.path("guests").forEach(g -> concert.guests.put((long) counter.incrementAndGet(), g.path("name").asString()));
        concert.ticketsKind = wizard.path("tickets").path("kind").asString("unknown");
        concert.ticketRows = wizard.path("tickets").path("rows");
        concert.description = wizard.path("info").asString(null);
        concert.facebookUrl = wizard.path("facebookUrl").asString(null);
        concerts.put(concert.id, concert);
        json(exchange, 200, Map.of("gigId", concert.id, "merged", false));
    }

    private void concert(HttpExchange exchange, String method, long id, String rest, JsonNode body) throws IOException {
        Concert concert = concerts.get(id);
        if (concert == null || concert.deleted) {
            if ("DELETE".equals(method) && rest.isEmpty()) {
                json(exchange, 200, Map.of("success", true));       // as Bandzone: gone is deleted
            } else {
                json(exchange, 404, Map.of("success", false, "error", "Koncert nebyl nalezen."));
            }
            return;
        }
        String call = method + " " + rest.replaceAll("/\\d+$", "/{id}");
        switch (call) {
            case "PATCH " -> {
                if (body.has("title")) concert.title = body.path("title").asString(null);
                if (body.has("dateFrom")) concert.dateFrom = body.path("dateFrom").asString(null);
                if (body.has("dateUntil")) concert.dateUntil = body.path("dateUntil").asString(null);
                if (body.has("facebookUrl")) concert.facebookUrl = body.path("facebookUrl").asString(null);
                if (body.has("description")) concert.description = body.path("description").asString(null);
                if (body.has("venueId") || body.has("venueText")) {
                    if (body.hasNonNull("cityId")) concert.cityId = body.path("cityId").asLong();
                    place(concert, body.hasNonNull("venueId") ? body.path("venueId").asLong() : null,
                            body.path("venueText").asString(null));
                }
                json(exchange, 200, Map.of("success", true));
            }
            case "PUT /tickets" -> {
                concert.ticketsKind = body.path("kind").asString();
                concert.ticketRows = body.path("rows");
                tickets(exchange, concert);
            }
            case "GET /tickets" -> tickets(exchange, concert);
            case "PATCH /cover" -> {
                Long blob = body.hasNonNull("blobId") ? body.path("blobId").asLong() : null;
                if (blob != null && !jobs.containsValue(blob)) {
                    json(exchange, 400, Map.of("error", "Invalid request payload"));
                    return;
                }
                concert.coverBlobId = blob;
                json(exchange, 200, Map.of("success", true));
            }
            case "POST /lineup" -> {
                long band = body.path("bandId").asLong();
                concert.bands.put(band, BANDS.getOrDefault(band, "?"));
                json(exchange, 200, Map.of("success", true, "status", "added"));
            }
            case "DELETE /lineup/{id}" -> {
                concert.bands.remove(Long.parseLong(rest.substring(rest.lastIndexOf('/') + 1)));
                json(exchange, 200, Map.of("success", true));
            }
            case "POST /guests" -> {
                long guest = counter.incrementAndGet();
                concert.guests.put(guest, body.path("name").asString());
                json(exchange, 200, Map.of("success", true, "guest", Map.of("guestId", guest, "name", body.path("name").asString())));
            }
            case "DELETE /guests/{id}" -> {
                concert.guests.remove(Long.parseLong(rest.substring(rest.lastIndexOf('/') + 1)));
                json(exchange, 200, Map.of("success", true));
            }
            case "POST /status" -> {
                int status = switch (body.path("status").asString("")) {
                    case "approved" -> 2;
                    case "cancelled" -> 3;
                    case "postponed" -> 4;
                    case "draft" -> 6;
                    default -> -1;
                };
                if (status < 0 || concert.statusId == 7) {
                    json(exchange, 400, Map.of("success", false, "error", "Stav teď nejde měnit."));
                    return;
                }
                concert.statusId = status;
                json(exchange, 200, Map.of("success", true, "notified", 0, "statusId", status));
            }
            case "DELETE " -> {
                concert.deleted = true;
                json(exchange, 200, Map.of("success", true));
            }
            default -> json(exchange, 404, Map.of("error", "Not found"));
        }
    }

    /** A club (its name, and its town) or a typed place, as Bandzone stores either. */
    private static void place(Concert concert, Long venueId, String typed) {
        if (venueId != null && CLUBS.containsKey(venueId)) {
            concert.venueId = venueId;
            concert.venueText = (String) CLUBS.get(venueId)[0];
            concert.cityId = (Long) CLUBS.get(venueId)[1];
        } else {
            concert.venueId = null;
            concert.venueText = typed;
        }
    }

    private void tickets(HttpExchange exchange, Concert concert) throws IOException {
        ObjectNode tickets = JSON.createObjectNode().put("success", true).put("kind", concert.ticketsKind);
        tickets.set("rows", concert.ticketRows);
        tickets.putNull("legacyText");
        tickets.set("suggestedRows", JSON.createArrayNode());
        tickets.put("ticketUrl", "");
        json(exchange, 200, tickets);
    }

    // ---------------------------------------------------------------- the upload service

    private void upload(HttpExchange exchange, String method, String path) throws IOException {
        String cookies = String.join(";", exchange.getRequestHeaders().getOrDefault("Cookie", List.of()));
        if (sessions.stream().noneMatch(s -> cookies.contains("bz_upload=" + s))) {
            json(exchange, 401, Map.of("error", "unauthorized"));
            return;
        }
        Matcher job = UPLOAD_JOB.matcher(path);
        if ("POST".equals(method) && path.equals("/api/upload")) {
            String id = "job-" + counter.incrementAndGet();
            jobs.put(id, 3_528_000L + counter.incrementAndGet());
            polls.put(id, new AtomicInteger());
            json(exchange, 202, Map.of("ok", true, "jobId", id, "status", "received", "pollUrl", "/api/upload/" + id));
        } else if ("GET".equals(method) && job.matches() && jobs.containsKey(job.group(1))) {
            boolean done = polls.get(job.group(1)).incrementAndGet() > 1;      // processing first, then done
            json(exchange, 200, done
                    ? Map.of("ok", true, "status", "done", "registeredBlobId", jobs.get(job.group(1)))
                    : Map.of("ok", true, "status", "processing"));
        } else {
            json(exchange, 404, Map.of("error", "not_found"));
        }
    }

    // ---------------------------------------------------------------- pages

    /**
     * The concert's public page, or its edit page — which carries the concert as page data, split over two
     * pushes and with React's date prefix, as Bandzone's does.
     */
    private void page(HttpExchange exchange, long id, boolean edit) throws IOException {
        Concert concert = concerts.get(id);
        if (concert == null || concert.deleted) {
            send(exchange, 404, "text/html", "<html><body>Nenalezeno</body></html>".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!edit) {
            send(exchange, 200, "text/html", ("<html><body><h1>" + concert.title + "</h1></body></html>")
                    .getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!signedIn(exchange)) {
            exchange.getResponseHeaders().add("Location", "/login.html?redirect=%2Fkoncert%2F" + id + "%2Fupravit");
            send(exchange, 307, "text/html", new byte[0]);
            return;
        }
        ObjectNode gig = JSON.createObjectNode();
        gig.put("gigId", concert.id).put("title", concert.title).put("description", concert.description)
                .put("dateFrom", concert.dateFrom == null ? null : "$D" + concert.dateFrom)
                .put("dateUntil", concert.dateUntil == null ? null : "$D" + concert.dateUntil)
                .put("cityId", concert.cityId).put("venueId", concert.venueId).put("venueText", concert.venueText)
                .put("facebookUrl", concert.facebookUrl).put("coverBlobId", concert.coverBlobId)
                .put("statusId", concert.statusId);
        ArrayNode lineup = gig.putArray("lineup");
        concert.bands.forEach((band, name) -> lineup.addObject().put("bandId", band).put("name", name)
                .put("accessRole", "participant"));
        ArrayNode guests = gig.putArray("guests");
        concert.guests.forEach((guest, name) -> guests.addObject().put("guestId", guest).put("name", name));
        String data = "3f:[\"$\",\"$L3f\",null,{\"gig\":" + JSON.writeValueAsString(gig) + ",\"variant\":\"settings\"}]\n";
        int half = data.length() / 2;
        String html = "<html><head><title>Upravit koncert | Bandzone</title></head><body><main></main>"
                + "<script>self.__next_f.push([0])</script>"
                + push(data.substring(0, half)) + push(data.substring(half)) + "</body></html>";
        send(exchange, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    private static String push(String chunk) {
        return "<script>self.__next_f.push([1," + JSON.writeValueAsString(chunk) + "])</script>";
    }

    // ---------------------------------------------------------------- helpers

    private boolean signedIn(HttpExchange exchange) {
        return session(exchange) != null;
    }

    private String session(HttpExchange exchange) {
        String cookies = String.join(";", exchange.getRequestHeaders().getOrDefault("Cookie", List.of()));
        return sessions.stream().filter(s -> cookies.contains("bz_session=" + s)).findFirst().orElse(null);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return values;
    }

    private static void json(HttpExchange exchange, int status, Object value) throws IOException {
        send(exchange, status, "application/json", JSON.writeValueAsBytes(value));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
