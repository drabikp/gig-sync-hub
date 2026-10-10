package sk.drabikp.bzscraper.accounts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * {@code bzscraper.accounts.*}: the first band — the one the app ran for before it knew bands — and the
 * keys the bands' secrets are kept with.
 *
 * @param firstOwnerEmail who becomes its owner at their first sign-in; empty = whoever signs in first
 * @param firstBandSlug   its address and name, set once while it still has the ones migration V2
 * @param firstBandName   gave it ("first", "My band")
 * @param dataKeys        the keys sealing the bands' secrets, {@code id:base64} (32 bytes), the first
 *                        active — a secret (the Docker secret {@code data_keys}); {@link #toString} hides them
 * @param maxBandsOwned   how many bands one user may own (10)
 */
@ConfigurationProperties("bzscraper.accounts")
public record AccountsProperties(String firstOwnerEmail, String firstBandSlug, String firstBandName,
                                 String dataKeys, Integer maxBandsOwned) {

    public AccountsProperties {
        firstOwnerEmail = firstOwnerEmail == null ? "" : firstOwnerEmail.strip();
        firstBandSlug = firstBandSlug == null ? "" : firstBandSlug.strip().toLowerCase(Locale.ROOT);
        firstBandName = firstBandName == null ? "" : firstBandName.strip();
        dataKeys = dataKeys == null ? "" : dataKeys.strip();
        maxBandsOwned = maxBandsOwned == null || maxBandsOwned < 1 ? 10 : maxBandsOwned;
    }

    @Override
    public String toString() {
        return "AccountsProperties[firstOwnerEmail=" + firstOwnerEmail + ", firstBandSlug=" + firstBandSlug
                + ", firstBandName=" + firstBandName + ", dataKeys=" + (dataKeys.isEmpty() ? "" : "***")
                + ", maxBandsOwned=" + maxBandsOwned + "]";
    }
}
