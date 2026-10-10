package sk.drabikp.bzscraper.accounts.application;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.bzscraper.accounts.application.port.in.SecretBox;
import sk.drabikp.bzscraper.gig.application.UserFacingException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretBoxTest {

    private static final String OLD = "k1:" + key(1);
    private static final String NEW = "k2:" + key(2);

    private static String key(int fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) fill);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void a_secret_opens_only_where_it_was_sealed_for_and_isnt_readable_sealed() {
        SecretBox box = new AesGcmSecretBox(OLD);

        SecretBox.Sealed sealed = box.seal("hunter2", "band-a");

        assertThat(new String(sealed.data(), StandardCharsets.UTF_8)).doesNotContain("hunter2");
        assertThat(box.open(sealed, "band-a")).isEqualTo("hunter2");
        assertThatThrownBy(() -> box.open(sealed, "band-b")).isInstanceOf(IllegalStateException.class);
        assertThat(box.seal("hunter2", "band-a").data()).as("a new nonce every time").isNotEqualTo(sealed.data());
    }

    @Test
    void a_changed_ciphertext_doesnt_open() {
        SecretBox box = new AesGcmSecretBox(OLD);
        SecretBox.Sealed sealed = box.seal("hunter2", "band-a");
        byte[] changed = sealed.data().clone();
        changed[changed.length - 1] ^= 1;

        assertThatThrownBy(() -> box.open(new SecretBox.Sealed("k1", changed), "band-a"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_new_key_on_top_seals_from_now_on_and_the_old_one_still_opens() {
        SecretBox.Sealed before = new AesGcmSecretBox(OLD).seal("hunter2", "band-a");
        SecretBox rotated = new AesGcmSecretBox(NEW + "\n" + OLD);

        assertThat(rotated.open(before, "band-a")).isEqualTo("hunter2");
        assertThat(rotated.current(before)).isFalse();
        assertThat(rotated.seal("x", "band-a").keyId()).isEqualTo("k2");
        assertThatThrownBy(() -> new AesGcmSecretBox(NEW).open(before, "band-a"))
                .as("its key is gone").isInstanceOf(IllegalStateException.class);
    }

    @Test
    void without_a_key_nothing_is_sealed() {
        SecretBox none = new AesGcmSecretBox("");

        assertThat(none.ready()).isFalse();
        assertThatThrownBy(() -> none.seal("hunter2", "band-a")).isInstanceOfSatisfying(UserFacingException.class,
                e -> assertThat(e.code()).isEqualTo(AccountsErrorCode.SECRETS_UNAVAILABLE));
    }

    @Test
    void a_key_that_isnt_one_stops_the_start_without_showing_it() {
        assertThatThrownBy(() -> new AesGcmSecretBox("k1:" + Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new AesGcmSecretBox("no-colon")).hasMessageNotContaining("no-colon");
        assertThatThrownBy(() -> new AesGcmSecretBox(OLD + "," + OLD)).hasMessageContaining("twice");
        assertThat(new AesGcmSecretBox(OLD).toString()).doesNotContain(key(1));
    }
}
