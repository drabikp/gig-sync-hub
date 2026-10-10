package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.PlatformResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.PlatformsApi;
import sk.drabikp.bzscraper.catalog.application.port.in.ExportGigsUseCase;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;

import java.util.List;

/** Serves what the pages need to know of the platforms (catalog.yaml, Platforms). */
@RestController
class PlatformsController implements PlatformsApi {

    private final Platforms platforms;
    private final ExportGigsUseCase export;
    private final GigMapper mapper;

    PlatformsController(Platforms platforms, ExportGigsUseCase export, GigMapper mapper) {
        this.platforms = platforms;
        this.export = export;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<PlatformResponse>> listPlatforms() {
        return ResponseEntity.ok(mapper.toResponse(platforms.traits(), export.exportable()));
    }
}
