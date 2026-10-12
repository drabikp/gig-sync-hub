package sk.drabikp.gigsynchub.bandzone.scrape;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.accounts.application.AccountsFakes;
import sk.drabikp.gigsynchub.bandzone.BandzonePlatform;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.importing.domain.ImportedGig;
import sk.drabikp.gigsynchub.sync.application.port.out.FailureKind;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BandzoneGigImporterTest {

    private final GigProvider provider = mock(GigProvider.class);

    private static GigSummary summary(String bzId, boolean cancelled) {
        return GigSummary.GigSummaryBuilder.aGigSummary()
                .setBzId(bzId)
                .setTitle("Fest")
                .setStart(ZonedDateTime.of(2026, 9, 15, 20, 0, 0, 0, ZoneId.of("Europe/Prague")))
                .setCity("Praha, ČR")
                .setVenue("Klub 007")
                .setBands(List.of())
                .setIsCancelled(cancelled)
                .build();
    }

    /** Band #1 with its Bandzone account at {@code slug}. */
    private static AccountsFakes.Accounts band(String slug) {
        return new AccountsFakes.Accounts().with(BandId.FIRST, BandzonePlatform.PLATFORM,
                Map.of("login", "band@x.test", "bandSlug", slug), Map.of("password", "p"));
    }

    @Test
    void each_gig_keeps_its_bandzone_concert_id_and_cancelled_state() throws Exception {
        when(provider.findByBand("eufory")).thenReturn(List.of(summary("556604", false), summary("556605", true)));

        List<ImportedGig> gigs = new BandzoneGigImporter(provider, band("eufory")).importGigs(BandId.FIRST);

        assertThat(gigs).extracting(ImportedGig::externalRef).containsExactly("556604", "556605");
        assertThat(gigs).allMatch(g -> g.platform().equals(BandzonePlatform.PLATFORM));
        assertThat(gigs.get(1).gig().cancelled()).isTrue();
    }

    @Test
    void a_concert_listed_twice_is_imported_once() throws Exception {
        when(provider.findByBand("eufory")).thenReturn(List.of(summary("556604", false), summary("556604", false)));

        assertThat(new BandzoneGigImporter(provider, band("eufory")).importGigs(BandId.FIRST)).hasSize(1);
    }

    @Test
    void reads_the_page_of_the_bands_own_account_and_needs_one() throws Exception {
        BandId other = new BandId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        AccountsFakes.Accounts accounts = band("eufory").with(other, BandzonePlatform.PLATFORM,
                Map.of("login", "other@x.test", "bandSlug", "Other-Band"), Map.of("password", "p"));
        when(provider.findByBand("other-band")).thenReturn(List.of(summary("1", false)));

        assertThat(new BandzoneGigImporter(provider, accounts).importGigs(other)).extracting(ImportedGig::externalRef)
                .containsExactly("1");
        assertThatThrownBy(() -> new BandzoneGigImporter(provider, new AccountsFakes.Accounts())
                .importGigs(BandId.FIRST))
                .isInstanceOfSatisfying(BandzonePortalException.class, e -> {
                    assertThat(e.getMessage()).contains("isn't connected for this band");
                    assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER);
                });
    }
}
