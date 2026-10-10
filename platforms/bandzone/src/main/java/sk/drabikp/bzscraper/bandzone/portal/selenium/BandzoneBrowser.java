package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * The logged-in Bandzone band admin one session works in, and the ways its pages
 * ({@link BandzoneLoginPage}, {@link BandzoneCreateWizard}, {@link BandzoneUpdateForm},
 * {@link BandzoneDeleteTab}, {@link BandzoneLineupPage}) touch it. Bandzone's forms are plain
 * server-rendered forms with jQuery widgets: values are set by script (with input/change
 * events), buttons clicked by script — no human pacing is needed there. A field or button the
 * page doesn't have is an error, never a silent no-op.
 */
final class BandzoneBrowser {

    private static final Duration RELOAD_WAIT = Duration.ofSeconds(5);
    /** Sets a field found by a CSS selector and fires input/change; false when there is no such field. */
    private static final String SET_VALUE = "var e=document.querySelector(arguments[0]); if(!e){return false;}"
            + "e.value=arguments[1];"
            + "e.dispatchEvent(new Event('input',{bubbles:true}));"
            + "e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "return true;";
    /** Clicks an element found by a CSS selector; false when there is no such element. */
    private static final String CLICK = "var e=document.querySelector(arguments[0]); if(!e){return false;}"
            + "e.click(); return true;";

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String baseUrl;
    private final String bandSlug;

    BandzoneBrowser(WebDriver driver, WebDriverWait wait, String baseUrl, String bandSlug) {
        this.driver = driver;
        this.wait = wait;
        this.baseUrl = baseUrl;
        this.bandSlug = bandSlug;
    }

    /** The band whose concerts the session manages, as in its address ({@code /<slug>}). */
    String bandSlug() {
        return bandSlug;
    }

    /** A Bandzone page, e.g. {@code /koncert/563380/update}. */
    void open(String path) {
        driver.get(baseUrl + path);
    }

    String currentUrl() {
        return driver.getCurrentUrl();
    }

    WebElement find(By by) {
        return driver.findElement(by);
    }

    List<WebElement> findAll(By by) {
        return driver.findElements(by);
    }

    boolean has(By by) {
        return !driver.findElements(by).isEmpty();
    }

    /** Waits as long as the session waits for a page. */
    <T> T until(Function<? super WebDriver, T> condition) {
        return wait.until(condition);
    }

    <T> T until(Duration timeout, Function<? super WebDriver, T> condition) {
        return new WebDriverWait(driver, timeout).until(condition);
    }

    /** Sets a field's value and fires input/change, as typing would. */
    void setValue(String cssSelector, String value) {
        if (!Boolean.TRUE.equals(js(SET_VALUE, cssSelector, value))) {
            throw new NoSuchElementException("Bandzone field " + cssSelector);
        }
    }

    void clickByName(String name) {
        String cssSelector = "[name='" + name + "']";
        if (!Boolean.TRUE.equals(js(CLICK, cssSelector))) {
            throw new NoSuchElementException("Bandzone field " + cssSelector);
        }
    }

    void click(WebElement element) {
        js("arguments[0].click();", element);
    }

    /**
     * Picking from a city/venue autocomplete (or an uploaded poster) re-renders the update form
     * via AJAX about a second later, re-posting its current values; anything typed before that
     * lands is lost. Waits for the old form to go (the create wizard does not reload, so a
     * timeout just means there was nothing to wait for).
     */
    void awaitReload(WebElement oldFormElement) {
        try {
            until(RELOAD_WAIT, ExpectedConditions.stalenessOf(oldFormElement));
        } catch (TimeoutException e) {
            // no reload
        }
    }

    Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    Object jsAsync(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeAsyncScript(script, args);
    }
}
