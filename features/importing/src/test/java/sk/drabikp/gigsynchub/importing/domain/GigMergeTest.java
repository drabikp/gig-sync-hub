package sk.drabikp.gigsynchub.importing.domain;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.gig.domain.Admission;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.GigSchedule;
import sk.drabikp.gigsynchub.gig.domain.Location;
import sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms;
import sk.drabikp.gigsynchub.importing.domain.ImportProposal.Version;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GigMergeTest {

    private static final GigSchedule SCHEDULE = GigSchedule.startingAt(
            ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, ZoneId.of("Europe/Prague")));

    private final Gig bandzone = new Gig(GigId.fresh(), "Fest", SCHEDULE, new Location("Lucerna Music Bar", "Praha", Country.CZECHIA),
            List.of("Support"), Admission.paid("200 Kč"), null, "https://fb.example/e", null,
            "https://img.example/p.jpg", false);
    private final Gig bandsintown = new Gig(GigId.fresh(), "Fest 2026", SCHEDULE, new Location("Lucerna", "Praha", Country.CZECHIA),
            List.of(), Admission.free(), "From Bandsintown", null, null, null, false);
    private final Version bz = new Version(TestPlatforms.BANDZONE, bandzone);
    private final Version bit = new Version(TestPlatforms.BANDSINTOWN, bandsintown);

    @Test
    void the_chosen_version_decides_what_the_gig_is_and_the_others_fill_the_gaps() {
        Gig merged = GigMerge.merge(null, bit, List.of(bz, bit), TestPlatforms.PLATFORMS);

        assertThat(merged.title()).isEqualTo("Fest 2026");
        assertThat(merged.location().venue()).isEqualTo("Lucerna");
        assertThat(merged.description()).isEqualTo("From Bandsintown");
        assertThat(merged.lineup()).containsExactly("Support");
        assertThat(merged.facebookUrl()).isEqualTo("https://fb.example/e");
        assertThat(merged.posterImageUrl()).isEqualTo("https://img.example/p.jpg");
    }

    @Test
    void entry_never_comes_from_bandsintown_when_another_version_knows_it() {
        assertThat(GigMerge.merge(null, bit, List.of(bz, bit), TestPlatforms.PLATFORMS).admission()).isEqualTo(Admission.paid("200 Kč"));
        assertThat(GigMerge.merge(null, bit, List.of(bit), TestPlatforms.PLATFORMS).admission()).isEqualTo(Admission.free());
    }

    @Test
    void an_end_time_known_only_to_another_version_is_kept() {
        Gig withEnd = new Gig(GigId.fresh(), "Fest", new GigSchedule(SCHEDULE.start(), SCHEDULE.start().plusHours(6)),
                bandsintown.location(), List.of(), Admission.free(), null, null, null, null, false);

        Gig merged = GigMerge.merge(null, bz, List.of(bz, new Version(TestPlatforms.BANDSINTOWN, withEnd)),
                TestPlatforms.PLATFORMS);

        assertThat(merged.schedule().end()).isEqualTo(SCHEDULE.start().plusHours(6));
    }

    @Test
    void the_catalogs_gig_keeps_its_identity_and_a_new_one_takes_the_chosen_copys() {
        Gig inTheCatalog = bandzone.cancel().reactivate();

        assertThat(GigMerge.merge(inTheCatalog, bit, List.of(bz, bit), TestPlatforms.PLATFORMS).id())
                .as("an import of a gig we have is an edit of it").isEqualTo(inTheCatalog.id());
        assertThat(GigMerge.merge(null, bit, List.of(bz, bit), TestPlatforms.PLATFORMS).id())
                .as("a gig new to the catalog is the copy that was read").isEqualTo(bandsintown.id());
    }

    @Test
    void a_gig_cancelled_in_any_version_stays_cancelled() {
        Version cancelledOnBandzone = new Version(TestPlatforms.BANDZONE, bandzone.cancel());

        assertThat(GigMerge.merge(null, bit, List.of(cancelledOnBandzone, bit), TestPlatforms.PLATFORMS).cancelled()).isTrue();
    }
}
