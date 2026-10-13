package sk.drabikp.gigsynchub.bandzone.scrape;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reads a band page's gig articles. Bandzone leaves parts out (no end, no bands, no entry), so
 * every part is optional: a missing one is null, never an error; a gig without a link to its
 * concert page is dropped (it has no id).
 */
class BandzoneHtmlParser {

    private static final Logger logger = LoggerFactory.getLogger(BandzoneHtmlParser.class);

    private final Clock clock;

    BandzoneHtmlParser(Clock clock) {
        this.clock = clock;
    }

    List<GigSummary> parse(Elements elements) {
        return elements.stream().map(this::parseGig).flatMap(Optional::stream)
                .sorted(Comparator.comparing(GigSummary::start, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private Optional<GigSummary> parseGig(Element article) {
        String url = first(article, "a.gig__link").map(a -> a.attr("href")).orElse(null);
        Optional<String> bzId = bzId(url);
        if (bzId.isEmpty()) {
            logger.warn("Skipping a Bandzone gig without a concert link ({})", url);
            return Optional.empty();
        }
        ZonedDateTime start = dateTime(article.getElementsByAttributeValue("itemprop", "startDate")
                .select("time:not(.is-hide)").first());
        ZonedDateTime end = dateTime(article.getElementsByAttributeValue("itemprop", "endDate").select("time").first());
        if (start != null && end != null) {
            end = plausibleEnd(start, end, LocalDate.now(clock.withZone(end.getZone())));
        }
        Optional<Element> location = Optional.ofNullable(
                article.getElementsByAttributeValue("itemprop", "location").first());

        return Optional.of(GigSummary.GigSummaryBuilder.aGigSummary()
                .setCity(location.flatMap(l -> first(l, "strong")).map(e -> e.attr("title")).orElse(null))
                .setVenue(location.flatMap(l -> first(l, "span")).map(e -> e.select("span").attr("title"))
                        .orElse(null))
                .setUrl(url)
                .setBzId(bzId.get())
                .setBands(first(article, "div.gig__info__item--bands-list").map(BandzoneHtmlParser::bands).orElse(null))
                .setTitle(first(article, "h3.gig__title").map(Element::text).orElse(null))
                .setEntryFee(first(article, "div.gig__info__item--entry").map(e -> e.attr("title")).orElse(null))
                .setIsCancelled(article.hasClass("gig--canceled"))
                .setStart(start)
                .setEnd(end)
                .build());
    }

    /**
     * Bandzone renders an end given only as a time ("until 22:00") with TODAY's date — seen on
     * a 2020 concert whose end came out six years later. An end on today's date, more than two
     * weeks after a start that isn't today, is read as that time on the gig's own night.
     */
    static ZonedDateTime plausibleEnd(ZonedDateTime start, ZonedDateTime end, LocalDate today) {
        if (!end.toLocalDate().equals(today) || start.toLocalDate().equals(today)
                || !end.isAfter(start.plusDays(14))) {
            return end;
        }
        ZonedDateTime sameNight = start.toLocalDate().atTime(end.toLocalTime()).atZone(end.getZone());
        return sameNight.isBefore(start) ? sameNight.plusDays(1) : sameNight;
    }

    /** The concert id from {@code /koncert/<id>-<name>}. */
    static Optional<String> bzId(String url) {
        if (url == null) {
            return Optional.empty();
        }
        String[] parts = url.split("/");
        if (parts.length < 3 || parts[2].indexOf('-') <= 0) {
            return Optional.empty();
        }
        return Optional.of(parts[2].substring(0, parts[2].indexOf('-')));
    }

    private static Optional<Element> first(Element parent, String css) {
        return Optional.ofNullable(parent.selectFirst(css));
    }

    private static ZonedDateTime dateTime(Element time) {
        String value = time == null ? "" : time.attr("datetime");
        if (value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException _) {
            logger.debug("Unreadable Bandzone date '{}'", value);
            return null;
        }
    }

    private static List<String> bands(Element list) {
        return list.select("h5.gig__bands__name").stream()
                .map(Element::text)
                .map(s -> s.endsWith(",") ? s.substring(0, s.length() - 1) : s)
                .toList();
    }
}
