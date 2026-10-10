package sk.drabikp.bzscraper.live;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.UUID;

/**
 * Serves {@code GET /api/bands/{bandId}/events} (live.yaml): the page's stream of the band's live
 * updates (and those every band's pages show). Events are named
 * after what changed ({@code gigs}, {@code sync}, {@code check}, {@code import}, {@code calendar});
 * the page reads that part of the app again through the API. The one controller without a
 * generated interface: a stream (SseEmitter) is beyond what the generator can declare.
 */
@RestController
class LiveController {

    private final SseLiveUpdates live;

    LiveController(SseLiveUpdates live) {
        this.live = live;
    }

    @GetMapping(path = "/api/bands/{bandId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable UUID bandId) {
        return live.open(new BandId(bandId));
    }
}
