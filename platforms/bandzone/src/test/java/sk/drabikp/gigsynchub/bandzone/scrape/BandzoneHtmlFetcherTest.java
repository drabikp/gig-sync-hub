package sk.drabikp.gigsynchub.bandzone.scrape;

import com.sun.net.httpserver.HttpServer;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.BandzoneProperties;
import sk.drabikp.gigsynchub.sync.application.port.out.FailureKind;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The band page read from the configured Bandzone address: every year tab and the planned gigs. */
class BandzoneHtmlFetcherTest {

    private final List<String> requested = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private BandzoneHtmlFetcher fetcher;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            requested.add(exchange.getRequestURI().getPath() + "?" + query);
            if (!exchange.getRequestURI().getPath().equals("/eufory")) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            String page = query.equals("at=gig&gy=0")
                    ? "<div class=\"years\"><span class=\"years-upcoming\"><a>Plánované</a></span>"
                      + "<span><a>2025</a></span><span><a>2024</a></span></div>"
                    : "<article class=\"gig\" id=\"" + query + "\"></article>";
            byte[] body = ("<html><body>" + page + "</body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        fetcher = new BandzoneHtmlFetcher(new BandzoneProperties("http://127.0.0.1:" + server.getAddress().getPort(),
                null, null, "eufory", null));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void reads_every_year_tab_and_the_planned_gigs_from_the_configured_address() throws Exception {
        List<String> articles = fetcher.fetchAllGigs("eufory").stream().map(Element::id).toList();

        assertThat(articles).containsExactly("at=gig&gy=2025", "at=gig&gy=2024", "at=gig");
        assertThat(requested).contains("/eufory?at=gig&gy=0");
    }

    @Test
    void a_band_bandzone_doesnt_have_needs_the_user() {
        assertThatThrownBy(() -> fetcher.fetchAllGigs("nobody"))
                .isInstanceOfSatisfying(BandzonePortalException.class, e ->
                        assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER));
    }
}
