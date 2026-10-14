package sk.drabikp.gigsynchub.gig.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.TestBands;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;
import sk.drabikp.gigsynchub.gig.domain.platform.Publication;
import sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.OTHER;

@SpringBootTest
@Transactional
class JpaPublishedGigStoreTest {

    @Autowired
    private JpaPublishedGigStore store;

    @Autowired
    private JdbcTemplate jdbc;

    private final GigId klub = TestGigs.gig("A", "Klub 007").id();
    private final GigId barrak = TestGigs.gig("A", "Barrák").id();

    /**
     * The second band, which {@code published_gig_band} insists exists before a row may name it. Made
     * here rather than relied upon: the test database has only the first band, and a class that waits
     * for a sibling to leave the row behind passes or fails on the order the classes happen to run in
     * — which is how this one passed on a developer's machine for weeks and failed on the first CI run
     * that put it first.
     */
    @BeforeEach
    void anotherBand() {
        jdbc.update(TestBands.INSERT_OTHER);
    }

    @Test
    void records_and_reads_back_a_published_gig_per_platform() {
        store.record(BAND, TestPlatforms.BANDZONE, klub, "100");

        assertThat(store.isPublished(BAND, TestPlatforms.BANDZONE, klub)).isTrue();
        assertThat(store.externalRef(BAND, TestPlatforms.BANDZONE, klub)).contains("100");
        assertThat(store.isPublished(BAND, TestPlatforms.BANDSINTOWN, klub)).isFalse();
        assertThat(store.isPublished(BAND, TestPlatforms.BANDZONE, barrak)).isFalse();
    }

    @Test
    void a_publish_without_an_external_id_is_published_but_has_no_ref() {
        store.record(BAND, TestPlatforms.BANDSINTOWN, klub, null);

        assertThat(store.isPublished(BAND, TestPlatforms.BANDSINTOWN, klub)).isTrue();
        assertThat(store.externalRef(BAND, TestPlatforms.BANDSINTOWN, klub)).isEmpty();
    }

    @Test
    void recording_again_replaces_the_ref() {
        store.record(BAND, TestPlatforms.BANDZONE, klub, "100");
        store.record(BAND, TestPlatforms.BANDZONE, klub, "200");

        assertThat(store.externalRef(BAND, TestPlatforms.BANDZONE, klub)).contains("200");
    }

    @Test
    void remove_forgets_the_gig_on_that_platform_only() {
        store.record(BAND, TestPlatforms.BANDZONE, klub, "100");
        store.record(BAND, TestPlatforms.BANDSINTOWN, klub, null);

        store.remove(BAND, TestPlatforms.BANDZONE, klub);

        assertThat(store.isPublished(BAND, TestPlatforms.BANDZONE, klub)).isFalse();
        assertThat(store.isPublished(BAND, TestPlatforms.BANDSINTOWN, klub)).isTrue();
    }

    @Test
    void lists_every_publication_with_its_gig_identity() {
        store.record(BAND, TestPlatforms.BANDZONE, klub, "100");
        store.record(BAND, TestPlatforms.BANDSINTOWN, barrak, null);

        assertThat(store.all(BAND)).containsExactlyInAnyOrder(
                new Publication(TestPlatforms.BANDZONE, klub, "100"),
                new Publication(TestPlatforms.BANDSINTOWN, barrak, null));
    }

    /**
     * A publication is one band's. Two bands can hold the same gig identity on the same platform —
     * nothing stops them, the key is (band, platform, gig) — and neither may see, overwrite or forget
     * the other's. The same gig id on purpose: it is the hardest case, and the one a predicate that
     * left the band out would get wrong while every other test still passed.
     */
    @Test
    void a_publication_is_one_bands_even_when_both_bands_hold_the_same_gig_on_the_same_platform() {
        store.record(BAND, TestPlatforms.BANDZONE, klub, "100");
        store.record(OTHER, TestPlatforms.BANDZONE, klub, "999");

        assertThat(store.externalRef(BAND, TestPlatforms.BANDZONE, klub)).contains("100");
        assertThat(store.externalRef(OTHER, TestPlatforms.BANDZONE, klub)).contains("999");
        assertThat(store.all(BAND)).containsExactly(new Publication(TestPlatforms.BANDZONE, klub, "100"));
        assertThat(store.all(OTHER)).containsExactly(new Publication(TestPlatforms.BANDZONE, klub, "999"));

        store.remove(BAND, TestPlatforms.BANDZONE, klub);

        assertThat(store.isPublished(BAND, TestPlatforms.BANDZONE, klub)).isFalse();
        assertThat(store.isPublished(OTHER, TestPlatforms.BANDZONE, klub))
                .as("forgetting one band's copy leaves the other band's alone").isTrue();
    }

    @Test
    void one_bands_publication_is_not_visible_to_the_other() {
        store.record(BAND, TestPlatforms.BANDZONE, klub, "100");

        assertThat(store.isPublished(OTHER, TestPlatforms.BANDZONE, klub)).isFalse();
        assertThat(store.externalRef(OTHER, TestPlatforms.BANDZONE, klub)).isEmpty();
        assertThat(store.all(OTHER)).isEmpty();
    }
}
