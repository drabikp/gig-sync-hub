package sk.drabikp.gigsynchub.calendar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.gigsynchub.calendar.application.CalendarCatalogService;
import sk.drabikp.gigsynchub.accounts.application.port.in.SecretBox;
import sk.drabikp.gigsynchub.calendar.application.CalendarReviewService;
import sk.drabikp.gigsynchub.calendar.application.CalendarSetupService;
import sk.drabikp.gigsynchub.calendar.application.port.out.BandCalendarStore;
import sk.drabikp.gigsynchub.calendar.application.port.out.BandProfileStore;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarDecisionStore;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarFeed;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarLinkStore;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarSnapshotStore;
import sk.drabikp.gigsynchub.calendar.domain.rules.BandProfile;
import sk.drabikp.gigsynchub.catalog.application.port.in.GigWrites;
import sk.drabikp.gigsynchub.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.gigsynchub.gig.application.port.out.GigRepository;
import sk.drabikp.gigsynchub.gig.application.port.out.LiveUpdates;
import sk.drabikp.gigsynchub.gig.application.port.out.Transactions;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
class CalendarConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CalendarConfiguration.class);

    @Bean
    CalendarReviewService calendarReviewService(
            CalendarFeed feed, BandProfileStore profileStore, CalendarDecisionStore decisionStore,
            CalendarSnapshotStore snapshotStore, CalendarLinkStore linkStore, GigRepository gigRepository,
            Transactions transactions, Clock clock, CalendarProperties calendar, LiveUpdates live) {
        return new CalendarReviewService(feed, profileStore, decisionStore, snapshotStore, linkStore, gigRepository,
                transactions, clock, new BandProfile.Thresholds(calendar.gigScore(), calendar.notGigScore(),
                        calendar.strongNegative()), live);
    }

    /** The calendar's gigs into the catalog, through the catalog's write path. */
    @Bean
    CalendarCatalogService calendarCatalogService(CalendarReviewService review, GigWrites catalogWrites,
                                                  ListGigsUseCase gigs, CalendarLinkStore linkStore,
                                                  Transactions transactions, LiveUpdates live) {
        return new CalendarCatalogService(review, catalogWrites, gigs, linkStore, transactions, live);
    }

    @Bean
    CalendarSetupService calendarSetupService(BandCalendarStore calendars, BandProfileStore profiles,
                                              LiveUpdates live, CalendarProperties calendar) {
        return new CalendarSetupService(calendars, profiles, live, ZoneId.of(calendar.zone()));
    }

    /**
     * On start: band #1's calendar from the configuration ({@code gigsynchub.calendar.ical-url}, from before
     * each band set up its own) kept once, while it has none; every address an older key sealed sealed again.
     */
    @Bean
    ApplicationRunner bandCalendars(CalendarSetupService setup, CalendarProperties calendar, SecretBox box) {
        return args -> {
            if (!calendar.icalUrl().isEmpty() && box.ready()
                    && setup.adopt(BandId.FIRST, calendar.icalUrl(), ZoneId.of(calendar.zone()))) {
                log.info("Band #1's calendar kept from gigsynchub.calendar.ical-url — set up on the page from now on");
            }
            int resealed = box.ready() ? setup.reseal() : 0;
            if (resealed > 0) {
                log.info("Sealed {} calendar addresses again with the active data key", resealed);
            }
        };
    }

}
