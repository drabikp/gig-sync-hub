package sk.drabikp.gigsynchub.catalog.adapter.in.rest;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.gigsynchub.catalog.adapter.in.rest.api.ExportsApi;
import sk.drabikp.gigsynchub.catalog.application.port.in.ExportGigsUseCase.ExportFile;
import sk.drabikp.gigsynchub.catalog.application.port.in.ExportGigsUseCase;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Serves the per-platform file downloads (catalog.yaml, Exports): the file under its own name. */
@RestController
class ExportsController implements ExportsApi {

    private final Platforms platforms;
    private final ExportGigsUseCase export;

    ExportsController(Platforms platforms, ExportGigsUseCase export) {
        this.platforms = platforms;
        this.export = export;
    }

    @Override
    public ResponseEntity<Resource> exportGigs(UUID bandId, String platform) {
        ExportFile file = export.export(new BandId(bandId), platforms.get(platform));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(new ByteArrayResource(file.content().getBytes(StandardCharsets.UTF_8)));
    }
}
