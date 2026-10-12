package sk.drabikp.gigsynchub.bandsintown.portal.http;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.sync.application.port.out.FailureKind;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Connecting a band to Bandsintown: what it has to paste ({@link BitConnection}) and which artist of the
 * sign-in that makes it ({@link HttpBitSignIn#theArtist}). The band pastes the refresh token and nothing
 * else — the manager comes back from the portal's own sign-in, the artist from the list of what that
 * manager looks after — so neither of those numbers is ever the band's to find.
 */
class BitConnectingTest {

    private static final String TOKEN = "eyJjdHkiOiJKV1QiLCJlbmMiOiJBMjU2R0NN-a-refresh-token";

    @Test
    void the_refresh_token_on_its_own_is_a_connection() throws Exception {
        BitConnection connection = BitConnection.read("  " + TOKEN + "  ");

        assertThat(connection.refreshToken()).isEqualTo(TOKEN);
        assertThat(connection.managerId()).as("worked out at sign-in, not pasted").isEmpty();
        assertThat(connection.actorId()).isEmpty();
        assertThat(connection.fingerprint()).as("ours, made up for this band").isNotBlank();
    }

    @Test
    void the_line_an_older_paste_gave_still_works_ids_and_all() throws Exception {
        BitConnection connection = BitConnection.read(
                "{\"refreshToken\":\"" + TOKEN + "\",\"managerId\":\"1415161\",\"actorId\":\"270917\"}");

        assertThat(connection.refreshToken()).isEqualTo(TOKEN);
        assertThat(connection.managerId()).isEqualTo("1415161");
        assertThat(connection.actorId()).isEqualTo("270917");
    }

    @Test
    void the_quotes_a_console_prints_round_a_string_are_not_part_of_the_token() throws Exception {
        assertThat(BitConnection.read("\"" + TOKEN + "\"").refreshToken()).isEqualTo(TOKEN);
        assertThat(BitConnection.read("'" + TOKEN + "'").refreshToken()).isEqualTo(TOKEN);
    }

    @Test
    void two_bands_connecting_are_two_devices_to_the_portal() throws Exception {
        assertThat(BitConnection.read(TOKEN).fingerprint())
                .isNotEqualTo(BitConnection.read(TOKEN).fingerprint());
    }

    @Test
    void nothing_pasted_asks_the_band_again_rather_than_storing_an_account_that_cannot_work() {
        assertThatThrownBy(() -> BitConnection.read("   "))
                .isInstanceOfSatisfying(BitPortalException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER);
                    assertThat(e.getMessage()).contains("copy what the button");
                });
    }

    @Test
    void the_token_is_never_in_what_is_logged() throws Exception {
        assertThat(BitConnection.read(TOKEN)).asString().doesNotContain(TOKEN).contains("***");
    }

    @Test
    void the_artists_come_from_the_portal_asked_for_a_kind_which_is_what_makes_it_answer() throws Exception {
        try (BitHttpFixture portal = new BitHttpFixture()) {
            portal.artists.put("270917", "Eufory (Band)");
            BitApi api = new BitWeb(portal.baseUrl(), portal.credentials());

            assertThat(HttpBitSignIn.artists(api))
                    .containsExactly(new HttpBitSignIn.Actor("270917", "Eufory (Band)"));
            assertThat(portal.writes).as("asking is a read, nothing is written").isEmpty();
        }
    }

    @Test
    void asked_without_the_kind_the_portal_answers_nothing_so_the_client_must_always_send_it() throws Exception {
        try (BitHttpFixture portal = new BitHttpFixture()) {
            portal.artists.put("270917", "Eufory (Band)");
            BitApi api = new BitWeb(portal.baseUrl(), portal.credentials());

            assertThat(api.get(api.managerPath("managed-actors")).payloadRows())
                    .as("the trap: an empty list looks like an account with no artists")
                    .isEmpty();
        }
    }

    @Test
    void a_sign_in_that_looks_after_one_artist_needs_no_choosing() throws Exception {
        assertThat(HttpBitSignIn.theArtist(List.of(new HttpBitSignIn.Actor("270917", "Eufory (Band)")), ""))
                .isEqualTo("270917");
    }

    @Test
    void a_manager_of_several_artists_is_matched_by_the_name_the_band_gave() throws Exception {
        List<HttpBitSignIn.Actor> artists = List.of(new HttpBitSignIn.Actor("1", "Another Band"),
                new HttpBitSignIn.Actor("270917", "Eufory (Band)"));

        assertThat(HttpBitSignIn.theArtist(artists, "eufory (band)")).isEqualTo("270917");
    }

    @Test
    void a_name_matching_none_of_them_says_which_there_are() {
        List<HttpBitSignIn.Actor> artists = List.of(new HttpBitSignIn.Actor("1", "Another Band"),
                new HttpBitSignIn.Actor("2", "Third Band"));

        assertThatThrownBy(() -> HttpBitSignIn.theArtist(artists, "Eufory"))
                .isInstanceOfSatisfying(BitPortalException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER);
                    assertThat(e.getMessage()).contains("Another Band, Third Band");
                });
    }

    @Test
    void a_sign_in_looking_after_nobody_is_the_wrong_account() {
        assertThatThrownBy(() -> HttpBitSignIn.theArtist(List.<HttpBitSignIn.Actor>of(), "Eufory"))
                .isInstanceOfSatisfying(BitPortalException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER);
                    assertThat(e.getMessage()).contains("looks after no artist");
                });
    }
}
