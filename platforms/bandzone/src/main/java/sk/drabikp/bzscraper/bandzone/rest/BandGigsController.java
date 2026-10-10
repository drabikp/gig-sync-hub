package sk.drabikp.bzscraper.bandzone.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.bzscraper.bandzone.rest.api.BandGigsApi;
import sk.drabikp.bzscraper.bandzone.rest.api.GigSummaryResponse;

import java.util.List;

/** Serves the band's public gig list (bandzone.yaml). */
@RestController
class BandGigsController implements BandGigsApi {

    private final GigSummaries summaries;

    BandGigsController(GigSummaries summaries) {
        this.summaries = summaries;
    }

    @Override
    public ResponseEntity<List<GigSummaryResponse>> bandGigs(String bandSlug) {
        return ResponseEntity.ok(summaries.ofBand(bandSlug));
    }
}
