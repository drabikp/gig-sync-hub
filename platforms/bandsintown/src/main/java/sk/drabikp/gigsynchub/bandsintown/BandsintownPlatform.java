package sk.drabikp.gigsynchub.bandsintown;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.gigsynchub.gig.domain.platform.AccountField;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import sk.drabikp.gigsynchub.gig.domain.platform.PlatformTraits;

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
     *
     * <p>It uses nothing a plain page hasn't got, because that is where it runs: a bookmarklet is the
     * page's own code, with none of the browser console's conveniences — the console's {@code copy()} is
     * the console's alone and is not defined there. So it copies the way a page copies, and leaves the
     * token selected in a box as well: when the browser refuses a copy the band hasn't visibly asked for,
     * Ctrl+C still works.
     *
     * <p>No per cent sign, no newline: this line also becomes a {@code javascript:} address.
     */
    private static final String CONNECTION_SNIPPET =
            "(()=>{const k=Object.keys(localStorage).find(k=>"
                    + "/^CognitoIdentityServiceProvider\\.[^.]+\\.LastAuthUser$/.test(k));"
                    + "const token=k&&localStorage.getItem(`CognitoIdentityServiceProvider."
                    + "${k.split('.')[1]}.${localStorage.getItem(k)}.refreshToken`);"
                    + "if(!token){alert('Sign in to Bandsintown on this page first, then click this again.');return}"
                    + "const box=document.createElement('div');box.style.cssText='position:fixed;"
                    + "left:1em;right:1em;bottom:1em;z-index:2147483647;display:flex;flex-direction:column;"
                    + "gap:.5em;padding:.75em;border:2px solid #333;border-radius:8px;background:#fff;"
                    + "color:#000;font:14px sans-serif';"
                    + "const said=document.createElement('div');"
                    + "const field=document.createElement('textarea');field.readOnly=true;field.value=token;"
                    + "field.style.cssText='height:4em;font:11px monospace';"
                    + "const done=document.createElement('button');done.textContent='Done';"
                    + "done.style.cssText='align-self:flex-start;font:14px sans-serif';"
                    + "done.onclick=()=>box.remove();box.append(said,field,done);"
                    + "document.body.appendChild(box);field.focus();field.select();"
                    + "const yes='Copied — paste it into the app.';"
                    + "let copied=false;try{copied=document.execCommand('copy')}catch(e){}"
                    + "if(navigator.clipboard){navigator.clipboard.writeText(token)"
                    + ".then(()=>{said.textContent=yes},()=>{})}"
                    + "said.textContent=copied?yes"
                    + ":'Press Ctrl+C (Cmd+C on a Mac) to copy, then paste it into the app.';})()";

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
