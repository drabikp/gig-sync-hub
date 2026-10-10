package sk.drabikp.bzscraper.browser;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformClientTest {

    @Test
    void a_platform_is_off_unless_its_client_is_chosen() {
        assertThat(PlatformClient.of(new MockEnvironment(), "bandzone")).isEqualTo(PlatformClient.OFF);
        assertThat(PlatformClient.of(new MockEnvironment().withProperty("bzscraper.bandzone.client", "http"), "bandzone"))
                .isEqualTo(PlatformClient.HTTP);
        assertThat(PlatformClient.of(new MockEnvironment().withProperty("bzscraper.bandzone.client", "Selenium"), "bandzone"))
                .isEqualTo(PlatformClient.SELENIUM);
    }

    @Test
    void the_older_switch_still_means_the_browser_until_a_client_is_chosen() {
        MockEnvironment old = new MockEnvironment().withProperty("bzscraper.bandzone.selenium.enabled", "true");
        assertThat(PlatformClient.of(old, "bandzone")).isEqualTo(PlatformClient.SELENIUM);
        assertThat(PlatformClient.of(old.withProperty("bzscraper.bandzone.client", "http"), "bandzone"))
                .isEqualTo(PlatformClient.HTTP);
        assertThat(PlatformClient.of(old, "bandsintown")).as("another platform's switch").isEqualTo(PlatformClient.OFF);
    }

    @Test
    void an_unknown_client_stops_the_start() {
        assertThatThrownBy(() -> PlatformClient.of(new MockEnvironment().withProperty("bzscraper.bandzone.client", "curl"),
                "bandzone")).hasMessageContaining("off, http or selenium");
    }
}
