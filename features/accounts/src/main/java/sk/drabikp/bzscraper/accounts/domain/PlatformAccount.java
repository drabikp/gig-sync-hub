package sk.drabikp.bzscraper.accounts.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A band's account on a platform: its {@code settings} (not secret — a login, the band's name there)
 * and its {@code secrets} (a password, an authenticator secret — kept encrypted, never shown, never
 * logged: {@link #toString} names only which are set), by the names the platform's
 * {@link sk.drabikp.bzscraper.gig.domain.platform.AccountField}s give them; and what its last test said.
 */
@AggregateRoot
public record PlatformAccount(@Identity BandId band, @Identity Platform platform, Map<String, String> settings,
                              Map<String, String> secrets, AccountCheck lastCheck) {

    public PlatformAccount {
        Objects.requireNonNull(band, "the band");
        Objects.requireNonNull(platform, "the platform");
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    }

    /** A setting, blank when not set. */
    public String setting(String name) {
        return settings.getOrDefault(name, "");
    }

    /** A yes/no setting ({@code true}); no when not set. */
    public boolean flag(String name) {
        return Boolean.parseBoolean(setting(name));
    }

    /** A secret, blank when not set. Only for signing in — never shown or logged. */
    public String secret(String name) {
        return secrets.getOrDefault(name, "");
    }

    public Optional<AccountCheck> check() {
        return Optional.ofNullable(lastCheck);
    }

    public PlatformAccount checked(AccountCheck check) {
        return new PlatformAccount(band, platform, settings, secrets, check);
    }

    @Override
    public String toString() {
        return "PlatformAccount[band=" + band + ", platform=" + platform + ", settings=" + new TreeMap<>(settings)
                + ", secrets=" + new TreeMap<>(secrets).keySet() + ", lastCheck=" + lastCheck + "]";
    }
}
