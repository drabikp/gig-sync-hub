package sk.drabikp.gigsynchub.bandzone.portal.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.accounts.application.AccountsFakes;
import sk.drabikp.gigsynchub.bandzone.BandzonePlatform;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.portal.BandzoneSession;
import sk.drabikp.gigsynchub.bandzone.portal.PosterDownloader;
import sk.drabikp.gigsynchub.gig.domain.Address;
import sk.drabikp.gigsynchub.gig.domain.Admission;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.Country;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.GigId;
import sk.drabikp.gigsynchub.gig.domain.GigSchedule;
import sk.drabikp.gigsynchub.gig.domain.Location;
import sk.drabikp.gigsynchub.sync.application.port.out.FailureKind;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The HTTP client against a stand-in that talks as Bandzone's pages do since the redesign
 * ({@link BandzoneHttpFixture}): every operation of a session, the sign-in and its reuse, and the cases
 * that need the user.
 */
class HttpBandzonePortalClientTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final Location HRANICE_NA_MORAVE = new Location("Garáž u Nováků", "Hranice", Country.CZECHIA,
            new Address(null, "753 01", "okres Přerov", "Olomoucký kraj", 49.548, 17.735));

    private BandzoneHttpFixture bandzone;
    private HttpBandzonePortalClient client;

    @BeforeEach
    void setUp() throws IOException {
        bandzone = new BandzoneHttpFixture();
        client = client(Map.of("password", BandzoneHttpFixture.PASSWORD));
    }

    @AfterEach
    void tearDown() {
        bandzone.close();
    }

    @Test
    void creates_edits_cancels_and_deletes_a_concert_as_the_pages_do() throws Exception {
        Gig gig = new Gig(GigId.fresh(), "Release show", new GigSchedule(at(2026, 11, 12, 20, 0), at(2026, 11, 13, 1, 0)),
                new Location("Lucerna Music Bar", "Praha", Country.CZECHIA), List.of("Incertus Posterus", "Stubby"),
                Admission.paid("200 Kč"), "first line\nsecond line", "https://www.facebook.com/events/1", null,
                bandzone.baseUrl() + "/poster.png", false);
        Gig edited = new Gig(GigId.fresh(), "Release show (moved)", GigSchedule.startingAt(at(2026, 11, 14, 21, 30)), HRANICE_NA_MORAVE,
                List.of("Incertus Posterus"), Admission.voluntary(), "edited", null, null, null, false);

        try (BandzoneSession session = client.openSession(BandId.FIRST)) {
            assertThat(session.bandName()).isEqualTo(BandzoneHttpFixture.BAND_NAME);
            String id = session.createGig(gig);
            BandzoneHttpFixture.Concert concert = bandzone.concerts.get(Long.parseLong(id));
            assertThat(concert.title).isEqualTo("Release show");
            assertThat(concert.cityId).isEqualTo(4109L);
            assertThat(concert.dateFrom).as("Prague's wall clock, marked UTC").isEqualTo("2026-11-12T20:00:00.000Z");
            assertThat(concert.bands).as("the band itself performs").containsOnlyKeys(BandzoneHttpFixture.BAND_ID);
            assertThat(concert.ticketsKind).isEqualTo("paid");
            assertThat(concert.ticketRows.get(0).path("price").asString()).isEqualTo("200");

            session.updateGig(id, gig);
            assertThat(concert.coverBlobId).as("the poster uploaded and set").isNotNull();
            assertThat(concert.venueId).as("the club linked").isEqualTo(125L);
            assertThat(concert.dateUntil).isEqualTo("2026-11-13T01:00:00.000Z");
            assertThat(concert.description).isEqualTo("first line\nsecond line");
            assertThat(concert.facebookUrl).isEqualTo("https://www.facebook.com/events/1");
            assertThat(concert.bands.values()).containsExactlyInAnyOrder(BandzoneHttpFixture.BAND_NAME, "Incertus Posterus");
            assertThat(concert.guests.values()).as("a profile that only starts alike is never linked")
                    .containsExactly("Stubby");

            session.updateGig(id, edited);
            assertThat(concert.cityId).as("the Hranice in okres Přerov, of three").isEqualTo(1603L);
            assertThat(concert.venueId).as("a club Bandzone doesn't know: typed").isNull();
            assertThat(concert.venueText).isEqualTo("Garáž u Nováků");
            assertThat(concert.dateUntil).as("the end cleared").isNull();
            assertThat(concert.facebookUrl).isNull();
            assertThat(concert.ticketsKind).isEqualTo("voluntary");
            assertThat(concert.performers()).containsExactlyInAnyOrder(BandzoneHttpFixture.BAND_NAME, "Incertus Posterus");

            session.cancelGig(id);
            assertThat(concert.statusId).isEqualTo(3);
            session.reactivateGig(id);
            assertThat(concert.statusId).as("public again, in place").isEqualTo(2);
            assertThat(bandzone.concerts).hasSize(1);
            session.cancelGig(id);
            assertThat(session.deleteGig(id)).isTrue();
            assertThat(concert.deleted).isTrue();
            assertThat(session.deleteGig(id)).as("already gone").isFalse();
        }
    }

    @Test
    void the_sign_in_is_reused_until_bandzone_ends_it() throws Exception {
        try (BandzoneSession first = client.openSession(BandId.FIRST)) {
            first.bandName();
        }
        try (BandzoneSession second = client.openSession(BandId.FIRST)) {
            second.bandName();
        }
        assertThat(bandzone.logins).hasValue(1);
    }

    @Test
    void a_refused_sign_in_needs_the_user_and_is_not_retried() {
        assertThatThrownBy(() -> client(Map.of("password", "wrong")).openSession(BandId.FIRST))
                .isInstanceOfSatisfying(BandzonePortalException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER);
                    assertThat(e.getMessage()).doesNotContain("wrong");
                });
        assertThat(bandzone.calls).filteredOn(c -> c[1].equals("/api/auth/login")).hasSize(1);
    }

    /** A session kept before the redesign (Nette's cookies) is nothing to the new site: the band connects again. */
    @Test
    void a_session_bandzone_no_longer_knows_needs_the_band_to_connect_again() {
        assertThatThrownBy(() -> client(Map.of("session", "SID=old; permaLogin=older")).openSession(BandId.FIRST))
                .isInstanceOfSatisfying(BandzonePortalException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER);
                    assertThat(e.getMessage()).contains("connect Bandzone again");
                });
        assertThat(bandzone.logins).hasValue(0);
    }

    @Test
    void a_login_that_doesnt_manage_the_band_needs_the_user() throws Exception {
        bandzone.managed = List.of(4242L);
        try (BandzoneSession session = client.openSession(BandId.FIRST)) {
            assertThatThrownBy(session::bandName)
                    .isInstanceOfSatisfying(BandzonePortalException.class, e -> {
                        assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER);
                        assertThat(e.getMessage()).contains("doesn't manage the band '" + BandzoneHttpFixture.SLUG + "'");
                    });
        }
    }

    @Test
    void a_town_bandzone_knows_several_of_needs_the_user_to_pick_it() throws Exception {
        Gig typedOnly = new Gig(GigId.fresh(), "Somewhere", GigSchedule.startingAt(at(2026, 11, 20, 20, 0)),
                new Location(null, "Hranice", Country.CZECHIA), List.of(), Admission.free(), null, null, null, null, false);
        try (BandzoneSession session = client.openSession(BandId.FIRST)) {
            assertThatThrownBy(() -> session.createGig(typedOnly))
                    .isInstanceOfSatisfying(BandzonePortalException.class, e -> {
                        assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER);
                        assertThat(e.getMessage()).contains("several towns named Hranice");
                    });
        }
        assertThat(bandzone.concerts).isEmpty();
    }

    /** The redesigned form searches Czech towns only — a Slovak town is the user's to handle, not a retry. */
    @Test
    void a_slovak_town_needs_the_user_since_bandzone_takes_czech_towns_only() throws Exception {
        Gig slovak = new Gig(GigId.fresh(), "Košice show", GigSchedule.startingAt(at(2026, 11, 20, 20, 0)),
                new Location("Collosseum", "Košice", Country.SLOVAKIA), List.of(), Admission.free(), null, null, null,
                null, false);
        try (BandzoneSession session = client.openSession(BandId.FIRST)) {
            assertThatThrownBy(() -> session.createGig(slovak))
                    .isInstanceOfSatisfying(BandzonePortalException.class, e -> {
                        assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER);
                        assertThat(e.getMessage()).contains("only Czech towns");
                    });
        }
        assertThat(bandzone.concerts).isEmpty();
    }

    /** Bandzone wants a club or a typed place since the redesign; a gig without one says "-", which is none. */
    @Test
    void a_gig_without_a_venue_is_placed_as_none_and_free_entry_stays_free() throws Exception {
        Gig gig = new Gig(GigId.fresh(), "Club night", GigSchedule.startingAt(at(2026, 12, 1, 21, 0)),
                new Location(null, "Praha", Country.CZECHIA), List.of(), Admission.free(), null, null, null, null, false);
        try (BandzoneSession session = client.openSession(BandId.FIRST)) {
            String id = session.createGig(gig);
            session.updateGig(id, gig);
            BandzoneHttpFixture.Concert concert = bandzone.concerts.get(Long.parseLong(id));
            assertThat(concert.venueText).isEqualTo("-");
            assertThat(concert.venueId).isNull();
            assertThat(concert.ticketsKind).isEqualTo("free");
            assertThat(concert.title).isEqualTo("Club night");
        }
    }

    /** What Bandzone won't bring back is refused, so the workflow removes it and creates it again instead. */
    @Test
    void a_concert_gone_or_played_is_refused_reactivation_and_nothing_is_sent() throws Exception {
        Gig gig = new Gig(GigId.fresh(), "Played", GigSchedule.startingAt(at(2026, 12, 1, 21, 0)),
                new Location(null, "Praha", Country.CZECHIA), List.of(), Admission.free(), null, null, null, null, false);
        try (BandzoneSession session = client.openSession(BandId.FIRST)) {
            String id = session.createGig(gig);
            bandzone.concerts.get(Long.parseLong(id)).statusId = 7;
            assertThatThrownBy(() -> session.reactivateGig(id))
                    .isInstanceOfSatisfying(BandzonePortalException.class,
                            e -> assertThat(e.kind()).isEqualTo(FailureKind.REFUSED));
            assertThatThrownBy(() -> session.reactivateGig("123"))
                    .isInstanceOfSatisfying(BandzonePortalException.class,
                            e -> assertThat(e.kind()).isEqualTo(FailureKind.REFUSED));
        }
        assertThat(bandzone.calls).noneMatch(c -> c[1].endsWith("/status"));
    }

    @Test
    void a_concert_gone_from_bandzone_is_deleted_already_and_cant_be_cancelled() throws Exception {
        try (BandzoneSession session = client.openSession(BandId.FIRST)) {
            assertThat(session.deleteGig("123")).isFalse();
            assertThatThrownBy(() -> session.cancelGig("123"))
                    .isInstanceOfSatisfying(BandzonePortalException.class,
                            e -> assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER));
        }
        assertThat(bandzone.calls).as("nothing deleted blind").noneMatch(c -> c[0].equals("DELETE"));
    }

    private HttpBandzonePortalClient client(Map<String, String> secrets) {
        return new HttpBandzonePortalClient(bandzone.baseUrl(), bandzone.baseUrl(),
                new AccountsFakes.Accounts().with(BandId.FIRST, BandzonePlatform.PLATFORM,
                        Map.of("login", BandzoneHttpFixture.LOGIN, "bandSlug", BandzoneHttpFixture.SLUG), secrets),
                new PosterDownloader(Duration.ofSeconds(5), Duration.ofSeconds(5), false));
    }

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, PRAGUE);
    }
}
