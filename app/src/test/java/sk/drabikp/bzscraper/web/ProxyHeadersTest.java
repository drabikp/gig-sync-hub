package sk.drabikp.bzscraper.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind the proxy (HTTPS ends there): the request the app gets is plain HTTP, and the proxy's
 * X-Forwarded-Proto says how it really came in — then Rauthy is told to send the user back over
 * HTTPS, and the session's cookie is Secure. A real server, as Tomcat itself reads the header
 * (here from 127.0.0.1, a trusted address).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyHeadersTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void signing_in_over_https_at_the_proxy_comes_back_over_https_with_a_secure_session() throws Exception {
        HttpResponse<Void> start = startSignIn(true);

        assertThat(redirectUri(start)).startsWith("https://");
        assertThat(sessionCookie(start)).contains("Secure");
    }

    @Test
    void signing_in_over_plain_http_on_this_machine_stays_plain() throws Exception {
        HttpResponse<Void> start = startSignIn(false);

        assertThat(redirectUri(start)).startsWith("http://");
        assertThat(sessionCookie(start)).doesNotContain("Secure");
    }

    private HttpResponse<Void> startSignIn(boolean https) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/oauth2/authorization/rauthy"));
        if (https) {
            request.header("X-Forwarded-Proto", "https");
        }
        HttpResponse<Void> response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(302);
        return response;
    }

    /** Where Rauthy is to send the user back. */
    private static String redirectUri(HttpResponse<Void> start) {
        return UriComponentsBuilder.fromUriString(start.headers().firstValue("Location").orElseThrow()).build()
                .getQueryParams().getFirst("redirect_uri");
    }

    /** The session that keeps the sign-in's state until the user comes back. */
    private static String sessionCookie(HttpResponse<Void> start) {
        return start.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("JSESSIONID="))
                .findFirst().orElseThrow();
    }
}
