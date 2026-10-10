package sk.drabikp.bzscraper.bandsintown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.bzscraper.accounts.application.port.in.PlatformAccounts;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.AccountField;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.PlatformTraits;

import java.util.HashMap;
import java.util.Map;

/**
 * Bandsintown as the core knows it: no cancelled state (cancelling removes the event), an
 * event is when the artist plays (the band's slot of a longer event), no entry information.
 * A band's account there: the artist, and the line its own browser sign-in printed — the portal's sign-in
 * happens only in a browser, so the band does that once itself and the server keeps the session.
 */
@Configuration
public class BandsintownPlatform {

    public static final Platform PLATFORM = Platform.of("BANDSINTOWN");
    private static final Logger log = LoggerFactory.getLogger(BandsintownPlatform.class);

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
                        "Sign in at artists.bandsintown.com, open its Events page, then paste what this prints in "
                                + "the browser's console: (()=>{const m=performance.getEntriesByType('resource')"
                                + ".map(r=>/managers\\/(\\d+)\\/managed-actors\\/(\\d+)\\//.exec(r.name))"
                                + ".find(x=>x);return JSON.stringify({refreshToken:Object.entries(localStorage)"
                                + ".find(([k])=>/refreshToken$/.test(k))[1],managerId:m[1],actorId:m[2]})})()"))
                .accountField(AccountField.flag(BandsintownAccount.NOTIFY_FOLLOWERS, "Notify followers",
                        "Off: new events are published without telling the followers"))
                .build();
    }

    // Band #1's configured login is NOT adopted any more: what a session needs cannot be made from a
    // password on this server (see HttpBitSignIn), so every band connects Bandsintown from its own browser.
}
