package sk.drabikp.gigsynchub.gig.application;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicAddressesTest {

    private static boolean isPublic(String ip) throws Exception {
        return PublicAddresses.isPublic(InetAddress.getByName(ip));
    }

    @Test
    void the_server_itself_its_networks_and_metadata_addresses_are_never_fetched() throws Exception {
        for (String ip : new String[] {"127.0.0.1", "0.0.0.0", "10.201.0.5", "172.17.0.1", "192.168.1.10",
                "169.254.169.254", "100.64.0.1", "198.18.0.1", "224.0.0.1", "255.255.255.255", "::1", "fd00::1",
                "fe80::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1", "64:ff9b::a00:1", "2002:a00:1::1"}) {
            assertThat(isPublic(ip)).as(ip).isFalse();
        }
        assertThat(isPublic("1.1.1.1")).isTrue();
        assertThat(isPublic("2606:4700:4700::1111")).isTrue();
    }

    @Test
    void only_http_addresses_of_known_public_hosts_pass() {
        assertThatThrownBy(() -> PublicAddresses.checked(URI.create("http://localhost:8081/actuator")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicAddresses.checked(URI.create("http://127.0.0.1/x.ics")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicAddresses.checked(URI.create("file:///etc/passwd")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicAddresses.checked(URI.create("http://[::1]/x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PublicAddresses.fetchable("http://10.0.0.1/x.ics")).isFalse();
        assertThat(PublicAddresses.fetchable("not an address")).isFalse();
    }
}
