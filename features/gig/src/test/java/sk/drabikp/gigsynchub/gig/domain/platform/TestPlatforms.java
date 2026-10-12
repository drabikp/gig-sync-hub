package sk.drabikp.gigsynchub.gig.domain.platform;

import java.util.List;

/**
 * Two platforms for the core's tests — the core itself names none. Their traits are those of
 * the real two: one that keeps cancelled events, shows the whole event and the entry; one that
 * removes cancelled events, lists the band's slot and has no entry info.
 */
public final class TestPlatforms {

    public static final Platform BANDZONE = Platform.of("BANDZONE");
    public static final Platform BANDSINTOWN = Platform.of("BANDSINTOWN");

    public static final PlatformTraits BANDZONE_TRAITS = PlatformTraits.of(BANDZONE, "Bandzone")
            .keepsCancelledEvents(true).listsBandSlot(false).carriesAdmission(true).importPrecedence(10)
            .eventUrl("https://bandzone.cz/koncert/{id}").build();
    public static final PlatformTraits BANDSINTOWN_TRAITS = PlatformTraits.of(BANDSINTOWN, "Bandsintown")
            .keepsCancelledEvents(false).listsBandSlot(true).carriesAdmission(false).importPrecedence(20)
            .eventUrl("https://www.bandsintown.com/e/{id}").build();

    public static final Platforms PLATFORMS = new Platforms(List.of(BANDZONE_TRAITS, BANDSINTOWN_TRAITS));

    private TestPlatforms() {
    }
}
