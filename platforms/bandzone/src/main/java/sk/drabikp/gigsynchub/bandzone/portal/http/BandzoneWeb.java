package sk.drabikp.gigsynchub.bandzone.portal.http;

import io.micrometer.observation.ObservationRegistry;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.gig.application.PublicAddresses;
import sk.drabikp.gigsynchub.portal.PlatformEgress;
import sk.drabikp.gigsynchub.portal.PortalCall;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One account's way into Bandzone over plain HTTP, as its pages talk to it since the redesign (seen
 * 2026-11-22): a JSON API under {@code /api} (the page's own {@code fetch} calls — JSON both ways, the
 * session in an HttpOnly cookie, no CSRF token), pages read for the data they carry, and a poster sent to
 * the upload service ({@code upload.bandzone.cz}). Keeps the account's cookies, so a sign-in lasts across
 * sessions until Bandzone ends it. A network failure is a temporary failure; being interrupted is
 * {@code busy} (the work runs again later).
 */
final class BandzoneWeb {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(90);
    /**
     * The sign-in form's nonce must be this old before it is sent — the page itself waits so, and a
     * nonce sent sooner is a bot's.
     */
    static final Duration NONCE_AGE = Duration.ofMillis(1200);
    /** How long a poster may take to be processed after its upload, asked again each {@link #UPLOAD_POLL}. */
    private static final int UPLOAD_POLLS = 30;
    private static final Duration UPLOAD_POLL = Duration.ofSeconds(1);
    private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/130.0 Safari/537.36";
    private static final String ENDED = "Bandzone has ended this band's sign-in — connect Bandzone again in Settings.";

    /** A page as read: the status and where it ended up after redirects, and what it holds. */
    record Page(int status, String url, String body) {
    }

    /** An API's answer: its status and its JSON (an empty object when it sent none). */
    record Reply(int status, JsonNode json) {

        boolean ok() {
            return status / 100 == 2 && (!json.path("success").isBoolean() || json.path("success").asBoolean());
        }

        /** What Bandzone said went wrong, in its own words. */
        String error() {
            String error = json.path("error").asString("");
            String message = json.path("message").asString("");
            return error.isBlank() ? (message.isBlank() ? "answered " + status : message) : error;
        }
    }

    private final ObservationRegistry observations;
    private final String baseUrl;
    private final String uploadUrl;
    private final Duration nonceAge;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    /** For signing in once: unobserved and straight out of this machine. */
    BandzoneWeb(String baseUrl) {
        this(baseUrl, uploadUrlOf(baseUrl), ObservationRegistry.NOOP, PlatformEgress.DIRECT, NONCE_AGE);
    }

