package sk.drabikp.gigsynchub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sk.drabikp.gigsynchub.bandsintown.BandsintownPlatform;
import sk.drabikp.gigsynchub.bandzone.BandzonePlatform;
import sk.drabikp.gigsynchub.gig.domain.platform.AccountField;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.gig.domain.platform.PlatformTraits;
import sk.drabikp.gigsynchub.sync.application.port.out.SyncStep;
import sk.drabikp.gigsynchub.sync.domain.StepType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The app starts with both platforms switched ON — which every other context test leaves off, so
 * nothing else builds the clients a running server builds. A client bean that the wiring cannot make
 * (an ambiguous constructor, a missing dependency) then shows up here rather than on the server, where
 * it is a start-up crash and the whole app is down, not just that platform.
 *
 * <p>Switched on with the older {@code selenium.enabled} spelling on purpose: a deployment that still
 * carries it must keep driving its platforms (there is no browser left for it to mean).
 */
@SpringBootTest(properties = {
        "gigsynchub.bandzone.selenium.enabled=true",
        "gigsynchub.bandsintown.client=http",
})
class DrivingThePlatformsTest {

    @Autowired
    private List<SyncStep> steps;

    @Autowired
    private List<PlatformTraits> traits;

    @Test
    void every_step_of_both_platforms_is_wired_and_can_reach_its_portal() {
        assertThat(steps).isNotEmpty();
        assertThat(onPlatform(BandzonePlatform.PLATFORM))
                .as("Bandzone's steps, each holding a portal client the wiring could build")
                .contains(StepType.FORM_CREATE, StepType.FORM_EDIT, StepType.CANCEL, StepType.REMOVE);
        assertThat(onPlatform(BandsintownPlatform.PLATFORM))
                .as("Bandsintown's")
                .contains(StepType.BULK_CREATE, StepType.BULK_EDIT, StepType.FORM_EDIT, StepType.REMOVE);
    }

    @Test
    void a_field_the_band_cannot_type_says_where_it_is_read_so_the_page_can_offer_it() {
        List<AccountField.ReadAt> readAt = traits.stream()
                .flatMap(t -> t.accountFields().stream())
                .map(AccountField::readAt)
                .filter(where -> where != null)
                .toList();

        assertThat(readAt).as("a platform whose sign-in only happens in the band's own browser").isNotEmpty();
        assertThat(readAt).allSatisfy(where -> {
            assertThat(where.site()).startsWith("https://");
            assertThat(where.script()).as("the line must hand the value over, not merely show it")
                    .containsAnyOf("navigator.clipboard", "execCommand('copy')");
            assertThat(where.script())
                    .as("the band clicks a bookmark, where the console's own helpers do not exist")
                    .doesNotContain("copy(")
                    .doesNotContain("console.");
            assertThat(where.script()).as("the page turns the line into a javascript: address")
                    .doesNotContain("%")
                    .doesNotContain("\n");
        });
    }

    private List<StepType> onPlatform(Platform platform) {
        return steps.stream().filter(step -> platform.equals(step.platform())).map(SyncStep::type).toList();
    }
}
