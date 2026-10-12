package sk.drabikp.gigsynchub.importing.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;
import sk.drabikp.gigsynchub.importing.adapter.in.rest.api.ImportApi;
import sk.drabikp.gigsynchub.importing.adapter.in.rest.api.ImportApplyRequest;
import sk.drabikp.gigsynchub.importing.adapter.in.rest.api.ImportReadRequest;
import sk.drabikp.gigsynchub.importing.adapter.in.rest.api.ImportResultResponse;
import sk.drabikp.gigsynchub.importing.adapter.in.rest.api.ImportStateResponse;
import sk.drabikp.gigsynchub.importing.application.port.in.ImportSessionUseCase;

import java.util.UUID;

/** Serves the import (import.yaml): the platforms are read in the background, the plan applied by index. */
@RestController
class ImportController implements ImportApi {

    private final ImportSessionUseCase session;
    private final Platforms platforms;
    private final ImportMapper mapper;

    ImportController(ImportSessionUseCase session, Platforms platforms, ImportMapper mapper) {
        this.session = session;
        this.platforms = platforms;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<ImportStateResponse> importState(UUID bandId) {
        return ResponseEntity.ok(mapper.toResponse(session.state(new BandId(bandId))));
    }

    @Override
    public ResponseEntity<Void> readForImport(UUID bandId, ImportReadRequest read) {
        session.read(new BandId(bandId), platforms.get(read.getPlatforms()));
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<ImportResultResponse> applyImport(UUID bandId, ImportApplyRequest apply) {
        return ResponseEntity.ok(mapper.toResponse(session.apply(new BandId(bandId),
                mapper.toChoices(apply.getDecisions()))));
    }
}
