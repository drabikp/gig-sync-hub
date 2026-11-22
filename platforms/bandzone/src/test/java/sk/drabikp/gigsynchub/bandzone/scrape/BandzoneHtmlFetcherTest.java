package sk.drabikp.gigsynchub.bandzone.scrape;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.BandzoneProperties;
import sk.drabikp.gigsynchub.sync.application.port.out.FailureKind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The band page read from the configured Bandzone address: the gigs its data carries, planned and played,
 * and the archive for the played ones the page leaves out.
 */
class BandzoneHtmlFetcherTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<String> requested = new CopyOnWriteArrayList<>();
    private volatile String gigs;
    private HttpServer server;
    private BandzoneHtmlFetcher fetcher;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            requested.add(path + (query == null ? "" : "?" + query));
            String body;
            String type = "text/html; charset=utf-8";
            if (path.equals("/eufory")) {
                body = page(gigs);
            } else if (path.equals("/api/band/gigs-archive") && query.contains("slug=eufory")) {
                body = query.contains("page=1&")
                        ? "{\"items\":[" + gig(3, "2025") + "," + gig(4, "2024") + "],\"total\":3,\"page\":1,\"totalPages\":2}"
                        : "{\"items\":[" + gig(5, "2023") + "],\"total\":3,\"page\":2,\"totalPages\":2}";
                type = "application/json";
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", type);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
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
    void reads_the_planned_and_the_played_gigs_the_page_carries_from_the_configured_address() throws Exception {
        gigs = "{\"upcoming\":[" + gig(1, "2026") + "],\"past\":[" + gig(2, "2025") + "],"
                + "\"countsByYear\":[{\"year\":2025,\"count\":1}]}";

        assertThat(fetcher.fetchAllGigs("eufory")).extracting(g -> g.path("gigId").asInt()).containsExactly(1, 2);
        assertThat(requested).containsExactly("/eufory");
    }

    @Test
    void played_gigs_the_page_leaves_out_are_read_from_the_archive() throws Exception {
        gigs = "{\"upcoming\":[" + gig(1, "2026") + "],\"past\":[" + gig(3, "2025") + "],"
                + "\"countsByYear\":[{\"year\":2025,\"count\":1},{\"year\":2024,\"count\":1},{\"year\":2023,\"count\":1}]}";

        List<JsonNode> all = fetcher.fetchAllGigs("eufory");

        assertThat(all).extracting(g -> g.path("gigId").asInt()).containsExactly(1, 3, 4, 5);
        assertThat(requested).contains("/api/band/gigs-archive?slug=eufory&page=1&perPage=50",
                "/api/band/gigs-archive?slug=eufory&page=2&perPage=50");
    }

    @Test
    void a_band_bandzone_doesnt_have_needs_the_user() {
        assertThatThrownBy(() -> fetcher.fetchAllGigs("nobody"))
                .isInstanceOfSatisfying(BandzonePortalException.class, e ->
                        assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER));
    }

    @Test
    void a_page_without_gig_data_is_a_changed_page_not_a_band_without_gigs() {
        gigs = null;
        assertThatThrownBy(() -> fetcher.fetchAllGigs("eufory"))
                .isInstanceOfSatisfying(BandzonePortalException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.TEMPORARY);
                    assertThat(e.getMessage()).contains("carries no gigs");
                });
    }

    private static String gig(int id, String year) {
        return "{\"gigId\":" + id + ",\"title\":\"Gig " + id + "\",\"dateFrom\":\"" + year
                + "-05-01T20:00:00.000Z\",\"cityName\":\"Praha\",\"statusId\":2,\"slug\":\"s" + id + "\"}";
    }

    /** A band page as Next.js renders it: its data pushed in chunks, the gigs somewhere inside. */
    private static String page(String gigs) {
        String data = "1:[\"$\",\"div\",null,{\"children\":\"Eufory\"}]\n"
                + (gigs == null ? "" : "2:[\"$\",\"$L2c\",null,{\"bandId\":1,\"gigs\":" + gigs + ",\"canEdit\":false}]\n");
        int half = data.length() / 2;
        return "<html><body><script>self.__next_f.push([0])</script>"
                + push(data.substring(0, half)) + push(data.substring(half)) + "</body></html>";
    }

    private static String push(String chunk) {
        return "<script>self.__next_f.push([1," + JSON.writeValueAsString(chunk) + "])</script>";
    }
}
