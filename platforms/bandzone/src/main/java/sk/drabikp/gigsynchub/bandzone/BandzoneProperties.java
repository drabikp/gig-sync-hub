package sk.drabikp.gigsynchub.bandzone;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code gigsynchub.bandzone.*}: where Bandzone is (whether this server drives it is
 * {@code gigsynchub.bandzone.client}). Each band connects its own account on the page; {@code login},
 * {@code password} and {@code band-slug} are band #1's from before that, kept once on start
 * ({@link BandzonePlatform}). Secrets never leave this record ({@link #toString} hides them).
 */
@ConfigurationProperties("gigsynchub.bandzone")
public record BandzoneProperties(String baseUrl, String login, String password, String bandSlug) {

    public BandzoneProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://bandzone.cz" : baseUrl;
        login = login == null ? "" : login.strip();
        password = password == null ? "" : password;
        bandSlug = bandSlug == null ? "" : bandSlug.strip();
    }

    @Override
    public String toString() {
        return "BandzoneProperties[baseUrl=" + baseUrl + ", bandSlug=" + bandSlug + ", login=" + (login.isBlank()
                ? "" : "***") + ", password=" + (password.isBlank() ? "" : "***") + "]";
    }
}
