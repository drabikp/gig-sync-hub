package sk.drabikp.bzscraper.bandsintown.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sk.drabikp.bzscraper.bandsintown.BitPortalException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The artist's Events pages: the Upcoming and Past lists (as the portal fetched them), the
 * row "⋯" menu of an upcoming event, and the Bulk Upload button. Published past events are
 * listed under Past only, and the past list comes 20 at a time ({@code x-next-page}), more
 * loading as the page is scrolled.
 */
final class BitEventsPage {

    private static final Logger logger = LoggerFactory.getLogger(BitEventsPage.class);
    private static final int MAX_PAST_PAGES = 50;

    /** Marks the "⋯" button of the index-th listed event after checking its city and day. */
    static final String MARK_ROW = """
            const [index, city, day, expected] = arguments;
            const iconOnly = b => !b.innerText.trim() && b.querySelector('svg') && b.getBoundingClientRect().width > 0;
            const rows = [];
            for (const b of document.querySelectorAll('button')) {
              if (!iconOnly(b)) continue;
              let r = b.parentElement;
              while (r && !/RSVP/.test(r.innerText || '')) r = r.parentElement;
              // an event row holds exactly one icon-only button: its "⋯" menu
              if (!r || r.innerText.length >= 500) continue;
              if ([...r.querySelectorAll('button')].filter(iconOnly).length !== 1) continue;
              if (!rows.some(x => x.r === r)) rows.push({r, b});
            }
            if (rows.length !== expected) return 'expected ' + expected + ' event rows, found ' + rows.length;
            const text = rows[index].r.innerText;
            if (!text.includes(city)) return 'row ' + index + ' is not in ' + city + ': ' + text.slice(0, 80);
            if (!new RegExp('(^|\\\\D)0?' + day + '(\\\\D|$)').test(text)) return 'row ' + index + ' is not on day ' + day;
            document.querySelectorAll('[data-bz-kebab]').forEach(e => e.removeAttribute('data-bz-kebab'));
            rows[index].b.setAttribute('data-bz-kebab', '1');
            return 'ok';""";

    private static final String SCROLL_TO_END = """
            window.scrollTo(0, document.body.scrollHeight);
            for (const el of document.querySelectorAll('*')) {
              if (el.scrollHeight > el.clientHeight + 50 && /(auto|scroll)/.test(getComputedStyle(el).overflowY)) {
                el.scrollTop = el.scrollHeight;
              }
            }""";

    private final BitPortal portal;

    public BitEventsPage(BitPortal portal) {
        this.portal = portal;
    }

    /** The upcoming (published) events, in the order the page lists them. */
    public List<BitEvent> upcoming() throws BitPortalException {
        open("upcoming");
        PortalReply reply = portal.replies().await(0, r -> !r.write() && r.urlContains("past=false"), "the event list");
        if (reply.status() != 200) {
            throw BitPortalException.temporary("Could not read the Bandsintown event list — " + reply.describe());
        }
        return reply.events();
    }

    /** Every past event: the first page comes with the tab, the rest load as the list is scrolled. */
    public List<BitEvent> past() throws BitPortalException {
        open("past");
        Predicate<PortalReply> pastList = r -> !r.write() && r.urlContains("past=true");
        PortalReply reply = portal.replies().await(0, pastList, "the past events");
        List<BitEvent> events = new ArrayList<>(reply.events());
        for (int page = 1; reply.hasNextPage() && page < MAX_PAST_PAGES; page++) {
            int mark = portal.replies().count();
            portal.pause();
            loadMore();
            try {
                reply = portal.replies().await(mark, pastList, "more past events", Duration.ofSeconds(30));
            } catch (BitPortalException e) {
                logger.warn("Bandsintown: stopped after {} past events — the list did not load more", events.size());
                break;
            }
            events.addAll(reply.events());
        }
        return events;
    }

    /** The event among both lists, upcoming first; empty when neither lists it. */
    public Optional<BitEvent> find(String eventId) throws BitPortalException {
        Optional<BitEvent> upcoming = upcoming().stream().filter(e -> eventId.equals(e.id())).findFirst();
        if (upcoming.isPresent()) {
            return upcoming;
        }
        portal.pause();
        return past().stream().filter(e -> eventId.equals(e.id())).findFirst();
    }

    /**
     * Opens the delete dialog of {@code event} from its row's "⋯" menu: the row is found by its
     * index in the {@code listed} upcoming events and checked (city, day) before anything is
     * clicked.
     */
    public BitDeleteDialog delete(BitEvent event, List<BitEvent> listed) throws BitPortalException {
        int index = listed.indexOf(event);
        String[] check = new String[1];
        try {
            portal.until(d -> "ok".equals(check[0] = String.valueOf(
                    portal.js(MARK_ROW, index, event.venueCity(), event.day(), listed.size()))));
        } catch (TimeoutException e) {
            throw BitPortalException.temporary("Could not find event " + event.id() + " in the Bandsintown list ("
                    + check[0] + ") — nothing was deleted.");
        }
        portal.pause();
        portal.click(portal.find(By.cssSelector("[data-bz-kebab]")));
        portal.click(portal.until(d -> portal.firstVisible(By.xpath("//*[normalize-space(text())='Delete']"))));
        return new BitDeleteDialog(portal);
    }

    /** Opens Bulk Upload (on the Upcoming tab). */
    public BitBulkUpload bulkUpload() {
        open("upcoming");
        portal.click(portal.visibleButton("Bulk Upload"));
        return new BitBulkUpload(portal);
    }

    private void open(String tab) {
        portal.open("events/" + tab);
        portal.until(d -> portal.firstVisible(By.xpath("//a[normalize-space()='Upcoming Events']")));
        portal.replies().install();
        portal.pause();
        portal.closePopups();
    }

    /** Scrolls to the end of the list (and presses "load more" if there is such a button). */
    private void loadMore() {
        portal.js(SCROLL_TO_END);
        for (WebElement button : portal.findAll(By.xpath(
                "//button[contains(translate(normalize-space(),'LOADMORESHW','loadmoreshw'),'more')]"))) {
            if (button.isDisplayed()) {
                portal.click(button);
                return;
            }
        }
    }
}
