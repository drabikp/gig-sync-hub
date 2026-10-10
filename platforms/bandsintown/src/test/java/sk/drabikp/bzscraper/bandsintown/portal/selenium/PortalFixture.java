package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import sk.drabikp.bzscraper.browser.TestChromium;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for the artist portal, for the page objects' tests: copies of its pages
 * ({@code src/test/resources/bandsintown-portal}, built like the real ones as seen in the
 * probes) served with the API replies a test sets, in a headless Chromium with the real reply
 * capture registered as on the real portal. Skipped where no Chromium is installed.
 */
final class PortalFixture implements AutoCloseable {

    /** A request the page made to the API (method, path with query, body). */
    record Request(String method, String path, String body) {
    }

    private record Reply(int status, String json, String nextPage) {
    }

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    final List<Request> requests = new CopyOnWriteArrayList<>();
    final ChromeDriver driver;
    final BitPortal portal;

    PortalFixture() throws IOException {
        TestChromium.assumeInstalled();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serve);
        server.start();
        driver = TestChromium.start("1366,900");
        driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", Map.of("source", PortalReplies.CAPTURE));
        portal = new BitPortal(driver, new WebDriverWait(driver, Duration.ofSeconds(10)), new HumanPacer(0, 0),
                "http://127.0.0.1:" + server.getAddress().getPort()).forArtist("1");
    }

    /** What the API answers to requests whose path (with query) starts with {@code path}. */
    PortalFixture reply(String path, String json) {
        return reply(path, json, null);
    }

    PortalFixture reply(String path, String json, String nextPage) {
        replies.put(path, new Reply(200, json, nextPage));
        return this;
    }

    List<Request> writes() {
        return requests.stream().filter(r -> !"GET".equals(r.method())).toList();
    }

    private void serve(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().toString();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (path.startsWith("/api/")) {
            requests.add(new Request(exchange.getRequestMethod(), path, body));
            Reply reply = replies.entrySet().stream().filter(e -> path.startsWith(e.getKey()))
                    .max(Map.Entry.comparingByKey((a, b) -> a.length() - b.length()))
                    .map(Map.Entry::getValue).orElse(new Reply(404, "{\"status\":\"ERROR\"}", null));
            if (reply.nextPage() != null) {
                exchange.getResponseHeaders().add("x-next-page", reply.nextPage());
                exchange.getResponseHeaders().add("Access-Control-Expose-Headers", "x-next-page");
            }
            send(exchange, reply.status(), "application/json", reply.json().getBytes(StandardCharsets.UTF_8));
            return;
        }
        // /artists/1/events/upcoming|past → the list page; /artists/1/events/<id>?… → the form
        String page = path.matches("/artists/1/events/(upcoming|past).*") ? "events.html"
                : path.matches("/artists/1/events/\\d+.*") ? "form.html"
                : path.equals("/dialog.js") ? "dialog.js" : null;
        if (page == null) {
            send(exchange, 404, "text/plain", new byte[0]);
            return;
        }
        try (InputStream file = getClass().getResourceAsStream("/bandsintown-portal/" + page)) {
            send(exchange, 200, page.endsWith(".js") ? "text/javascript" : "text/html; charset=utf-8",
                    file.readAllBytes());
        }
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
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
