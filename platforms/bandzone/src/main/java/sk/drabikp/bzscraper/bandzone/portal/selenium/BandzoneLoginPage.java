package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;

import java.util.List;

/**
 * Signing in to Bandzone. The browser's profile keeps a saved login, which shows the logout
 * link on any page; only when Bandzone ended it is the login form filled in with the account's
 * credentials. Then the cookie banner is dismissed.
 */
final class BandzoneLoginPage {

    private static final Logger logger = LoggerFactory.getLogger(BandzoneLoginPage.class);
    /** On every page while logged in. */
    static final By LOGOUT = By.cssSelector("a[href*='logout']");

    private final BandzoneBrowser browser;
    private final String userLogin;
    private final String userSecret;

    BandzoneLoginPage(BandzoneBrowser browser, String userLogin, String userSecret) {
        this.browser = browser;
        this.userLogin = userLogin;
        this.userSecret = userSecret;
    }

    /** Logs in unless the saved login is still valid. */
    void logIn() throws BandzonePortalException {
        if (loggedIn()) {
            logger.info("Bandzone: reusing the saved login");
        } else {
            submitLogin();
        }
        dismissCookies();
    }

    private boolean loggedIn() {
        browser.open("/");
        return browser.has(LOGOUT);
    }

    private void submitLogin() throws BandzonePortalException {
        browser.open("/login.html?do=login");
        browser.until(ExpectedConditions.presenceOfElementLocated(By.id("frmloginForm-login")));
        browser.setValue("#frmloginForm-login", userLogin);
        browser.setValue("#frmloginForm-password", userSecret);
        browser.find(By.id("frmloginForm-doLogin")).click();
        try {
            browser.until(ExpectedConditions.presenceOfElementLocated(LOGOUT));
        } catch (TimeoutException e) {
            throw BandzonePortalException.temporary("Bandzone login failed (check credentials).", e);
        }
    }

    private void dismissCookies() {
        List<WebElement> consent = browser.findAll(By.xpath(
                "//a[contains(@href,'approveCookies')] | //button[contains(.,'pořádku')]"));
        if (!consent.isEmpty()) {
            try {
                browser.click(consent.get(0));
            } catch (RuntimeException ignored) {
                // best-effort; the script field-setters do not require the banner gone
            }
        }
    }
}
