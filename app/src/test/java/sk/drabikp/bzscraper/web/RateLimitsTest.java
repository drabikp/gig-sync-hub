package sk.drabikp.bzscraper.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One user may ask so much of the API in a minute; more is a 429 saying when to try again. */
@SpringBootTest(properties = "bzscraper.limits.requests-per-minute=3")
@AutoConfigureMockMvc
class RateLimitsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void too_many_requests_in_a_minute_are_answered_429_per_user() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/bands").with(SignedIn.as("busy@band.test"))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/bands").with(SignedIn.as("busy@band.test")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("tooManyRequests"));
        mvc.perform(get("/api/bands").with(SignedIn.as("calm@band.test"))).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isOk());              // the page itself isn't counted
    }
}
