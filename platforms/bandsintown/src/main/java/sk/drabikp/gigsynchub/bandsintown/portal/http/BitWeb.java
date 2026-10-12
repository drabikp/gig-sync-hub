package sk.drabikp.gigsynchub.bandsintown.portal.http;

import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.bandsintown.portal.HumanPacer;
import sk.drabikp.gigsynchub.bandsintown.portal.PortalReply;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * One band's requests to the artist portal's API over plain HTTP — no browser: the session token and
 * the fingerprint of {@link BitCredentials} on every one, the answer handed on as a {@link PortalReply}
 * whoever asked for it can read. A reply that says the session is over is the band's to renew, so it
 * needs the user rather than being tried again; anything else that goes wrong is temporary.
 */
final class BitWeb implements BitApi {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    /**
     * An upload is given longer than anything else: the portal reads the whole spreadsheet, works out
     * where each row is and makes the events before it answers. A single change is not — it is told
     * everything it needs, so one that hangs is something wrong, and better said so in half a minute
     * than after two (a lane waits for it).
     */
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(120);
    /** What the portal calls the spreadsheet in its upload — it says so itself if this is wrong. */
    private static final String UPLOAD_FIELD = "upfile";

    private final String baseUrl;
    private final BitCredentials credentials;
    private final HumanPacer pacer;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    BitWeb(String baseUrl, BitCredentials credentials) {
        this(baseUrl, credentials, new HumanPacer(0, 0));
    }

    BitWeb(String baseUrl, BitCredentials credentials, HumanPacer pacer) {
        this.pacer = pacer;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.credentials = credentials;
    }

    @Override
    public String managerId() {
        return credentials.managerId();
    }

    @Override
    public String actorId() {
        return credentials.actorId();
    }

    @Override
    public PortalReply get(String path) throws BitPortalException {
        return send("GET", path, authorized(path).GET(), REQUEST_TIMEOUT);
    }

    @Override
    public PortalReply patch(String path, Map<String, ?> body) throws BitPortalException {
        return json("PATCH", path, body);
    }

    @Override
    public PortalReply put(String path, Map<String, ?> body) throws BitPortalException {
        return json("PUT", path, body);
    }

    private PortalReply json(String method, String path, Map<String, ?> body) throws BitPortalException {
        return send(method, path, authorized(path)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))),
                REQUEST_TIMEOUT);
    }

    /** The spreadsheet under {@code upfile}, as the Bulk Upload's own form sends it. */
    @Override
    public PortalReply multipartPatch(String path, String csv) throws BitPortalException {
        String boundary = "----gigsynchub" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + UPLOAD_FIELD + "\"; "
                + "filename=\"events.csv\"\r\nContent-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(csv.getBytes(StandardCharsets.UTF_8));
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send("PATCH", path, authorized(path)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())), UPLOAD_TIMEOUT);
    }

    private HttpRequest.Builder authorized(String path) throws BitPortalException {
        return HttpRequest.newBuilder(URI.create(path.startsWith("http") ? path : baseUrl + path))
                .header("Authorization", "Bearer " + credentials.sessionToken())
                .header("x-fingerprint", credentials.fingerprint())
                .header("Accept", "application/json");
    }

    private PortalReply send(String method, String path, HttpRequest.Builder request, Duration timeout)
            throws BitPortalException {
        pacer.pause();           // a person's pace, never a burst
        HttpResponse<String> response;
        try {
            response = http.send(request.timeout(timeout).build(), HttpResponse.BodyHandlers.ofString(
                    StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BitPortalException.busy("Interrupted while talking to Bandsintown — again later.");
        } catch (IOException e) {
            throw BitPortalException.temporary("Could not reach Bandsintown (" + path + "): "
                    + e.getClass().getSimpleName(), e);
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw BitPortalException.needsUser("Bandsintown no longer accepts this band's sign-in — sign in there "
                    + "again and connect it in Settings.");
        }
        return PortalReply.of(method, path, response.statusCode(), response.body(),
                response.headers().firstValue("x-next-page").orElse(null));
    }
}
