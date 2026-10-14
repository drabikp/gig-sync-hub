package sk.drabikp.gigsynchub.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What an operator can see, and the one join everything else hangs off.
 *
 * <p>Metrics, logs and traces are three separate things until something links them. The link is the
 * trace id: it is put in the span by the tracer, carried into the logging context, and written into
 * every log line — so a slow request found in a metric leads to its trace, and the trace to the
 * lines that request wrote. That chain is three libraries agreeing on a field name, which is
 * exactly the kind of thing that works until a version bump and then quietly does not, with no
 * failure anywhere: the logs keep being written and the traces keep being collected, and only the
 * link between them is gone. So it is asserted here rather than noticed later in Grafana.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.endpoints.web.exposure.include=health,metrics,prometheus",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.show-details=always",
        "management.observations.key-values.application=gigsynchub",
        "management.tracing.sampling.probability=1.0",
        "logging.structured.format.file=ecs",
        "logging.file.name=target/observability-test.json"})
class ObservabilityTest {

    private static final Path LOG = Path.of("target/observability-test.json");

    @Autowired
    private Tracer tracer;
    @LocalManagementPort
    private int management;
    @LocalServerPort
    private int server;

    private final HttpClient http = HttpClient.newHttpClient();

    private String get(int port, String path) {
        try {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String fromManagementPort(String path) {
        return get(management, path);
    }

    @Test
    @DisplayName("a log line written inside a span carries that span's trace id")
    void a_log_line_written_inside_a_span_carries_that_spans_trace_id() throws Exception {
        Span span = tracer.nextSpan().name("a-test-span").start();
        String traceId;
        try (Tracer.SpanInScope inScope = tracer.withSpan(span)) {
            traceId = tracer.currentSpan().context().traceId();
            LoggerFactory.getLogger("gigsynchub.observability.test").error("something worth finding again");
        } finally {
            span.end();
        }

        List<String> lines = Files.readAllLines(LOG);
        JsonNode line = JsonMapper.builder().build().readTree(lines.stream()
                .filter(l -> l.contains("something worth finding again"))
                .reduce((first, last) -> last)
                .orElseThrow(() -> new AssertionError("the structured log file holds no such line")));

        // `traceId`/`spanId` at the top level, and not ECS's own `trace.id`: Spring Boot's ECS
        // formatter passes the logging context through under the keys Micrometer puts there. That
        // is the shape Grafana's Loki datasource must be told to look for, which is the other half
        // of why this is asserted rather than assumed.
        assertThat(line.path("traceId").asString(""))
                .as("the id in the line and the id in the span are the same id, or nothing joins them")
                .isEqualTo(traceId);
        assertThat(line.path("spanId").asString("")).isNotEmpty();
        assertThat(line.path("log").path("level").asString("")).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("Prometheus is served what this app is actually doing")
    void prometheus_is_served_what_this_app_is_actually_doing() {
        get(server, "/");                                // something for the server to have served

        String scraped = fromManagementPort("/actuator/prometheus");

        assertThat(scraped).as("the JVM's own").contains("jvm_memory_used_bytes");
        assertThat(scraped).as("what the app served").contains("http_server_requests_seconds");
        assertThat(scraped).as("the pool behind it").contains("hikaricp_connections");
        assertThat(scraped).as("this app's own, per band").contains("gigsynchub_sync_tasks");
        assertThat(scraped).as("every series says which app it came from, for the day something "
                + "else writes to the same Prometheus").contains("application=\"gigsynchub\"");
    }

    @Test
    @DisplayName("alive and ready are different questions")
    void alive_and_ready_are_different_questions() {
        assertThat(fromManagementPort("/actuator/health/liveness")).contains("\"status\":\"UP\"");
        assertThat(fromManagementPort("/actuator/health/readiness")).contains("\"status\":\"UP\"");
        assertThat(fromManagementPort("/actuator/health")).as("and the database is what readiness means here")
                .contains("\"db\"");
    }
}
