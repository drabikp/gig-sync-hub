package sk.drabikp.bzscraper.calendar.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.AddToCatalogRequest;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarApi;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarConnectionRequest;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarConnectionResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarOverviewResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.LinkRequest;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.LinkedResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.RuleKindResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.RuleRequest;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.RuleResponse;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.VerdictRequest;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarCatalogUseCase;
import sk.drabikp.bzscraper.calendar.application.port.in.CalendarSetupUseCase;
import sk.drabikp.bzscraper.calendar.application.port.in.ReviewCalendarUseCase;
import sk.drabikp.bzscraper.gig.api.GigFormMapper;
import sk.drabikp.bzscraper.gig.api.RefMapper;
import sk.drabikp.bzscraper.gig.application.InvalidGigException;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;
import java.util.UUID;

/**
 * Serves the band calendar (calendar.yaml): reviewing it, and setting it up (which calendar, the band's
 * own rules). The calendar itself is never changed; the use cases announce their changes on the live
 * updates themselves.
 */
@RestController
class CalendarController implements CalendarApi {

    private final ReviewCalendarUseCase review;
    private final CalendarCatalogUseCase catalog;
    private final CalendarSetupUseCase setup;
    private final CalendarMapper mapper;
    private final GigFormMapper forms;
    private final RefMapper refs;

    CalendarController(ReviewCalendarUseCase review, CalendarCatalogUseCase catalog, CalendarSetupUseCase setup,
                       CalendarMapper mapper, GigFormMapper forms, RefMapper refs) {
        this.review = review;
        this.catalog = catalog;
        this.setup = setup;
        this.refs = refs;
        this.mapper = mapper;
        this.forms = forms;
    }

    @Override
    public ResponseEntity<CalendarOverviewResponse> calendarOverview(UUID bandId) {
        BandId band = refs.toBandId(bandId);
        return ResponseEntity.ok(review.configured(band) ? mapper.toResponse(review.overview(band))
                : mapper.unconfigured());
    }

    @Override
    public ResponseEntity<CalendarOverviewResponse> readCalendar(UUID bandId) {
        return ResponseEntity.ok(mapper.toResponse(review.read(refs.toBandId(bandId))));
    }

    @Override
    public ResponseEntity<Void> seenAll(UUID bandId) {
        review.seenAll(refs.toBandId(bandId));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<LinkedResponse> linkSameDay(UUID bandId) {
        return ResponseEntity.ok(new LinkedResponse(catalog.linkSameDayGigs(refs.toBandId(bandId))));
    }

    @Override
    public ResponseEntity<List<RuleResponse>> calendarRules(UUID bandId) {
        return ResponseEntity.ok(mapper.toResponse(review.rules(refs.toBandId(bandId))));
    }

    @Override
    public ResponseEntity<List<RuleResponse>> replaceCalendarRules(UUID bandId, List<RuleRequest> rules) {
        return ResponseEntity.ok(mapper.toResponse(setup.replaceOwnRules(refs.toBandId(bandId), mapper.toDrafts(rules))));
    }

    @Override
    public ResponseEntity<List<RuleKindResponse>> calendarRuleKinds(UUID bandId) {
        return ResponseEntity.ok(mapper.toKindResponses(setup.ruleKinds()));
    }

    @Override
    public ResponseEntity<CalendarConnectionResponse> calendarConnection(UUID bandId) {
        return ResponseEntity.ok(mapper.toResponse(setup.connection(refs.toBandId(bandId))));
    }

    @Override
    public ResponseEntity<CalendarConnectionResponse> connectCalendar(UUID bandId, CalendarConnectionRequest request) {
        return ResponseEntity.ok(mapper.toResponse(setup.connect(refs.toBandId(bandId), request.getAddress(),
                request.getZone())));
    }

    @Override
    public ResponseEntity<Void> disconnectCalendar(UUID bandId) {
        setup.disconnect(refs.toBandId(bandId));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> decideEvent(UUID bandId, String eventId, VerdictRequest verdict) {
        review.decide(refs.toBandId(bandId), eventId, mapper.toKind(verdict.getKind()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> forgetVerdict(UUID bandId, String eventId) {
        review.forget(refs.toBandId(bandId), eventId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> seenEvent(UUID bandId, String eventId) {
        review.seen(refs.toBandId(bandId), eventId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> addEventToCatalog(UUID bandId, String eventId, AddToCatalogRequest request) {
        catalog.addToCatalog(refs.toBandId(bandId), eventId, InvalidGigException.gigOf(forms.toDraft(request.getGig())));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> linkEvent(UUID bandId, String eventId, LinkRequest link) {
        catalog.link(refs.toBandId(bandId), eventId, refs.toGigId(link.getGig()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unlinkEvent(UUID bandId, String eventId) {
        catalog.unlink(refs.toBandId(bandId), eventId);
        return ResponseEntity.noContent().build();
    }
}
