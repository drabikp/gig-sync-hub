package sk.drabikp.bzscraper.catalog.application;

import org.jmolecules.architecture.hexagonal.Application;
import sk.drabikp.bzscraper.catalog.application.port.in.ExportGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.out.GigExporter;
import sk.drabikp.bzscraper.gig.application.BadRequestException;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Exports the whole catalog through the platform's {@link GigExporter}. */
@Application
public class GigExportService implements ExportGigsUseCase {

    private final GigRepository gigRepository;
    private final Map<Platform, GigExporter> exporters = new TreeMap<>();

    public GigExportService(GigRepository gigRepository, List<GigExporter> exporters) {
        this.gigRepository = gigRepository;
        exporters.forEach(e -> {
            if (this.exporters.put(e.platform(), e) != null) {
                throw new IllegalStateException("Two exporters for platform " + e.platform());
            }
        });
    }

    @Override
    public List<Platform> exportable() {
        return List.copyOf(exporters.keySet());
    }

    @Override
    public ExportFile export(BandId band, Platform platform) {
        GigExporter exporter = exporters.get(platform);
        if (exporter == null) {
            throw new BadRequestException("No export for " + platform);
        }
        return new ExportFile(exporter.fileName(), exporter.mediaType(), exporter.export(band, gigRepository.findAll(band)));
    }
}
