package sk.drabikp.gigsynchub.gig.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A gig's identity: its own, and the same text wherever it travels. */
class GigIdTest {

    @Test
    void a_fresh_identity_is_nobody_elses() {
        assertThat(GigId.fresh()).isNotEqualTo(GigId.fresh());
    }

    @Test
    void the_key_and_the_token_stand_for_the_same_identity() {
        GigId id = new GigId(UUID.fromString("3f2a5c41-9b7e-4d2a-8f10-0c5b6d7e8a90"));

        assertThat(id.key()).isEqualTo("3f2a5c41-9b7e-4d2a-8f10-0c5b6d7e8a90");
        assertThat(GigId.fromKey(id.key())).isEqualTo(id);
        assertThat(id.token()).matches("[A-Za-z0-9_-]+");
        assertThat(GigId.fromToken(id.token())).isEqualTo(id);
    }

    @Test
    void a_token_that_names_no_gig_is_refused_rather_than_read_as_some_other_gig() {
        assertThatThrownBy(() -> GigId.fromToken("not-a-gig"))
                .isInstanceOf(UnknownReferenceException.class)
                .hasMessageContaining("not a gig");
        assertThatThrownBy(() -> GigId.fromToken("")).isInstanceOf(UnknownReferenceException.class);
        // the old form, which named a gig by its day and venue: it says nothing here any more
        assertThatThrownBy(() -> GigId.fromToken("MjAyNi0xMC0wMXxrbHViIDAwNw"))
                .isInstanceOf(UnknownReferenceException.class);
    }
}
