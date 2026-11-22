package sk.drabikp.gigsynchub.bandzone.scrape;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.BandzoneProperties;
import sk.drabikp.gigsynchub.bandzone.portal.BandzonePageData;
import sk.drabikp.gigsynchub.portal.PlatformEgress;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a band's gigs from its public page on Bandzone ({@code gigsynchub.bandzone.base-url}). Since the
 * redesign (2026-11-22) the page carries every gig it can show as data ({@link BandzonePageData}): the
 * planned ones and the played ones, with how many were played each year. Should the played ones it carries
 * fall short of those counts, the rest are read from the archive the page's "show more" reads
 * ({@code /api/band/gigs-archive}), a page at a time. Each request has {@link #PAGE_TIMEOUT}.
 */
@Component
class BandzoneHtmlFetcher {

    private static final Duration PAGE_TIMEOUT = Duration.ofSeconds(20);
    /** The archive's own largest page (it answers 50 when asked for more). */
    private static final int ARCHIVE_PAGE = 50;
    /** However many years a band has played, its archive ends: this many pages is a page that loops. */
    private static final int ARCHIVE_PAGES_AT_MOST = 200;
    private static final Logger logger = LoggerFactory.getLogger(BandzoneHtmlFetcher.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final PlatformEgress egress;

    BandzoneHtmlFetcher(BandzoneProperties properties) {
        this.baseUrl = properties.baseUrl();
        this.egress = properties.egress();
    }

    /** Every gig the band's page knows, planned first, each as the page's own data has it. */
    List<JsonNode> fetchAllGigs(String bandSlug) throws BandzonePortalException {
        String page;
        try {
            page = get(UriComponentsBuilder.fromUriString(baseUrl).pathSegment(bandSlug).build().toUriString());
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                throw BandzonePortalException.needsUser("Bandzone has no band '" + bandSlug
                        + "' — check the band's address in Settings.");
            }
            throw BandzonePortalException.temporary("Bandzone answered " + e.getStatusCode() + " for the band page.", e);
        } catch (IOException e) {
            throw BandzonePortalException.temporary("Could not read Bandzone: " + e.getMessage(), e);
        }
        JsonNode gigs = BandzonePageData.of(page).object("gigs", "{\"upcoming\"").orElseThrow(() ->
                BandzonePortalException.temporary("Bandzone's page of '" + bandSlug + "' carries no gigs — has "
                        + "Bandzone changed its pages?"));

        List<JsonNode> all = new ArrayList<>();
        gigs.path("upcoming").forEach(all::add);
        List<JsonNode> past = new ArrayList<>();
        gigs.path("past").forEach(past::add);
        int played = 0;
        for (JsonNode year : gigs.path("countsByYear")) {
            played += year.path("count").asInt();
        }
        if (past.size() < played) {
            logger.debug("The page of '{}' carries {} of its {} played gigs; reading the archive", bandSlug,
                    past.size(), played);
            past = archive(bandSlug, played);
        }
        all.addAll(past);
        return all;
    }

    /** Every played gig, from the archive, a page at a time. */
    private List<JsonNode> archive(String bandSlug, int played) throws BandzonePortalException {
        List<JsonNode> past = new ArrayList<>();
        for (int number = 1; number <= ARCHIVE_PAGES_AT_MOST && past.size() < played; number++) {
            JsonNode page;
            try {
                page = JSON.readTree(get(UriComponentsBuilder.fromUriString(baseUrl).path("/api/band/gigs-archive")
                        .queryParam("slug", bandSlug).queryParam("page", number).queryParam("perPage", ARCHIVE_PAGE)
                        .build().toUriString()));
            } catch (IOException | JacksonException e) {
                throw BandzonePortalException.temporary("Could not read Bandzone's archive of '" + bandSlug + "': "
                        + e.getMessage(), e);
            }
            page.path("items").forEach(past::add);
            if (page.path("items").isEmpty() || number >= page.path("totalPages").asInt()) {
                break;
            }
        }
        return past;
    }

    /**
     * Through the same address the rest of Bandzone's traffic uses ({@link PlatformEgress}): if this
     * server's traffic is to come from somewhere, it should ALL come from there, not just the half that
     * carries a session.
     */
    private String get(String url) throws IOException {
        Connection connection = Jsoup.connect(url).timeout((int) PAGE_TIMEOUT.toMillis())
                .ignoreContentType(true).maxBodySize(0);
        if (egress.configured()) {
            connection.proxy(egress.host(), egress.port());
            if (egress.username() != null) {
                connection.auth(context -> context.isProxy()
                        ? new PasswordAuthentication(egress.username(), egress.password().toCharArray())
                        : null);
            }
        }
        return connection.execute().body();
    }
}
