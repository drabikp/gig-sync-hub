package sk.drabikp.bzscraper.catalog.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigDetailResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigEditRequest;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigEditedResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigListResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigResponse;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigSelectionRequest;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.GigsApi;
import sk.drabikp.bzscraper.catalog.adapter.in.rest.api.QueueResultResponse;
import sk.drabikp.bzscraper.catalog.application.port.in.CancelGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.DeleteGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.SaveGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.UpdateGigUseCase;
import sk.drabikp.bzscraper.gig.api.GigFormMapper;
import sk.drabikp.bzscraper.gig.api.GigFormRequest;
import sk.drabikp.bzscraper.gig.api.RefMapper;
import sk.drabikp.bzscraper.gig.application.InvalidGigException;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.port.in.PublishGigsUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.ResyncGigUseCase;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Serves a band's gigs (catalog.yaml, Gigs): the platforms follow every change through the sync. */
@RestController
class GigsController implements GigsApi {

    private final ListGigsUseCase gigs;
    private final SaveGigUseCase saveGig;
    private final UpdateGigUseCase updateGig;
    private final CancelGigUseCase cancelGig;
    private final DeleteGigUseCase deleteGig;
    private final PublishGigsUseCase publishGigs;
    private final ResyncGigUseCase resyncGigs;
    private final Platforms platforms;
    private final GigViews views;
    private final GigMapper mapper;
    private final GigFormMapper forms;
    private final RefMapper refs;

    GigsController(ListGigsUseCase gigs, SaveGigUseCase saveGig, UpdateGigUseCase updateGig,
                   CancelGigUseCase cancelGig, DeleteGigUseCase deleteGig, PublishGigsUseCase publishGigs,
                   ResyncGigUseCase resyncGigs, Platforms platforms, GigViews views, GigMapper mapper,
                   GigFormMapper forms, RefMapper refs) {
        this.gigs = gigs;
        this.saveGig = saveGig;
        this.updateGig = updateGig;
        this.cancelGig = cancelGig;
        this.deleteGig = deleteGig;
        this.publishGigs = publishGigs;
        this.resyncGigs = resyncGigs;
        this.platforms = platforms;
        this.views = views;
        this.mapper = mapper;
        this.forms = forms;
        this.refs = refs;
    }

    @Override
    public ResponseEntity<GigListResponse> listGigs(UUID bandId, String period, String q, String show,
                                                    String after, Integer size) {
        return ResponseEntity.ok(views.list(refs.toBandId(bandId), mapper.toQuery(period, q, after, size),
                mapper.toShow(show)));
    }

    @Override
    public ResponseEntity<GigDetailResponse> gigDetail(UUID bandId, String id) {
        BandId band = refs.toBandId(bandId);
        return ResponseEntity.ok(views.detail(band, gigs.gig(band, refs.toGigId(id))));
    }

    @Override
    public ResponseEntity<GigResponse> addGig(UUID bandId, GigFormRequest form) {
        BandId band = refs.toBandId(bandId);
        Gig gig = InvalidGigException.gigOf(forms.toDraft(form));
        saveGig.add(band, gig);
        return ResponseEntity.status(HttpStatus.CREATED).body(views.of(band, gig));
    }

    @Override
    public ResponseEntity<GigEditedResponse> editGig(UUID bandId, String id, GigEditRequest edit) {
        BandId band = refs.toBandId(bandId);
        Gig seen = GigRevisions.asSeen(gigs.gig(band, refs.toGigId(id)), edit.getRev());
        Gig updated = InvalidGigException.gigOf(forms.toDraft(edit.getGig()));
        return ResponseEntity.ok(views.edited(band, updated, updateGig.update(band, seen, updated)));
    }

    @Override
    public ResponseEntity<QueueResultResponse> deleteGig(UUID bandId, String id) {
        BandId band = refs.toBandId(bandId);
        return ResponseEntity.ok(mapper.toResponse(deleteGig.delete(band, gigs.gig(band, refs.toGigId(id)).id())));
    }

    @Override
    public ResponseEntity<QueueResultResponse> cancelGig(UUID bandId, String id) {
        BandId band = refs.toBandId(bandId);
        return ResponseEntity.ok(mapper.toResponse(cancelGig.cancel(band, gigs.gig(band, refs.toGigId(id)).id())));
    }

    @Override
    public ResponseEntity<QueueResultResponse> reactivateGig(UUID bandId, String id) {
        BandId band = refs.toBandId(bandId);
        return ResponseEntity.ok(mapper.toResponse(cancelGig.reactivate(band, gigs.gig(band, refs.toGigId(id)).id())));
    }

    @Override
    public ResponseEntity<QueueResultResponse> publishGigs(UUID bandId, GigSelectionRequest selection) {
        BandId band = refs.toBandId(bandId);
        List<Gig> selected = mapper.toGigIds(selection.getGigs()).stream().map(id -> gigs.gig(band, id)).toList();
        return ResponseEntity.ok(mapper.toResponse(publishGigs.publish(band,
                new LinkedHashSet<>(platforms.get(selection.getPlatforms())), selected)));
    }

    @Override
    public ResponseEntity<QueueResultResponse> resyncGigs(UUID bandId, GigSelectionRequest selection) {
        BandId band = refs.toBandId(bandId);
        List<GigId> selected = mapper.toGigIds(selection.getGigs()).stream().map(id -> gigs.gig(band, id).id())
                .toList();
        return ResponseEntity.ok(mapper.toResponse(resyncGigs.resync(band, selected)));
    }
}
