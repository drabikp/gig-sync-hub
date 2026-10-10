package sk.drabikp.bzscraper.bandzone.portal.selenium;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The poster download: a file with the image's extension, an error with nothing left behind, never waiting long. */
class PosterDownloaderTest {

    private final CountDownLatch never = new CountDownLatch(1);
    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/poster.png", exchange -> {
            byte[] body = {1, 2, 3};
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/missing.jpg", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/slow.jpg", exchange -> {
            try {
                never.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        never.countDown();
        server.stop(0);
    }

    @Test
    void downloads_the_poster_into_a_temp_file_with_its_extension() throws Exception {
        Path file = new PosterDownloader().download(base + "/poster.png");
        try {
            assertThat(file.getFileName().toString()).endsWith(".png");
            assertThat(Files.readAllBytes(file)).containsExactly(1, 2, 3);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void a_poster_the_host_doesnt_have_is_a_temporary_failure() {
        assertThatThrownBy(() -> new PosterDownloader().download(base + "/missing.jpg"))
                .isInstanceOfSatisfying(BandzonePortalException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.TEMPORARY);
                    assertThat(e.getMessage()).contains("HTTP 404");
                });
    }

    @Test
    void a_host_that_doesnt_answer_is_given_up_on_in_time() {
        PosterDownloader downloader = new PosterDownloader(Duration.ofSeconds(1), Duration.ofMillis(300));

        assertThatThrownBy(() -> downloader.download(base + "/slow.jpg"))
                .isInstanceOfSatisfying(BandzonePortalException.class, e ->
                        assertThat(e.kind()).isEqualTo(FailureKind.TEMPORARY));
    }
}
