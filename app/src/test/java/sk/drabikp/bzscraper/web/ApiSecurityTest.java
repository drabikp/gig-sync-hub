package sk.drabikp.bzscraper.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import sk.drabikp.bzscraper.gig.domain.BandId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sk.drabikp.bzscraper.web.SignedIn.owner;

/**
 * The API is for whoever signed in through Rauthy, each band's addresses for its members; the page
 * itself is public and its own addresses load it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTest {

    private static final String BAND = "/api/bands/" + BandId.FIRST;

    @Autowired
    private MockMvc mvc;

    @Test
    void the_api_answers_401_until_signed_in() throws Exception {
        mvc.perform(get(BAND + "/gigs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bands")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platforms")).andExpect(status().isUnauthorized());
    }

    @Test
    void the_first_bands_owner_signs_in_and_uses_it() throws Exception {
        mvc.perform(get("/api/auth/me").with(owner())).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(SignedIn.OWNER)).andExpect(jsonPath("$.name").value("owner"));
        mvc.perform(get("/api/bands").with(owner())).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(BandId.FIRST.toString())))
                .andExpect(jsonPath("$[?(@.id == '" + BandId.FIRST + "')].role", hasItem("OWNER")));
        mvc.perform(get(BAND + "/gigs").with(owner())).andExpect(status().isOk())
                .andExpect(jsonPath("$.gigs").isArray()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/platforms").with(owner())).andExpect(status().isOk());
        mvc.perform(get(BAND + "/sync/status").with(owner())).andExpect(status().isOk());
        mvc.perform(get(BAND + "/calendar").with(owner())).andExpect(status().isOk());
    }

    @Test
    void someone_signed_in_without_the_band_doesnt_find_it() throws Exception {
        mvc.perform(get("/api/auth/me").with(SignedIn.as("fan@elsewhere.test"))).andExpect(status().isOk());
        mvc.perform(get(BAND + "/gigs").with(SignedIn.as("fan@elsewhere.test")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("notFound"));
        mvc.perform(get("/api/bands/not-a-band/gigs").with(SignedIn.as("fan@elsewhere.test")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void an_owner_connects_a_platform_and_its_password_is_never_sent_back() throws Exception {
        String account = "{\"settings\":{\"login\":\"band@x.test\",\"bandSlug\":\"the-band\"},"
                + "\"secrets\":{\"password\":\"hunter2\"}}";
        mvc.perform(put(BAND + "/platform-accounts/BANDZONE").with(owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.secretsSet[0]").value("password"))
                .andExpect(content().string(not(containsString("hunter2"))));
        mvc.perform(get(BAND + "/platform-accounts").with(owner())).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.platform == 'BANDZONE')].settings.login", hasItem("band@x.test")))
                .andExpect(content().string(not(containsString("hunter2"))));
        mvc.perform(put(BAND + "/platform-accounts/BANDZONE").with(owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{},\"secrets\":{}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalidAccount"));
        mvc.perform(put(BAND + "/platform-accounts/NOWHERE").with(owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(account))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(BAND + "/platform-accounts/BANDZONE").with(owner()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void a_bands_calendar_address_is_kept_but_only_whose_it_is_is_shown() throws Exception {
        mvc.perform(put(BAND + "/calendar/connection").with(owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"https://1.1.1.1/ical/secret-part/basic.ics\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value("1.1.1.1"))
                .andExpect(content().string(not(containsString("secret-part"))));
        mvc.perform(get(BAND + "/calendar/connection").with(owner())).andExpect(jsonPath("$.connected").value(true));
        mvc.perform(delete(BAND + "/calendar/connection").with(owner()).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void signing_in_goes_to_rauthy_with_pkce_and_comes_back_to_the_app() throws Exception {
        MvcResult start = mvc.perform(get("/oauth2/authorization/rauthy")).andExpect(status().is3xxRedirection())
                .andReturn();
        UriComponents rauthy = UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl()).build();

        assertThat(rauthy.toUriString()).startsWith("https://auth.test/auth/v1/oidc/authorize");
        assertThat(rauthy.getQueryParams().getFirst("client_id")).isEqualTo("bzscraper");
        assertThat(rauthy.getQueryParams().getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(rauthy.getQueryParams().getFirst("redirect_uri")).isEqualTo("http://localhost/login/oauth2/code/rauthy");
        assertThat(rauthy.getQueryParams().getFirst("scope")).contains("groups");
    }

    @Test
    void signing_out_ends_the_session_and_says_where_rauthy_ends_its_own() throws Exception {
        mvc.perform(post("/api/auth/logout").with(owner())).andExpect(status().isForbidden());   // no CSRF token

        mvc.perform(post("/api/auth/logout").with(owner()).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect").value(allOf(
                        startsWith("https://auth.test/auth/v1/oidc/logout?"),
                        containsString("post_logout_redirect_uri=http://localhost/"),
                        containsString("id_token_hint="))));
    }

    @Test
    void a_change_needs_the_csrf_token() throws Exception {
        mvc.perform(post(BAND + "/gigs").with(owner()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void a_refused_change_is_a_409_with_its_code() throws Exception {
        mvc.perform(post(BAND + "/gigs").with(owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"city\":\"Košice\",\"country\":\"SLOVAKIA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalidGig"))
                .andExpect(jsonPath("$.args.fields").value("title,date,time"));
        mvc.perform(post("/api/bands").with(owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Taken\",\"slug\":\"Not An Address\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalidBand"));
    }

    @Test
    void a_request_that_names_nothing_is_a_400_and_something_gone_a_404() throws Exception {
        mvc.perform(get(BAND + "/gigs/not-a-gig").with(owner()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("badRequest"));
        mvc.perform(get(BAND + "/exports/NOWHERE").with(owner()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("badRequest"));
        mvc.perform(get(BAND + "/sync/tasks").param("show", "nonsense").with(owner()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("badRequest"));
        mvc.perform(post(BAND + "/sync/tasks/999999/retry").with(owner()).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("notFound"));
    }

    @Test
    void the_page_is_public_and_its_own_addresses_load_it() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/b/first/calendar")).andExpect(status().isOk()).andExpect(content().string(
                containsString("<div id=\"root\">")));
        mvc.perform(get("/b/first/gig/some-gig/edit")).andExpect(status().isOk()).andExpect(content().string(
                containsString("<div id=\"root\">")));
        mvc.perform(get("/b/first/calendar/abc123@google.com/add")).andExpect(status().isOk());
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
        mvc.perform(get("/api/nothing-here")).andExpect(status().isUnauthorized());
    }
}
