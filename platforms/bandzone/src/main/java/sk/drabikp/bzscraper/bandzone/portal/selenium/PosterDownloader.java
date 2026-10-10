package sk.drabikp.bzscraper.bandzone.portal.selenium;

import sk.drabikp.bzscraper.bandzone.BandzonePortalException;

import java.io.IOException;
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
 * One HTTP client for the app.
 */
final class PosterDownloader {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;
    private final Duration requestTimeout;

    PosterDownloader() {
        this(CONNECT_TIMEOUT, REQUEST_TIMEOUT);
    }

    PosterDownloader(Duration connectTimeout, Duration requestTimeout) {
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    /** The poster in a temp file (the caller deletes it), named with the image's own extension. */
    Path download(String posterUrl) throws BandzonePortalException {
        Path file = null;
        try {
            URI uri = URI.create(posterUrl);
            String path = uri.getPath();
            String ext = path != null && path.matches(".*\\.(?i)(jpe?g|png|gif|webp)$")
                    ? path.substring(path.lastIndexOf('.')) : ".jpg";
            file = Files.createTempFile("bz-poster-", ext);
            HttpResponse<Path> response = http.send(HttpRequest.newBuilder(uri).timeout(requestTimeout).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(file));
            if (response.statusCode() != 200) {
                throw BandzonePortalException.temporary("Poster download failed (HTTP " + response.statusCode() + "): "
                        + posterUrl);
            }
            Path downloaded = file;
            file = null;
            return downloaded;
        } catch (IOException | IllegalArgumentException e) {
            throw BandzonePortalException.temporary("Poster download failed: " + posterUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BandzonePortalException.busy("Poster download interrupted: " + posterUrl);
        } finally {
            deleteQuietly(file);
        }
    }

    static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // temp file; the OS cleans it up eventually
        }
    }
}
