package sk.drabikp.bzscraper.catalog.application.port.in;

import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.List;

/** The catalog as a file to import on a platform by hand — for the platforms that offer one. */
public interface ExportGigsUseCase {

    /** The platforms there is an export for. */
    List<Platform> exportable();

    ExportFile export(BandId band, Platform platform);

    record ExportFile(String fileName, String mediaType, String content) {
    }
}