    BandzoneWeb(String baseUrl, String uploadUrl, ObservationRegistry observations, PlatformEgress egress,
                Duration nonceAge) {
        this.observations = observations;
        this.baseUrl = withoutSlash(baseUrl);
        this.uploadUrl = withoutSlash(uploadUrl);
        this.nonceAge = nonceAge;
        this.http = egress.on(HttpClient.newBuilder()
                .cookieHandler(new AsBrowsersSendThem())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT))
                .build();
    }

    /** Bandzone's upload service: {@code https://upload.bandzone.cz} beside {@code https://bandzone.cz}. */
    static String uploadUrlOf(String baseUrl) {
        URI base = URI.create(withoutSlash(baseUrl));
        return base.getScheme() + "://upload." + base.getHost() + (base.getPort() < 0 ? "" : ":" + base.getPort());
    }

    /**
     * The way back in, as one line to keep in the account's place of a password: the cookies Bandzone gave
     * ("stay signed in" makes its session last 90 days). Only for keeping and sending back — never shown or
     * logged.
     */
    String session() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> !cookie.hasExpired())
                .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                .distinct()
                .collect(Collectors.joining("; "));
    }

    /** Works as the account again with a {@link #session()} kept earlier, instead of signing in. */
    void useSession(String session) {
        URI base = URI.create(baseUrl);
        for (String pair : session.split(";")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                HttpCookie cookie = new HttpCookie(pair.substring(0, equals).strip(), pair.substring(equals + 1).strip());
                cookie.setDomain(base.getHost());
                cookie.setPath("/");
                cookie.setVersion(0);
                cookies.getCookieStore().add(base, cookie);
            }
        }
    }

    /**
     * Whether Bandzone still knows this account's sign-in (its account answers {@code authenticated}). Asking
     * also gives the cookie the upload service reads, as it does in the browser.
     */
    boolean loggedIn() throws BandzonePortalException {
        Reply me = call("GET", "/api/bff/account/me", null);
        return me.status() == 200 && me.json().path("authenticated").asBoolean();
    }

    /**
     * Signs in as the page's form does ("stay signed in" on): a fresh form nonce, old enough, the empty
     * honeypot beside it. A refusal is the user's to fix, never retried.
     */
    void logIn(String login, String password) throws BandzonePortalException {
        Reply nonce = call("GET", "/api/auth/form-nonce", null);
        String formNonce = nonce.json().path("nonce").asString("");
        if (formNonce.isBlank()) {
            throw BandzonePortalException.temporary("Bandzone's sign-in gave no form nonce (answered "
                    + nonce.status() + ").");
        }
        pause(nonceAge);
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("login", login);
        form.put("password", password);
        form.put("permanent", true);
        form.put("formNonce", formNonce);
        form.put("website", "");
        Reply signedIn = call("POST", "/api/auth/login", form);
        if (signedIn.status() == 429) {
            throw BandzonePortalException.temporary("Bandzone asked to wait before signing in again.");
        }
        if (!signedIn.ok()) {
            throw BandzonePortalException.needsUser("Bandzone refused the sign-in (" + signedIn.error()
                    + ") — check the login and password connected in Settings.");
        }
        if (!loggedIn()) {
            throw BandzonePortalException.temporary("Bandzone accepted the sign-in but doesn't know it afterwards.");
        }
    }

    /**
     * An API call that must succeed. Bandzone's refusals are typed: a sign-in that is over or a login that
     * may not do this needs the user, as does a request Bandzone calls invalid (its words are passed on — a
     * club to pick, a town it doesn't know); anything else is temporary.
     */
    JsonNode send(String method, String path, Object body, String what) throws BandzonePortalException {
        Reply reply = call(method, path, body);
        if (reply.ok()) {
            return reply.json();
        }
        throw switch (reply.status()) {
            case 401 -> BandzonePortalException.needsUser(ENDED);
            case 403 -> BandzonePortalException.needsUser("Bandzone doesn't let this login " + what + " ("
                    + reply.error() + ") — is the band's address in Settings right?");
            case 400, 409, 422 -> BandzonePortalException.needsUser("Bandzone refused to " + what + ": "
                    + reply.error());
            default -> BandzonePortalException.temporary("Bandzone could not " + what + ": " + reply.error());
        };
    }

    /** An API call, answered whatever it answers (the caller reads the status). */
    Reply call(String method, String path, Object body) throws BandzonePortalException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).header("Accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        }
        Raw raw = exchange(request.header("Origin", baseUrl), REQUEST_TIMEOUT);
        return new Reply(raw.status(), parse(raw));
    }

    /** A page (path or full address), redirects followed. */
    Page page(String address) throws BandzonePortalException {
        Raw raw = exchange(HttpRequest.newBuilder(uri(address)).GET(), REQUEST_TIMEOUT);
        if (raw.status() >= 500) {
            throw BandzonePortalException.temporary("Bandzone answered " + raw.status() + " to " + path(raw.uri()) + ".");
        }
        return new Page(raw.status(), raw.uri().toString(), raw.body());
    }

    /** The status a page answers with (redirects followed): 404 for a deleted concert. */
    int status(String address) throws BandzonePortalException {
        return page(address).status();
    }

    /**
     * Uploads an image as the page's uploader does — to the upload service, {@code multipart/form-data} with
     * the file and its {@code purpose} — and waits until the service has processed it.
     *
     * @return the id Bandzone then knows the image by (what a concert's cover is set to)
     */
    long upload(Path file, String purpose) throws BandzonePortalException {
        String boundary = "----gigsynchub" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            String type = Files.probeContentType(file);
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                    + file.getFileName() + "\"\r\nContent-Type: " + (type == null ? "application/octet-stream" : type)
                    + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.writeBytes(Files.readAllBytes(file));
            body.writeBytes(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\n"
                    + purpose + "\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw BandzonePortalException.temporary("Could not read the poster to upload.", e);
        }
        Raw sent = exchange(HttpRequest.newBuilder(URI.create(uploadUrl + "/api/upload"))
                .header("Origin", baseUrl)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())), UPLOAD_TIMEOUT);
        JsonNode job = parse(sent);
        if (sent.status() == 401 || sent.status() == 403) {
            throw BandzonePortalException.needsUser(ENDED);
        }
        String jobId = job.path("jobId").asString("");
        if (sent.status() / 100 != 2 || !job.path("ok").asBoolean() || jobId.isBlank()) {
            throw BandzonePortalException.temporary("Bandzone did not take the poster ("
                    + job.path("error").asString("answered " + sent.status()) + ").");
        }
        for (int poll = 0; poll < UPLOAD_POLLS; poll++) {
            JsonNode state = parse(exchange(HttpRequest.newBuilder(URI.create(uploadUrl + "/api/upload/" + jobId))
                    .GET(), REQUEST_TIMEOUT));
            switch (state.path("status").asString("")) {
                case "done" -> {
                    long blob = state.path("registeredBlobId").asLong();
                    if (blob <= 0) {
                        throw BandzonePortalException.temporary("Bandzone processed the poster but gave it no id.");
                    }
                    return blob;
                }
                case "failed" -> throw BandzonePortalException.needsUser("Bandzone could not use the poster: "
                        + state.path("error").asString("it gave no reason") + ".");
                default -> pause(UPLOAD_POLL);
            }
        }
        throw BandzonePortalException.temporary("Bandzone did not finish processing the poster in time.");
    }

    /**
     * The account's cookies, sent back as a browser sends them: {@code name=value}. The JDK's own handler
     * takes a cookie that comes with {@code Max-Age} — Bandzone's session does — for an RFC 2965 one and
     * sends it back as {@code $Version="1"; bz_session="…"}, which is not the cookie Bandzone gave.
     */
    private final class AsBrowsersSendThem extends CookieHandler {

        @Override
        public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
            String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
            String line = cookies.getCookieStore().get(uri).stream()
                    .filter(cookie -> !cookie.hasExpired())
                    .filter(cookie -> cookie.getPath() == null || path.startsWith(cookie.getPath()))
                    .filter(cookie -> !cookie.getSecure() || "https".equalsIgnoreCase(uri.getScheme()))
                    .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                    .distinct()
                    .collect(Collectors.joining("; "));
            return line.isEmpty() ? Map.of() : Map.of("Cookie", List.of(line));
        }

        @Override
        public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
            cookies.put(uri, responseHeaders);
        }
    }

    /** What an exchange answered: as much of it as we are willing to hold, and where it came from. */
    private record Raw(int status, URI uri, String body) {
    }

    private JsonNode parse(Raw raw) {
        if (raw.body().isBlank()) {
            return json.createObjectNode();
        }
        try {
            JsonNode node = json.readTree(raw.body());
            return node == null || node.isMissingNode() ? json.createObjectNode() : node;
        } catch (JacksonException _) {
            return json.createObjectNode();   // an HTML error page, say: the status speaks for it
        }
    }

    /**
     * One exchange, with the answer read into memory UP TO a limit rather than however much comes
     * ({@link PublicAddresses#limited}): a page that goes wrong is then not the app's heap.
     */
    private Raw exchange(HttpRequest.Builder request, Duration timeout) throws BandzonePortalException {
        HttpRequest built = request.header("User-Agent", USER_AGENT).timeout(timeout).build();
        return PortalCall.observed(observations, "bandzone", built.method(), path(built.uri()), () -> send(built));
    }

    private Raw send(HttpRequest built) throws BandzonePortalException {
        try {
            HttpResponse<InputStream> answer = http.send(built, HttpResponse.BodyHandlers.ofInputStream());
            return new Raw(answer.statusCode(), answer.uri(),
                    new String(PublicAddresses.limited(answer.body()), StandardCharsets.UTF_8));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw BandzonePortalException.busy("Interrupted while talking to Bandzone — again later.");
        } catch (IllegalArgumentException e) {
            throw BandzonePortalException.temporary("Bandzone's answer to " + path(built.uri())
                    + " was larger than this server will read.", e);
        } catch (IOException e) {
            throw BandzonePortalException.temporary("Could not reach Bandzone (" + path(built.uri()) + "): "
                    + e.getClass().getSimpleName(), e);
        }
    }

    private static void pause(Duration duration) throws BandzonePortalException {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw BandzonePortalException.busy("Interrupted while talking to Bandzone — again later.");
        }
    }

    private URI uri(String address) {
        return URI.create(address.startsWith("http") ? address : baseUrl + address);
    }

    private static String path(URI uri) {
        return uri.getRawPath();
    }

    private static String withoutSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
