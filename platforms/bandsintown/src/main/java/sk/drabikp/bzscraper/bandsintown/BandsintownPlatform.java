package sk.drabikp.bzscraper.bandsintown;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.gig.domain.platform.AccountField;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.PlatformTraits;

/**
 * Bandsintown as the core knows it: no cancelled state (cancelling removes the event), an
 * event is when the artist plays (the band's slot of a longer event), no entry information.
 * A band's account there: the artist, and the refresh token its own browser sign-in left — the portal's sign-in
 * happens only in a browser, so the band does that once itself and the server keeps nothing else.
 */
@Configuration
public class BandsintownPlatform {

    public static final Platform PLATFORM = Platform.of("BANDSINTOWN");

    /**
     * What the band runs in its own browser to hand the server its Bandsintown sign-in: the refresh token
     * the portal's own identity library left, read from the key that library documents
     * ({@code CognitoIdentityServiceProvider.<clientId>.<user>.refreshToken}, found through its
     * {@code LastAuthUser} key rather than guessed at). Nothing else is needed — the manager comes back
     * from the portal's sign-in and the artist from the list of what that manager looks after.
     */
    private static final String CONNECTION_SNIPPET =
            "copy((()=>{const k=Object.keys(localStorage).find(k=>"
                    + "/^CognitoIdentityServiceProvider\\.[^.]+\\.LastAuthUser$/.test(k));"
                    + "return localStorage.getItem(`CognitoIdentityServiceProvider."
                    + "${k.split('.')[1]}.${localStorage.getItem(k)}.refreshToken`)})())";

    @Bean
    PlatformTraits bandsintownTraits() {
        return PlatformTraits.of(PLATFORM, "Bandsintown")
                .keepsCancelledEvents(false)                       // cancelling removes the event
                .listsBandSlot(true)
                .carriesAdmission(false)
                .importPrecedence(20)
                .eventUrl("https://www.bandsintown.com/e/{id}")
                .accountField(AccountField.text(BandsintownAccount.ARTIST_NAME, true, "Artist name",
                        "As Bandsintown spells it — every uploaded event carries it"))
                .accountField(AccountField.secret(BandsintownAccount.CONNECTION, true, "Sign-in from your browser",
                                "Bandsintown's own sign-in, so Facebook and two-factor work as usual — we never "
                                        + "see your password, only a session you can revoke by signing out there.")
                        .readAt("https://artists.bandsintown.com/events", CONNECTION_SNIPPET))
                .accountField(AccountField.flag(BandsintownAccount.NOTIFY_FOLLOWERS, "Notify followers",
                        "Off: new events are published without telling the followers"))
                .build();
    }

    // Band #1's configured login is NOT adopted any more: what a session needs cannot be made from a
    // password on this server (see HttpBitSignIn), so every band connects Bandsintown from its own browser.
}
