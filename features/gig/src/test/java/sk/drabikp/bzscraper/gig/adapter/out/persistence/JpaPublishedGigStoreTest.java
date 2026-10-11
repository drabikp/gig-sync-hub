package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.TestGigs;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.gig.domain.platform.TestPlatforms;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;

@SpringBootTest
@Transactional
class JpaPublishedGigStoreTest {

    @Autowired
    private JpaPublishedGigStore store;

    private final GigId klub = TestGigs.gig("A", "Klub 007").id();
    private final GigId barrak = TestGigs.gig("A", "Barrák").id();

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
}
