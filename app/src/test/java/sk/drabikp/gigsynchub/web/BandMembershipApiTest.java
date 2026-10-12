package sk.drabikp.gigsynchub.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sk.drabikp.gigsynchub.web.SignedIn.owner;

/**
 * Bringing people into a band through the API, and what owners see of it (multi-band plan, phase 4): an
 * invitation link joins only whom it's for; every change is in the audit log; the band's data exports as one
 * file — never a secret. The app's connections work under row-level security here, as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BandMembershipApiTest {

    private static final String BAND = "/api/bands/" + BandId.FIRST;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    /**
     * The app's connections are not the database's owner. Row-level security is gone (V12) and the
     * queries keep the bands apart themselves, but the restricted role stays, for its own reasons:
     * the app cannot change the schema it validates against, and it cannot read the migration
     * history. Those two are what {@code set role gigsynchub_app} is still here for, so they are what
     * is asserted — not the policies, which no longer exist.
     */
    @Test
    void the_apps_connections_work_as_the_restricted_role_not_the_owner() {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("gigsynchub_app");

        assertThatThrownBy(() -> jdbc.execute("alter table gig add column sneaked_in text"))
                .as("the app cannot change its own schema").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.queryForObject("select count(*) from flyway_schema_history", Integer.class))
                .as("nor read the migration history").isInstanceOf(DataAccessException.class);

        assertThat(jdbc.queryForObject("select count(*) from gig", Integer.class))
                .as("and it does read the tables it is meant to").isNotNull();
    }

    @Test
    void an_invitation_link_joins_whom_its_for_once() throws Exception {
        String created = mvc.perform(post(BAND + "/invitations").with(owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"Joiner@Band.test\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitation.email").value("joiner@band.test"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(created, "$.token");

        mvc.perform(get("/api/invitations/" + token).with(SignedIn.as("someone@else.test"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.forYou").value(false)).andExpect(jsonPath("$.maskedEmail").value("j***@band.test"));
        mvc.perform(post("/api/invitations/" + token + "/accept").with(SignedIn.as("someone@else.test")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invitationNotForYou"));
        mvc.perform(get(BAND + "/gigs").with(SignedIn.as("joiner@band.test"))).andExpect(status().isNotFound());

        mvc.perform(post("/api/invitations/" + token + "/accept").with(SignedIn.as("joiner@band.test")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MANAGER"));

        mvc.perform(get(BAND + "/gigs").with(SignedIn.as("joiner@band.test"))).andExpect(status().isOk());
        mvc.perform(post("/api/invitations/" + token + "/accept").with(SignedIn.as("joiner@band.test")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invitationGone"));
        mvc.perform(post(BAND + "/invitations").with(SignedIn.as("joiner@band.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@y.test\",\"role\":\"OWNER\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ownerOnly"));
        mvc.perform(get(BAND + "/export").with(SignedIn.as("joiner@band.test")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ownerOnly"));
    }

    @Test
    void owners_see_who_changed_what_and_export_the_band_without_a_secret() throws Exception {
        mvc.perform(put(BAND + "/calendar/connection").with(owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"https://1.1.1.1/ical/secret-part/basic.ics\"}"))
                .andExpect(status().isOk());

        mvc.perform(get(BAND + "/audit").with(owner())).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("connectCalendar")))
                .andExpect(jsonPath("$[?(@.action == 'connectCalendar')].who", hasItem("owner")));
        mvc.perform(get(BAND + "/export").with(owner())).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(jsonPath("$.band.id").value(BandId.FIRST.toString()))
                .andExpect(jsonPath("$.gigs").isArray())
                .andExpect(jsonPath("$.calendar.connection.host").value("1.1.1.1"))
                .andExpect(content().string(not(containsString("secret-part"))));
    }

    @Test
    void the_public_list_of_a_bands_gigs_reads_that_band_under_row_level_security() throws Exception {
        mvc.perform(get("/gigs/first")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    void a_bands_public_list_is_its_owners_choice() throws Exception {
        String bands = mvc.perform(get("/api/bands").with(owner())).andReturn().getResponse().getContentAsString();
        String name = JsonPath.read(bands, "$[0].name");
        try {
            mvc.perform(put(BAND).with(owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + name + "\",\"slug\":\"first\",\"publicList\":false}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.publicList").value(false));
            mvc.perform(get("/gigs/first")).andExpect(status().isNotFound());
        } finally {
            mvc.perform(put(BAND).with(owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + name + "\",\"slug\":\"first\",\"publicList\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.publicList").value(true));
        }
        mvc.perform(get("/gigs/first")).andExpect(status().isOk());
    }
}
