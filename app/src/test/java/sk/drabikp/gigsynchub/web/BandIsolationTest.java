package sk.drabikp.gigsynchub.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static sk.drabikp.gigsynchub.web.SignedIn.owner;

/**
 * A band is only its members': for EVERY band address the specs list (read from the OpenAPI files, so
 * a new one can't be forgotten), someone who isn't the band's member gets 404 — before anything is
 * read or written — while its member gets through.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BandIsolationTest {

    private static final Pattern PATH = Pattern.compile("^  (/api/bands/\\{bandId}[^:]*):\\s*$");
    private static final Pattern METHOD = Pattern.compile("^    (get|post|put|patch|delete):\\s*$");
    private static final Pattern VARIABLE = Pattern.compile("\\{(?!bandId)[^}]+}");

    @Autowired
    private MockMvc mvc;

    /** A band address and how it is asked. */
    private record Operation(HttpMethod method, String path) {

        MockHttpServletRequestBuilder on(String band) {
            String address = VARIABLE.matcher(path.replace("{bandId}", band)).replaceAll("1");
            return request(method, address).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}");
        }
    }

    /** Every band address of every spec in the repository. */
    private static List<Operation> bandOperations() throws IOException {
        Path root = Path.of("").toAbsolutePath().getParent();
        List<Path> specs;
        try (Stream<Path> files = Files.walk(root)) {
            specs = files.filter(p -> p.toString().contains("/src/main/openapi/") && p.toString().endsWith(".yaml"))
                    .filter(p -> !p.toString().contains("/node_modules/") && !p.toString().contains("/target/"))
                    .toList();
        }
        List<Operation> operations = new ArrayList<>();
        for (Path spec : specs) {
            String path = null;
            for (String line : Files.readAllLines(spec)) {
                Matcher pathLine = PATH.matcher(line);
                Matcher methodLine = METHOD.matcher(line);
                if (pathLine.matches()) {
                    path = pathLine.group(1);
                } else if (!line.startsWith("    ") && !line.isBlank()) {
                    path = null;
                } else if (path != null && methodLine.matches()) {
                    operations.add(new Operation(HttpMethod.valueOf(methodLine.group(1).toUpperCase(Locale.ROOT)), path));
                }
            }
        }
        return operations;
    }

    @Test
    void every_band_address_is_404_for_someone_who_isnt_the_bands_member() throws Exception {
        List<Operation> operations = bandOperations();
        assertThat(operations).as("the specs' band addresses").hasSizeGreaterThan(30);
        String strangersBand = JsonPath.read(mvc.perform(post("/api/bands").with(SignedIn.as("other@band.test"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other band\",\"slug\":\"other-band-" + UUID.randomUUID().toString()
                                .substring(0, 8) + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        for (Operation operation : operations) {
            int status = mvc.perform(operation.on(strangersBand).with(owner())).andReturn().getResponse().getStatus();
            assertThat(status).as(operation.method() + " " + operation.path() + " on another's band").isEqualTo(404);
            int theirs = mvc.perform(operation.on(BandId.FIRST.toString()).with(SignedIn.as("other@band.test")))
                    .andReturn().getResponse().getStatus();
            assertThat(theirs).as(operation.method() + " " + operation.path() + " on band #1 by another").isEqualTo(404);
        }
    }

    @Test
    void a_member_gets_through_to_the_band() throws Exception {
        int status = mvc.perform(new Operation(HttpMethod.GET, "/api/bands/{bandId}/gigs").on(BandId.FIRST.toString())
                .with(owner())).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
    }
}
