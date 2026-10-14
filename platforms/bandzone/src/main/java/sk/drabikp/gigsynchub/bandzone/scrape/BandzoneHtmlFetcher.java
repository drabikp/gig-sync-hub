package sk.drabikp.gigsynchub.bandzone.scrape;

import org.jsoup.HttpStatusException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.BandzoneProperties;
import sk.drabikp.gigsynchub.portal.PlatformEgress;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Reads a band's public page on Bandzone ({@code gigsynchub.bandzone.base-url}): the year tabs it
 * lists, then every year's gig articles and the planned ones, all at once. Each page has
 * {@link #PAGE_TIMEOUT}, the whole read {@link #READ_TIMEOUT}.
 */
@Component
class BandzoneHtmlFetcher {

    /** Pseudo-year for the band's "Plánované koncerty" (planned gigs) tab. */
    private static final String UPCOMING = "upcoming";
    private static final Duration PAGE_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final Logger logger = LoggerFactory.getLogger(BandzoneHtmlFetcher.class);

    private final String baseUrl;
    private final PlatformEgress egress;

    BandzoneHtmlFetcher(BandzoneProperties properties) {
        this.baseUrl = properties.baseUrl();
        this.egress = properties.egress();
    }

    Elements fetchAllGigs(String bandSlug) throws BandzonePortalException {
        Document document;
        try {
            document = get(UriComponentsBuilder.fromUriString(baseUrl).pathSegment(bandSlug)
                    .queryParam("at", "gig").queryParam("gy", "0").build().toUriString());
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                throw BandzonePortalException.needsUser("Bandzone has no band '" + bandSlug
                        + "' — check the band's address in Settings.");
            }
            throw BandzonePortalException.temporary("Bandzone answered " + e.getStatusCode() + " for the band page.", e);
        } catch (IOException e) {
            throw BandzonePortalException.temporary("Could not read Bandzone: " + e.getMessage(), e);
        }

        // the year tabs list played gigs only; planned ones are on the separate "upcoming" tab
        List<String> pages = new ArrayList<>(parseYears(document));
        pages.add(UPCOMING);
        List<Callable<Elements>> reads = pages.stream()
                .<Callable<Elements>>map(year -> () -> getArticlesForYear(bandSlug, year))
                .toList();

        Elements articles = new Elements();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Elements>> read = executor.invokeAll(reads, READ_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            for (Future<Elements> year : read) {
                articles.addAll(year.get());
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw BandzonePortalException.busy("Interrupted while reading Bandzone.");
        } catch (CancellationException e) {
            throw BandzonePortalException.temporary("Bandzone did not give the band's gigs within "
                    + READ_TIMEOUT.toSeconds() + " s.", e);
        } catch (ExecutionException e) {
            throw BandzonePortalException.temporary("Could not read a year of Bandzone gigs: "
                    + e.getCause().getMessage(), e.getCause());
        }
        return articles;
    }

    private List<String> parseYears(Document document) {
        Element tabs = document.selectFirst("div.years");
        List<String> years = tabs == null ? List.of()
                : tabs.select("span:not(.years-upcoming) a").stream().map(Element::text).toList();
        logger.debug("Parsed years: {}", years);
        return years;
    }

    private Elements getArticlesForYear(String bandSlug, String year) throws IOException {
        logger.debug("Downloading data for year {}", year);
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(baseUrl).pathSegment(bandSlug).queryParam("at", "gig");
        Elements elements = get((UPCOMING.equals(year) ? uri : uri.queryParam("gy", year)).build().toUriString())
                .select("article.gig");
        logger.debug("Data for year {} downloaded", year);
        return elements;
    }

    /**
     * Through the same address the rest of Bandzone's traffic uses ({@link PlatformEgress}). This
     * one matters more than it looks: reading a band's history asks for every year tab, and a
     * burst is the thing the pacing elsewhere exists to avoid — so if this server's traffic is to
     * come from somewhere, it should ALL come from there, not just the half that carries a session.
     */
    private Document get(String url) throws IOException {
        Connection connection = Jsoup.connect(url).timeout((int) PAGE_TIMEOUT.toMillis());
        if (egress.configured()) {
            connection.proxy(egress.host(), egress.port());
            if (egress.username() != null) {
                connection.auth(context -> context.isProxy()
                        ? new PasswordAuthentication(egress.username(), egress.password().toCharArray())
                        : null);
            }
        }
        return connection.get();
    }
}
