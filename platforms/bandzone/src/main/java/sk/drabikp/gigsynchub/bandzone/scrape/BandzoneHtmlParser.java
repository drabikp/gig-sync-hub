package sk.drabikp.gigsynchub.bandzone.scrape;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.gigsynchub.bandzone.portal.BandzonePageData;
import tools.jackson.databind.JsonNode;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reads the gigs a band's page carries ({@link BandzonePageData}). Bandzone leaves parts out (no time,
 * no end, no venue, no entry), so every part is optional: a missing one is null, never an error; a gig
 * without its concert id is dropped. The page's list says nothing of who else plays, so {@code bands}
 * stays null.
 */
class BandzoneHtmlParser {

    /** Bandzone's {@code statusId} of a cancelled concert (2 approved, 4 postponed, 6 draft, 7 played). */
    static final int CANCELLED = 3;

    private static final Logger logger = LoggerFactory.getLogger(BandzoneHtmlParser.class);

    List<GigSummary> parse(List<JsonNode> gigs) {
        return gigs.stream().map(BandzoneHtmlParser::parseGig).flatMap(Optional::stream)
                .sorted(Comparator.comparing(GigSummary::start, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static Optional<GigSummary> parseGig(JsonNode gig) {
        String id = BandzonePageData.text(gig, "gigId");
        if (id == null || !id.chars().allMatch(Character::isDigit)) {
            logger.warn("Skipping a Bandzone gig without a concert id ({})", BandzonePageData.text(gig, "title"));
            return Optional.empty();
        }
        String slug = BandzonePageData.text(gig, "slug");
        ZonedDateTime start = BandzonePageData.dateTime(gig, "dateFrom");
        String venue = BandzonePageData.text(gig, "venueName");
        return Optional.of(GigSummary.GigSummaryBuilder.aGigSummary()
                .setCity(BandzonePageData.text(gig, "cityName"))
                .setVenue(venue != null ? venue : BandzonePageData.text(gig, "venueText"))
                .setUrl("/koncert/" + id + (slug == null ? "" : "-" + slug))
                .setBzId(id)
                .setBands(null)
                .setTitle(BandzonePageData.text(gig, "title"))
                .setEntryFee(BandzonePageData.text(gig, "ticketPriceText"))
                .setIsCancelled(gig.path("statusId").asInt() == CANCELLED)
                .setStart(start)
                .setEnd(end(start, BandzonePageData.dateTime(gig, "dateUntil")))
                .build());
    }

    /**
     * The end, when there is one: Bandzone writes a concert without an end as ending at midnight of its
     * own day, which is no end at all — and an end before the start is none either.
     */
    static ZonedDateTime end(ZonedDateTime start, ZonedDateTime end) {
        if (end == null || start == null) {
            return end;
        }
        boolean noEnd = end.toLocalDate().equals(start.toLocalDate()) && end.toLocalTime().equals(LocalTime.MIDNIGHT);
        return noEnd || end.isBefore(start) ? null : end;
    }
}
