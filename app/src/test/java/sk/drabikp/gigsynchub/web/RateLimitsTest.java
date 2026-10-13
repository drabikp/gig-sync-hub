package sk.drabikp.gigsynchub.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One user may ask so much of the API in a minute; more is a 429 saying when to try again — and
 * what keeps the counts is itself bounded, which is the part a limiter is easiest to get wrong in.
 */
@SpringBootTest(properties = {"gigsynchub.limits.requests-per-minute=3",
        "gigsynchub.limits.tracked-callers=100"})
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

    /**
     * A caller the counting has forgotten is allowed again — which is what being bounded COSTS, and
     * is the right side to err on: the map forgets the least recently SEEN caller, and whoever is
     * flooding is by definition the most recently seen. Before this the map simply grew, and a
     * flood from many addresses filled the heap rather than meeting the limiter.
     *
     * <p>Counted before anyone is signed in, so the key is the address and no user is made: these
     * are a hundred and twenty callers, not a hundred and twenty people.
     */
    @Test
    void the_counting_forgets_the_quietest_caller_rather_than_growing_for_ever() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/bands").with(from("10.9.0.1"))).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/bands").with(from("10.9.0.1"))).andExpect(status().isTooManyRequests());

        for (int caller = 0; caller < 120; caller++) {    // more callers than the map holds
            mvc.perform(get("/api/bands").with(from("10.9.1." + caller))).andExpect(status().isUnauthorized());
        }

        mvc.perform(get("/api/bands").with(from("10.9.0.1")))
                .andExpect(status().isUnauthorized());    // forgotten, so counted from nothing again
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
