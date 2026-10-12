package sk.drabikp.gigsynchub.check.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.gigsynchub.check.adapter.in.rest.api.CheckApi;
import sk.drabikp.gigsynchub.check.adapter.in.rest.api.CheckStateResponse;
import sk.drabikp.gigsynchub.check.adapter.in.rest.api.DriftRefRequest;
import sk.drabikp.gigsynchub.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.gigsynchub.check.application.port.in.StartPlatformCheckUseCase;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;

import java.util.UUID;

/** Serves the platform check (check.yaml). */
@RestController
class CheckController implements CheckApi {

    private final CheckPlatformsUseCase check;
    private final StartPlatformCheckUseCase start;
    private final Platforms platforms;
    private final CheckMapper mapper;

    CheckController(CheckPlatformsUseCase check, StartPlatformCheckUseCase start, Platforms platforms,
                    CheckMapper mapper) {
        this.check = check;
        this.start = start;
        this.platforms = platforms;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<CheckStateResponse> checkState(UUID bandId) {
        BandId band = new BandId(bandId);
        return ResponseEntity.ok(mapper.toResponse(check.running(band), check.lastCheck(band).orElse(null)));
    }

    @Override
    public ResponseEntity<Void> runCheck(UUID bandId) {
        start.start(new BandId(bandId));
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> forgetLink(UUID bandId, DriftRefRequest ref) {
        check.forget(new BandId(bandId), platforms.get(ref.getPlatform()), GigId.fromToken(ref.getGig()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> dismissDrift(UUID bandId, DriftRefRequest ref) {
        check.dismiss(new BandId(bandId), platforms.get(ref.getPlatform()), GigId.fromToken(ref.getGig()));
        return ResponseEntity.noContent().build();
    }
}
