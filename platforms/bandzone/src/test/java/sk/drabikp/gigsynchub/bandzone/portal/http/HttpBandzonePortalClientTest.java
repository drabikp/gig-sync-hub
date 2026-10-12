package sk.drabikp.gigsynchub.bandzone.portal.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
 * The HTTP client against a stand-in that talks as Bandzone's pages do ({@link BandzoneHttpFixture}):
 * every operation of a session, the login and its reuse, and the cases that need the user.
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
        client = client(BandzoneHttpFixture.PASSWORD);
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
            BandzoneHttpFixture.Concert concert = bandzone.concerts.get(id);
            assertThat(concert.value("name")).isEqualTo("Release show");
            assertThat(concert.value("cityId")).isEqualTo("4109");

            session.updateGig(id, gig);
            assertThat(concert.poster).as("the poster uploaded").isNotNull();
            assertThat(concert.value("venueId")).as("the club linked").isEqualTo("125");
            assertThat(concert.value("end[date]")).isEqualTo("13.11.2026");
            assertThat(concert.value("info")).isEqualTo("first line\nsecond line");
            assertThat(concert.performers).extracting(p -> p[2])
                    .containsExactly(BandzoneHttpFixture.BAND_NAME, "Incertus Posterus", "Stubby");
            assertThat(concert.performers).extracting(p -> p[1]).as("another concert's profile-less band never linked")
                    .doesNotContain("999");

            bandzone.dialogsRedirect = true;        // the concert's address changed with its name and place
            session.updateGig(id, edited);
            assertThat(concert.value("cityId")).as("the Hranice in okres Přerov, of three").isEqualTo("1603");
            assertThat(concert.value("venueId")).as("a club Bandzone doesn't know: free text").isEmpty();
            assertThat(concert.value("venueId__container[textInput]")).isEqualTo("Garáž u Nováků");
            assertThat(concert.value("entryType")).isEqualTo("1");
            assertThat(concert.performers).extracting(p -> p[2])
                    .containsExactly(BandzoneHttpFixture.BAND_NAME, "Incertus Posterus");

            session.cancelGig(id);
            assertThat(concert.cancelled).isTrue();
            assertThat(session.deleteGig(id)).isTrue();
            assertThat(concert.deleted).isTrue();
            assertThat(session.deleteGig(id)).as("already gone").isFalse();
        }
        assertThat(bandzone.posts).as("one button per form sent")
                .noneMatch(p -> p.getValue().containsKey("cancelGig") && p.getValue().containsKey("delete"));
    }

    @Test
    void the_login_is_reused_until_bandzone_ends_it() throws Exception {
        try (BandzoneSession first = client.openSession(BandId.FIRST)) {
            first.bandName();
        }
        try (BandzoneSession second = client.openSession(BandId.FIRST)) {
            second.bandName();
        }
        assertThat(bandzone.logins).hasValue(1);
    }

    @Test
    void a_refused_login_needs_the_user_and_is_not_retried() {
        assertThatThrownBy(() -> client("wrong").openSession(BandId.FIRST))
                .isInstanceOfSatisfying(BandzonePortalException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER);
                    assertThat(e.getMessage()).doesNotContain("wrong");
                });
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

    @Test
    void a_concert_gone_from_bandzone_is_deleted_already_and_cant_be_cancelled() throws Exception {
        try (BandzoneSession session = client.openSession(BandId.FIRST)) {
            assertThat(session.deleteGig("123")).isFalse();
            assertThatThrownBy(() -> session.cancelGig("123"))
                    .isInstanceOfSatisfying(BandzonePortalException.class,
                            e -> assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER));
        }
    }

    /**
     * The wizard's middle is read from the page, not from its address: Bandzone sends us to its
     * "similar concerts" address even when it has nothing to ask and serves the info step there.
     * Taking the address at its word cost a publish (live 2026-10-11), so each shape gets a test.
     */
    @ParameterizedTest
    @EnumSource(BandzoneHttpFixture.AfterStepOne.class)
    void creates_however_the_wizard_leads_from_its_first_step(BandzoneHttpFixture.AfterStepOne shape) throws Exception {
        bandzone.afterStepOne = shape;
        Gig gig = new Gig(GigId.fresh(), "Club night", GigSchedule.startingAt(at(2026, 12, 1, 21, 0)),
                new Location(null, "Praha", Country.CZECHIA), List.of(), Admission.free(), null, null, null, null, false);
        try (BandzoneSession session = client.openSession(BandId.FIRST)) {
            String id = session.createGig(gig);
            assertThat(bandzone.concerts.get(id).value("entryType")).isEqualTo("2");
            assertThat(bandzone.concerts.get(id).value("name")).isEqualTo("Club night");
        }
    }

    private HttpBandzonePortalClient client(String password) {
        return new HttpBandzonePortalClient(bandzone.baseUrl(),
                new AccountsFakes.Accounts().with(BandId.FIRST, BandzonePlatform.PLATFORM,
                        Map.of("login", BandzoneHttpFixture.LOGIN, "bandSlug", BandzoneHttpFixture.SLUG),
                        Map.of("password", password)),
                new PosterDownloader(Duration.ofSeconds(5), Duration.ofSeconds(5), false));
    }

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, PRAGUE);
    }
}
