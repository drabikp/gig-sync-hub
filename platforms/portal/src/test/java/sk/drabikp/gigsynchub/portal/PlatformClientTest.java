package sk.drabikp.gigsynchub.portal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformClientTest {

    @Test
    void a_platform_is_off_unless_its_client_is_chosen() {
        assertThat(PlatformClient.of(new MockEnvironment(), "bandzone")).isEqualTo(PlatformClient.OFF);
        assertThat(PlatformClient.of(new MockEnvironment().withProperty("gigsynchub.bandzone.client", "http"), "bandzone"))
                .isEqualTo(PlatformClient.HTTP);
        assertThat(PlatformClient.of(new MockEnvironment().withProperty("gigsynchub.bandzone.client", "HTTP"), "bandzone"))
                .as("however it is spelled").isEqualTo(PlatformClient.HTTP);
    }

    @Test
    void a_server_carrying_the_older_switch_keeps_driving_the_platform() {
        MockEnvironment old = new MockEnvironment().withProperty("gigsynchub.bandzone.selenium.enabled", "true");
        assertThat(PlatformClient.of(old, "bandzone")).isEqualTo(PlatformClient.HTTP);
        assertThat(PlatformClient.of(old.withProperty("gigsynchub.bandzone.client", "http"), "bandzone"))
                .isEqualTo(PlatformClient.HTTP);
        assertThat(PlatformClient.of(old, "bandsintown")).as("another platform's switch").isEqualTo(PlatformClient.OFF);
    }

    @Test
    void an_unknown_client_stops_the_start() {
        assertThatThrownBy(() -> PlatformClient.of(new MockEnvironment().withProperty("gigsynchub.bandzone.client", "curl"),
                "bandzone")).hasMessageContaining("off or http");
    }
}
