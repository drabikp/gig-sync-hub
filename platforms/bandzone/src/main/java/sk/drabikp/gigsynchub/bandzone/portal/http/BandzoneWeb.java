package sk.drabikp.gigsynchub.bandzone.portal.http;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One account's way into Bandzone over plain HTTP, as its pages talk to it: pages read as HTML, the
 * forms sent back through AJAX ({@code X-Requested-With}) and answered as JSON ({@link Answer}), a
 * poster uploaded as the page's uploader does. Keeps the account's cookies, so a login lasts across
 * sessions until Bandzone ends it. A network failure is a temporary failure; being interrupted is
 * {@code busy} (the work runs again later).
 */
final class BandzoneWeb {

    /** On every page while logged in. */
    private static final String LOGOUT = "a[href*=logout]";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(90);
    private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/130.0 Safari/537.36";

    /** A page as read: the status, where it ended up after redirects, its document. */
    record Page(int status, String url, Document document) {
    }

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    BandzoneWeb(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * The way back in, as one line to keep in the account's place of a password: the cookies Bandzone gave
     * ("stay signed in" makes them last years). Only for keeping and sending back — never shown or logged.
     */
    String session() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> !cookie.hasExpired())
                .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                .collect(Collectors.joining("; "));
    }

    /** Works as the account again with a {@link #session()} kept earlier, instead of logging in. */
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

    /** Whether Bandzone still knows this account's login (its pages show the logout link). */
    boolean loggedIn() throws BandzonePortalException {
        return get("/").document().selectFirst(LOGOUT) != null;
    }

    /**
     * Logs in through Bandzone's login form ("stay signed in" on). A login form that comes back
     * means Bandzone refused the login or password — the user's to fix, never retried.
     */
    void logIn(String login, String password) throws BandzonePortalException {
        Page page = get("/login.html?do=login");
        NetteForm form = NetteForm.find(page.document(), "loginForm-submit")
                .orElseThrow(() -> BandzonePortalException.temporary("Bandzone's login page has no login form."));
        form.set("login", login).set("password", password);
        if (form.element().selectFirst("[name=permanent]") != null) {
            form.set("permanent", "on");
        }
        Page after = send(HttpRequest.newBuilder(uri(form.action()))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form.pressing("doLogin")))));
        if (after.document().selectFirst(LOGOUT) == null) {
            throw BandzonePortalException.needsUser("Bandzone refused the login — check the login and password "
                    + "connected in Settings.");
        }
    }

    /** A page (path or full address), redirects followed. */
    Page get(String address) throws BandzonePortalException {
        return send(HttpRequest.newBuilder(uri(address)).GET());
    }

    /** The status a page answers with (redirects followed): 404 for a deleted concert. */
    int status(String address) throws BandzonePortalException {
        return get(address).status();
    }

    /** A link the page opens through AJAX (e.g. a performer's "remove" dialog). */
    Answer ajaxGet(String address) throws BandzonePortalException {
        return answer(HttpRequest.newBuilder(uri(address)).GET());
    }

    /** Sends a form through AJAX, as the page does. */
    Answer post(String action, List<Map.Entry<String, String>> fields) throws BandzonePortalException {
        return answer(HttpRequest.newBuilder(uri(action))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(encode(fields))));
    }

    /**
     * Uploads a file as the page's uploader does ({@code multipart/form-data}, one file under
     * {@code field}); Bandzone answers with the files it now holds for that form.
     */
    void upload(String address, String field, Path file) throws BandzonePortalException {
        String boundary = "----gigsynchub" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            String type = Files.probeContentType(file);
            body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\""
                    + file.getFileName() + "\"\r\nContent-Type: " + (type == null ? "application/octet-stream" : type)
                    + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.writeBytes(Files.readAllBytes(file));
            body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw BandzonePortalException.temporary("Could not read the poster to upload.", e);
        }
        HttpResponse<String> response = exchange(HttpRequest.newBuilder(uri(address))
                .timeout(UPLOAD_TIMEOUT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
        if (response.statusCode() / 100 != 2 || !response.body().contains(file.getFileName().toString())) {
            throw BandzonePortalException.temporary("Bandzone did not take the poster (answered "
                    + response.statusCode() + ").");
        }
    }

    private Answer answer(HttpRequest.Builder request) throws BandzonePortalException {
        HttpResponse<String> response = exchange(request
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01"));
        if (response.statusCode() != 200) {
            throw BandzonePortalException.temporary("Bandzone answered " + response.statusCode() + " to "
                    + path(response.uri()) + ".");
        }
        try {
            return Answer.of(json.readTree(response.body()), response.uri().toString());
        } catch (JacksonException e) {
            throw BandzonePortalException.temporary("Bandzone's answer to " + path(response.uri())
                    + " was no JSON — has its page changed?", e);
        }
    }

    private Page send(HttpRequest.Builder request) throws BandzonePortalException {
        HttpResponse<String> response = exchange(request);
        if (response.statusCode() >= 500) {
            throw BandzonePortalException.temporary("Bandzone answered " + response.statusCode() + " to "
                    + path(response.uri()) + ".");
        }
        return new Page(response.statusCode(), response.uri().toString(),
                Jsoup.parse(response.body(), response.uri().toString()));
    }

    private HttpResponse<String> exchange(HttpRequest.Builder request) throws BandzonePortalException {
        HttpRequest built = request.header("User-Agent", USER_AGENT).timeout(REQUEST_TIMEOUT).build();
        try {
            return http.send(built, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BandzonePortalException.busy("Interrupted while talking to Bandzone — again later.");
        } catch (IOException e) {
            throw BandzonePortalException.temporary("Could not reach Bandzone (" + path(built.uri()) + "): "
                    + e.getClass().getSimpleName(), e);
        }
    }

    private URI uri(String address) {
        return URI.create(address.startsWith("http") ? address : baseUrl + address);
    }

    private static String path(URI uri) {
        return uri.getRawPath();
    }

    private static String encode(List<Map.Entry<String, String>> fields) {
        return fields.stream()
                .map(f -> URLEncoder.encode(f.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(f.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
