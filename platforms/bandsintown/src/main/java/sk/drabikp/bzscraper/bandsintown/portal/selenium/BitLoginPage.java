package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.bandsintown.BandsintownAccount;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signing in to the artist portal, human-paced. The browser's profile keeps a saved login: a
 * still-valid one redirects from the login page to the artist (or the artist chooser); only
 * when Bandsintown ended it are the e-mail and password typed in, then the authenticator code
 * (from the account's authenticator secret) when asked for, then the artist picked when the account manages
 * several. A login or authenticator secret that doesn't work needs the user — trying again
 * would only type them in again.
 */
final class BitLoginPage {

    private static final Logger logger = LoggerFactory.getLogger(BitLoginPage.class);
    /** How long the portal may take to accept the code or open the artist after logging in. */
    private static final Duration LOGIN_WAIT = Duration.ofSeconds(30);
    private static final Pattern ARTIST_URL = Pattern.compile("/artists/(\\d+)(/|$)");
    /**
     * Marks the code boxes that are really on screen: the page keeps several code panels
     * side by side and slides the right one into view, so a box counts only if it is the
     * topmost element at its own centre.
     */
    private static final String MARK_VISIBLE_CODE_FORM = """
            document.querySelectorAll('[data-bz-otp]').forEach(e => e.removeAttribute('data-bz-otp'));
            for (const input of document.querySelectorAll('input[autocomplete="one-time-code"]')) {
              const r = input.getBoundingClientRect();
              if (r.width === 0) continue;
              const top = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
              if (top !== input && !input.contains(top)) continue;
              const form = input.closest('form');
              input.setAttribute('data-bz-otp', 'code');
              const go = form && [...form.querySelectorAll('button')]
                  .find(b => /continue/i.test(b.innerText) || b.type === 'submit');
              if (go) go.setAttribute('data-bz-otp', 'continue');
              return true;
            }
            return false;""";

    private final BitPortal portal;
    private final String userLogin;
    private final String userSecret;
    private final String authenticatorKey;
    private final String configuredArtistId;
    private final String artistName;
    private final Clock clock;

    BitLoginPage(BitPortal portal, BandsintownAccount account, Clock clock) {
        this.portal = portal;
        this.userLogin = account.login();
        this.userSecret = account.password();
        this.authenticatorKey = account.totpSecret();
        this.configuredArtistId = account.artistId();
        this.artistName = account.artistName();
        this.clock = clock;
    }

    /** Logs in unless the profile still holds a valid login; returns the artist id. */
    String logIn() throws BitPortalException {
        portal.openPortal("/login");
        portal.pause();                              // a saved login redirects away after a moment
        portal.until(d -> loggedIn() || onArtistChooser() || !portal.findAll(By.id("loginEmail")).isEmpty());
        if (!loggedIn() && !onArtistChooser()) {
            logger.info("Bandsintown: logging in");
            portal.pause();
            acceptCookies();
            portal.type(portal.find(By.id("loginEmail")), userLogin);
            portal.pause();
            portal.type(portal.find(By.id("loginPassword")), userSecret);
            portal.pause();
            portal.click(portal.find(By.xpath("//input[@id='loginPassword']/ancestor::form//button[@type='submit']")));
            try {
                portal.until(d -> loggedIn() || codeRequested());
            } catch (TimeoutException e) {
                throw BitPortalException.needsUser("Bandsintown login failed (check login and password).", e);
            }
            if (!loggedIn()) {
                enterAuthenticatorCode();
            }
        } else {
            logger.info("Bandsintown: reusing the saved login");
            if (!loggedIn()) {
                chooseArtist();
            }
        }
        return artistId(portal.currentUrl());
    }

    private void enterAuthenticatorCode() throws BitPortalException {
        if (authenticatorKey.isBlank()) {
            throw BitPortalException.needsUser("Bandsintown asks for an authenticator code, but "
                    + "the band's account has no authenticator secret (Settings).");
        }
        Totp totp = new Totp(authenticatorKey);
        long now = clock.millis() / 1000;
        if (Totp.secondsLeft(now) < 6) {           // don't type a code that expires mid-way
            HumanPacer.sleep((Totp.secondsLeft(now) + 1) * 1000);
        }
        portal.pause();
        portal.js(MARK_VISIBLE_CODE_FORM);
        portal.click(portal.find(By.cssSelector("[data-bz-otp='code']")));
        portal.typeIntoFocused(totp.codeAt(clock.millis() / 1000));
        portal.pause();
        if (!loggedIn()) {                           // the page may submit the code by itself
            try {
                portal.js(MARK_VISIBLE_CODE_FORM);
                List<WebElement> go = portal.findAll(By.cssSelector("[data-bz-otp='continue']"));
                if (!go.isEmpty() && go.getFirst().isEnabled()) {
                    portal.click(go.getFirst());
                }
            } catch (StaleElementReferenceException e) {
                // the code was already submitted and the page moved on
            }
        }
        try {
            portal.until(LOGIN_WAIT, d -> loggedIn() || onArtistChooser());
        } catch (TimeoutException e) {
            throw BitPortalException.needsUser("Bandsintown did not accept the authenticator code "
                    + "(check the account's authenticator secret and the system clock); stuck at "
                    + portal.currentUrl(), e);
        }
        if (!loggedIn()) {
            chooseArtist();
        }
    }

    /** After a fresh login the portal may show its artist chooser (or a loader) first. */
    private void chooseArtist() throws BitPortalException {
        try {
            portal.until(LOGIN_WAIT, d -> loggedIn() || !artistLinks().isEmpty());
        } catch (TimeoutException e) {
            throw BitPortalException.temporary("Logged in to Bandsintown, but no artist page appeared (at "
                    + portal.currentUrl() + ").");
        }
        if (loggedIn()) {
            return;
        }
        List<WebElement> links = artistLinks();
        WebElement pick = links.stream()
                .filter(a -> configuredArtistId.isBlank()
                        ? a.getText().contains(artistName)
                        : a.getDomAttribute("href").contains("/artists/" + configuredArtistId + "/"))
                .findFirst()
                .orElse(links.size() == 1 ? links.getFirst() : null);
        if (pick == null) {
            throw BitPortalException.needsUser("This Bandsintown account manages several artists — set "
                    + "the account's artist id (or artist name) to pick one.");
        }
        portal.pause();
        portal.click(pick);
        try {
            portal.until(LOGIN_WAIT, d -> loggedIn());
        } catch (TimeoutException e) {
            throw BitPortalException.temporary("Could not open the artist page on Bandsintown.");
        }
    }

    private List<WebElement> artistLinks() {
        return portal.findAll(By.cssSelector("a[href*='/artists/']")).stream()
                .filter(a -> ARTIST_URL.matcher(String.valueOf(a.getDomAttribute("href"))).find())
                .filter(WebElement::isDisplayed)
                .toList();
    }

    private boolean codeRequested() {
        return Boolean.TRUE.equals(portal.js(MARK_VISIBLE_CODE_FORM));
    }

    private boolean onArtistChooser() {
        return portal.currentUrl().contains("/managed-actors");
    }

    private boolean loggedIn() {
        return ARTIST_URL.matcher(portal.currentUrl()).find();
    }

    private String artistId(String url) throws BitPortalException {
        if (!configuredArtistId.isBlank()) {
            return configuredArtistId;
        }
        Matcher m = ARTIST_URL.matcher(url);
        if (!m.find()) {
            throw BitPortalException.needsUser("Logged in to Bandsintown but could not tell the artist id — "
                    + "set the account's artist id.");
        }
        return m.group(1);
    }

    private void acceptCookies() {
        for (WebElement button : portal.findAll(By.xpath("//button[normalize-space()='I agree']"))) {
            if (button.isDisplayed()) {
                portal.click(button);
                portal.pause();
                return;
            }
        }
    }
}
