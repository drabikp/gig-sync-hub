package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * The artist portal one session works in: the browser, the artist (known once logged in, see
 * {@link #forArtist}), the reply log, and the human-paced ways the pages ({@link BitLoginPage},
 * {@link BitEventsPage}, {@link BitBulkUpload}, {@link BitEventForm}, {@link BitDeleteDialog})
 * touch it — every click and key goes through the {@link HumanPacer}.
 */
final class BitPortal {

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final HumanPacer pacer;
    private final PortalReplies replies;
    private final String baseUrl;
    private final String artistId;

    /** The portal before logging in: no artist yet. */
    BitPortal(WebDriver driver, WebDriverWait wait, HumanPacer pacer, String baseUrl) {
        this(driver, wait, pacer, new PortalReplies(driver), baseUrl, null);
    }

    private BitPortal(WebDriver driver, WebDriverWait wait, HumanPacer pacer, PortalReplies replies, String baseUrl,
                      String artistId) {
        this.driver = driver;
        this.wait = wait;
        this.pacer = pacer;
        this.replies = replies;
        this.baseUrl = baseUrl;
        this.artistId = artistId;
    }

    /** The same portal, working on this artist's pages. */
    BitPortal forArtist(String artistId) {
        return new BitPortal(driver, wait, pacer, replies, baseUrl, artistId);
    }

    /** A page of the portal itself, e.g. {@code /login}. */
    void openPortal(String path) {
        driver.get(baseUrl + path);
    }

    /** A page of the artist, e.g. {@code events/upcoming}. */
    void open(String artistPage) {
        if (artistId == null) {
            throw new IllegalStateException("No Bandsintown artist yet — log in first");
        }
        driver.get(baseUrl + "/artists/" + artistId + "/" + artistPage);
    }

    String currentUrl() {
        return driver.getCurrentUrl();
    }

    PortalReplies replies() {
        return replies;
    }

    /** Waits as long as the session waits for a page. */
    <T> T until(Function<? super WebDriver, T> condition) {
        return wait.until(condition);
    }

    <T> T until(Duration timeout, Function<? super WebDriver, T> condition) {
        return new WebDriverWait(driver, timeout).until(condition);
    }

    WebElement find(By by) {
        return driver.findElement(by);
    }

    List<WebElement> findAll(By by) {
        return driver.findElements(by);
    }

    public void pause() {
        pacer.pause();
    }

    public void longPause() {
        pacer.longPause();
    }

    void click(WebElement element) {
        pacer.click(driver, element);
    }

    void type(WebElement field, String text) {
        pacer.type(driver, field, text);
    }

    void typeIntoFocused(String text) {
        pacer.typeIntoFocused(driver, text);
    }

    /** Replaces what the field holds with {@code text}, typed. */
    void clearAndType(WebElement field, String text) {
        pacer.clearAndType(driver, field, text);
    }

    void pressTab(WebElement field) {
        pacer.pressTab(field);
    }

    /** The visible button with this text — the last one, as dialogs render after the page. */
    WebElement visibleButton(String text) {
        return wait.until(d -> {
            List<WebElement> visible = d.findElements(By.xpath("//button[normalize-space()='" + text + "']"))
                    .stream().filter(WebElement::isDisplayed).toList();
            return visible.isEmpty() ? null : visible.getLast();
        });
    }

    WebElement firstVisible(By by) {
        return driver.findElements(by).stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
    }

    /** Closes promo pop-ups ("Boost your shows") that may cover the page. */
    void closePopups() {
        for (WebElement close : driver.findElements(By.cssSelector("button[aria-label='Close dialog']"))) {
            if (close.isDisplayed()) {
                click(close);
                pause();
            }
        }
    }

    Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }
}
