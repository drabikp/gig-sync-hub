package sk.drabikp.gigsynchub.catalog.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.catalog.application.port.in.ExportGigsUseCase;
import sk.drabikp.gigsynchub.catalog.application.port.out.GigExporter;
import sk.drabikp.gigsynchub.gig.application.BadRequestException;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.TestGigs;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.sync.application.SyncFakes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.gigsynchub.gig.domain.TestBands.BAND;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDSINTOWN;
import static sk.drabikp.gigsynchub.gig.domain.platform.TestPlatforms.BANDZONE;

class GigExportServiceTest {

    private final SyncFakes.Gigs gigs = new SyncFakes.Gigs();

    @Test
    void exports_the_whole_catalog_in_the_platforms_own_format() {
        Gig a = TestGigs.gig("A", "Klub 007");
        Gig b = TestGigs.gig("B", "Barrák");
        gigs.save(BAND, a);
        gigs.save(BAND, b);
        GigExporter exporter = new GigExporter() {
            @Override
            public Platform platform() {
                return BANDSINTOWN;
            }

            @Override
            public String fileName() {
                return "gigs.csv";
            }

            @Override
            public String mediaType() {
                return "text/csv";
            }

            @Override
            public String export(BandId band, List<Gig> all) {
                return all.size() + " gigs";
            }
        };
        GigExportService service = new GigExportService(gigs, List.of(exporter));

        assertThat(service.exportable()).containsExactly(BANDSINTOWN);
        assertThat(service.export(BAND, BANDSINTOWN)).isEqualTo(new ExportGigsUseCase.ExportFile("gigs.csv", "text/csv",
                "2 gigs"));
        assertThatThrownBy(() -> service.export(BAND, BANDZONE)).isInstanceOf(BadRequestException.class);
    }
}
