package sk.drabikp.gigsynchub.bandzone.portal;

import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.gig.application.PublicAddresses;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Fetches a gig's poster into a temp file for the edit form's uploader. The download runs
 * while the session holds Bandzone's browser and the sync's one thread, so a poster host that
 * doesn't answer must not hold them: the connection and the whole request have time limits.
 * One HTTP client for the app. Only a public address is fetched (redirects too) — a band gives the address.
 */
public final class PosterDownloader {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final int MAX_REDIRECTS = 5;

    private final HttpClient http;
    private final Duration requestTimeout;
    private final boolean publicOnly;

    public PosterDownloader() {
        this(CONNECT_TIMEOUT, REQUEST_TIMEOUT, true);
    }

    /** {@code publicOnly} false: also the server's own network (tests, with a poster served on this machine). */
    public PosterDownloader(Duration connectTimeout, Duration requestTimeout, boolean publicOnly) {
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)    // followed by hand, checked
                .connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
        this.publicOnly = publicOnly;
    }

    /** The poster in a temp file (the caller deletes it), named with the image's own extension. */
    public Path download(String posterUrl) throws BandzonePortalException {
        Path file = null;
        try {
            URI uri = URI.create(posterUrl);
            String path = uri.getPath();
            String ext = path != null && path.matches(".*\\.(?i)(jpe?g|png|gif|webp)$")
                    ? path.substring(path.lastIndexOf('.')) : ".jpg";
            file = Files.createTempFile("bz-poster-", ext);
            HttpResponse<InputStream> response = http.send(get(uri), HttpResponse.BodyHandlers.ofInputStream());
            for (int hops = 0; hops < MAX_REDIRECTS && isRedirect(response.statusCode()); hops++) {
                response.body().close();
                uri = uri.resolve(response.headers().firstValue("Location").orElseThrow(() ->
                        new IllegalArgumentException("a redirect without its address")));
                response = http.send(get(uri), HttpResponse.BodyHandlers.ofInputStream());
            }
            if (response.statusCode() != 200) {
                response.body().close();
                throw BandzonePortalException.temporary("Poster download failed: " + posterUrl);
            }
            Files.write(file, PublicAddresses.limited(response.body()));
            Path downloaded = file;
            file = null;
            return downloaded;
        } catch (IOException | IllegalArgumentException e) {
            throw BandzonePortalException.temporary("Poster download failed: " + posterUrl, e);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw BandzonePortalException.busy("Poster download interrupted: " + posterUrl);
        } finally {
            deleteQuietly(file);
        }
    }

    /** A request to a public address only ({@link PublicAddresses}) — a band's poster address can't reach the server's network. */
    private HttpRequest get(URI uri) {
        return HttpRequest.newBuilder(publicOnly ? PublicAddresses.checked(uri) : uri).timeout(requestTimeout).GET()
                .build();
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    public static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException _) {
            // temp file; the OS cleans it up eventually
        }
    }
}
