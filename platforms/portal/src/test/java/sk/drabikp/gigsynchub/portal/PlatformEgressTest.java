package sk.drabikp.gigsynchub.portal;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * That a configured proxy is actually USED — asserted against something standing in for one,
 * because "the builder was called" is not the same claim and is the one that is easy to believe.
 *
 * <p>A proxy sees a plain HTTP request as an absolute-form request line
 * ({@code GET http://host/path}) rather than the origin form a server sees, which is how this
 * test knows the bytes went through it rather than past it.
 */
class PlatformEgressTest {

    private HttpServer proxy;
    private final List<String> seen = new ArrayList<>();

    private int standInForAProxy() throws IOException {
        proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.createContext("/", exchange -> {
            seen.add(exchange.getRequestURI().toString());
            byte[] answer = "through the proxy".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        proxy.start();
        return proxy.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (proxy != null) {
            proxy.stop(0);
        }
    }

    @Test
    @DisplayName("a configured proxy is where the request goes")
    void a_configured_proxy_is_where_the_request_goes() throws Exception {
        int port = standInForAProxy();
        HttpClient client = new PlatformEgress("127.0.0.1", port, null, null)
                .on(HttpClient.newBuilder()).build();

        HttpResponse<String> answer = client.send(
                HttpRequest.newBuilder(URI.create("http://bandzone.invalid/eufory?at=gig")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(answer.body()).isEqualTo("through the proxy");
        assertThat(seen).singleElement().as("the whole address, which is what a proxy is asked for")
                .isEqualTo("http://bandzone.invalid/eufory?at=gig");
    }

    @Test
    @DisplayName("no proxy configured is straight out of this machine")
    void no_proxy_configured_is_straight_out_of_this_machine() throws Exception {
        int port = standInForAProxy();
        HttpClient client = PlatformEgress.DIRECT.on(HttpClient.newBuilder()).build();

        assertThatThrownBy(() -> client.send(
                HttpRequest.newBuilder(URI.create("http://bandzone.invalid/eufory")).build(),
                HttpResponse.BodyHandlers.ofString()))
                .as("nothing resolves that name, which is the proof it was not handed to the proxy")
                .isInstanceOf(IOException.class);
        assertThat(seen).isEmpty();
        assertThat(port).isPositive();
    }

    @Test
    @DisplayName("what it says about itself never includes the password")
    void what_it_says_about_itself_never_includes_the_password() {
        assertThat(new PlatformEgress("proxy.test", 3128, "band", "hunter2"))
                .hasToString("PlatformEgress[proxy.test:3128, as band ***]");
        assertThat(PlatformEgress.DIRECT).hasToString("PlatformEgress[direct]");
        assertThat(new PlatformEgress("  ", 0, null, null).configured())
                .as("blank is no proxy, not a proxy called nothing").isFalse();
    }

    @Test
    @DisplayName("a port that is not one is said so at once")
    void a_port_that_is_not_one_is_said_so_at_once() {
        assertThatThrownBy(() -> new PlatformEgress("proxy.test", 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 65535");
    }
}
