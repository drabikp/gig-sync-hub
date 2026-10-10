package sk.drabikp.bzscraper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sk.drabikp.bzscraper.bandsintown.BandsintownPlatform;
import sk.drabikp.bzscraper.bandzone.BandzonePlatform;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.sync.application.port.out.SyncStep;
import sk.drabikp.bzscraper.sync.domain.StepType;

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
        "bzscraper.bandzone.selenium.enabled=true",
        "bzscraper.bandsintown.client=http",
})
class DrivingThePlatformsTest {

    @Autowired
    private List<SyncStep> steps;

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

    private List<StepType> onPlatform(Platform platform) {
        return steps.stream().filter(step -> platform.equals(step.platform())).map(SyncStep::type).toList();
    }
}
