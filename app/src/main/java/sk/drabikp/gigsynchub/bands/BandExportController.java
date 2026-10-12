package sk.drabikp.gigsynchub.bands;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.gigsynchub.accounts.application.port.in.UsersUseCase;
import sk.drabikp.gigsynchub.bands.api.BandDataApi;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.UUID;

/** Serves a band's data as one file (bands.yaml). */
@RestController
class BandExportController implements BandDataApi {

    private final UsersUseCase users;
    private final BandExport export;

    BandExportController(UsersUseCase users, BandExport export) {
        this.users = users;
        this.export = export;
    }

    @Override
    public ResponseEntity<Object> exportBand(UUID bandId) {
        BandExport.Export file = export.of(users.current().orElseThrow().id(), new BandId(bandId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.data());
    }
}
