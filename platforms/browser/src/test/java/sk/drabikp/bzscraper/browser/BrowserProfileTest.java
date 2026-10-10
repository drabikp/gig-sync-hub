package sk.drabikp.bzscraper.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserProfileTest {

    @Test
    void every_account_gets_its_own_profile_named_by_a_hash() {
        Path real = BrowserProfile.dir("", "bandzone", "band@example.com");
        Path test = BrowserProfile.dir("", "bandzone", "test@example.com");

        assertThat(real).isNotEqualTo(test);
        assertThat(BrowserProfile.dir(null, "bandzone", " Band@Example.com ")).isEqualTo(real);
        assertThat(real.toString()).doesNotContain("example").contains(".bzscraper", "bandzone-browser");
        assertThat(BrowserProfile.dir("/srv/profiles", "bandzone", "band@example.com"))
                .isEqualTo(Path.of("/srv/profiles").resolve(real.getFileName()));
    }

    @Test
    void the_profile_is_created_and_kept_private_to_the_owner(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("a/b");
        BrowserProfile.ensurePrivate(dir);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir))).isEqualTo("rwx------");

        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        BrowserProfile.ensurePrivate(dir);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir))).isEqualTo("rwx------");
    }

    @Test
    void saved_logins_go_to_the_keyring_unless_switched_off() {
        assertThat(BrowserProfile.secretStoreArgs("gnome-libsecret")).containsExactly("--password-store=gnome-libsecret");
        assertThat(BrowserProfile.secretStoreArgs("basic")).containsExactly("--password-store=basic");
        assertThat(BrowserProfile.secretStoreArgs("")).isEmpty();
    }

    @Test
    void auto_uses_the_keyring_on_a_desktop_and_chromiums_own_store_on_a_server() {
        assertThat(BrowserProfile.secretStoreArgs("auto", "unix:path=/run/user/1000/bus"))
                .containsExactly("--password-store=gnome-libsecret");
        assertThat(BrowserProfile.secretStoreArgs("auto", null)).containsExactly("--password-store=basic");
    }
}
