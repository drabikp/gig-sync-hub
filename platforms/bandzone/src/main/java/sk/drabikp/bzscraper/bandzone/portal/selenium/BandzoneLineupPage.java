package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The concert's "performing bands" tab ({@code updateTabs-at=updateBands}). Makes the
 * performer list equal the gig's lineup plus the publishing band itself:
 * <ul>
 *   <li>performers not in the lineup are removed (the band itself never is);</li>
 *   <li>a missing name is added as the Bandzone profile whose name matches exactly
 *       (ignoring case) — the first one if several do — otherwise as a new "band/performer
 *       without a profile" stub, which exists only on this concert.</li>
 * </ul>
 * Stubs are not searchable afterwards, so presence is always decided from the concert's
 * current performer list, never from the search.
 */
final class BandzoneLineupPage {

    private final BandzoneBrowser browser;
    private final String path;

    public BandzoneLineupPage(BandzoneBrowser browser, String bandzoneId) {
        this.browser = browser;
        this.path = "/koncert/" + bandzoneId + "/update?updateTabs-at=updateBands";
    }

    /** Makes the tab's performers the lineup plus the band itself, and checks what Bandzone stored. */
    public void sync(List<String> lineup, String ownBandName) throws BandzonePortalException {
        List<String> desired = desired(lineup, ownBandName);
        open();
        boolean ownListed = performers().values().stream().anyMatch(p -> sameName(p, ownBandName));
        removeOthers(desired, ownBandName);
        addMissing(desired);
        verify(desired, ownBandName, ownListed);
    }

    /** The lineup without the band itself, each name once. */
    private static List<String> desired(List<String> lineup, String ownBandName) {
        List<String> desired = new ArrayList<>();
        for (String name : lineup) {
            if (!sameName(name, ownBandName) && desired.stream().noneMatch(d -> sameName(d, name))) {
                desired.add(name.trim());
            }
        }
        return desired;
    }

    /** Removes every performer neither in the lineup nor the band itself, one confirm form each. */
    private void removeOthers(List<String> desired, String ownBandName) {
        for (Map.Entry<String, String> performer : performers().entrySet()) {
            String name = performer.getValue();
            if (!sameName(name, ownBandName) && desired.stream().noneMatch(d -> sameName(d, name))) {
                remove(performer.getKey());
                open();
            }
        }
    }

    /** Queues every lineup name the tab doesn't list yet and adds them in one go. */
    private void addMissing(List<String> desired) throws BandzonePortalException {
        List<String> present = new ArrayList<>(performers().values());
        List<String> toAdd = desired.stream()
                .filter(d -> present.stream().noneMatch(p -> sameName(p, d)))
                .toList();
        if (toAdd.isEmpty()) {
            return;
        }
        for (String name : toAdd) {
            queue(name);
        }
        WebElement add = browser.find(By.id("frmaddBandForm-add"));
        browser.click(add);
        browser.until(ExpectedConditions.stalenessOf(add));
        open();
    }

    /** The stored performers are the lineup plus the band itself (if it was listed before). */
    private void verify(List<String> desired, String ownBandName, boolean ownListed) throws BandzonePortalException {
        List<String> stored = new ArrayList<>(performers().values());
        List<String> missing = desired.stream()
                .filter(d -> stored.stream().noneMatch(s -> sameName(s, d)))
                .toList();
        List<String> extra = stored.stream()
                .filter(s -> !sameName(s, ownBandName) && desired.stream().noneMatch(d -> sameName(d, s)))
                .toList();
        if (ownListed && stored.stream().noneMatch(p -> sameName(p, ownBandName))) {
            throw BandzonePortalException.temporary("Bandzone lineup sync removed the band itself ("
                    + ownBandName + ") — add it back on Bandzone.");
        }
        if (!missing.isEmpty() || !extra.isEmpty()) {
            throw BandzonePortalException.temporary("Bandzone lineup did not sync (missing " + missing
                    + ", unexpected " + extra + ").");
        }
    }

    private void open() {
        browser.open(path);
        browser.until(ExpectedConditions.presenceOfElementLocated(By.id("frmaddBandForm-add")));
    }

    /** Current performers in page order: editable element id -> name. */
    @SuppressWarnings("unchecked")
    private Map<String, String> performers() {
        List<List<String>> rows = (List<List<String>>) js(
                "return Array.from(document.querySelectorAll('#gigEditLineupBands .editable'))"
                        + ".map(e=>[e.id,((e.querySelector('.profileLink h4')||{}).innerText||'').trim()]);");
        Map<String, String> byId = new LinkedHashMap<>();
        rows.forEach(r -> byId.put(r.get(0), r.get(1)));
        return byId;
    }

    /** Removes one performer through its "odstranit z koncertu" confirm form. */
    private void remove(String editableId) {
        String gigBandId = editableId.substring(editableId.lastIndexOf('-') + 1);
        js("document.querySelector('#'+arguments[0]+' a.delete').click();", editableId);
        // this performer's form only: em=delete and ei exactly its id (50 is not 501), whatever
        // else the address carries
        String findButton = "var id=arguments[0];"
                + "for (const f of document.querySelectorAll('form[action*=\"em=delete\"]')) {"
                + "  const q=new URL(f.getAttribute('action'), location.href).searchParams;"
                + "  const b=f.querySelector('[name=delete]');"
                + "  if (q.get('em')==='delete' && q.get('ei')===id && b) return b;"
                + "}"
                + "return null;";
        browser.until(d -> js(findButton, gigBandId) != null);
        // requestSubmit with the button's value, as for the concert delete tab
        js("var b=(function(){" + findButton + "}).apply(null, arguments);var f=b.form;"
                        + "var h=document.createElement('input');h.type='hidden';h.name='delete';h.value=b.value;"
                        + "f.appendChild(h);f.requestSubmit(b);",
                gigBandId);
        browser.until(ExpectedConditions.invisibilityOfElementLocated(By.id(editableId)));
    }

    /** Queues one band in the add form: its exact-name profile, or a new stub. */
    private void queue(String name) throws BandzonePortalException {
        long before = queuedCount();
        // drop stale suggestions so the wait below only sees this search's results
        js("document.querySelectorAll('ul.ui-autocomplete').forEach(u=>{u.innerHTML='';u.style.display='none';});"
                + "var t=document.querySelector('#frmaddBandForm-bandIds__addCompleter__container-textInput');"
                + "t.focus();t.value=arguments[0];t.dispatchEvent(new Event('input',{bubbles:true}));", name);
        // the "add new band/performer" item is always offered, so a non-empty list is certain
        browser.until(d -> (Boolean) js("return Array.from(document.querySelectorAll('ul.ui-autocomplete'))"
                + ".some(u=>u.offsetParent!==null&&u.querySelector('li'));"));
        String picked = (String) js(
                "var n=arguments[0].toLowerCase();"
                        + "var li=Array.from(document.querySelectorAll('ul.ui-autocomplete li')).filter(l=>l.offsetParent!==null);"
                        + "var m=li.find(l=>{var h=l.querySelector('h4.title');return h&&h.innerText.trim().toLowerCase()===n;});"
                        + "if(m){(m.querySelector('a')||m).click();return 'profile';}"
                        + "var c=li.find(l=>l.querySelector('a.create-link'));"
                        + "if(c){c.querySelector('a.create-link').click();return 'stub';}"
                        + "return null;",
                name);
        if ("stub".equals(picked)) {
            By stubName = By.name("bandIds__addCompleter__createItem[name]");
            browser.until(ExpectedConditions.visibilityOfElementLocated(stubName));
            browser.setValue("[name='bandIds__addCompleter__createItem[name]']", name);
            browser.clickByName("bandIds__addCompleter__createItem[create]");
        } else if (picked == null) {
            throw BandzonePortalException.temporary("Bandzone offered no way to add band '" + name + "'.");
        }
        browser.until(d -> queuedCount() > before);
    }

    private long queuedCount() {
        return ((Number) js("return document.querySelectorAll("
                + "'form[action*=addBandForm] input[type=hidden][name^=\"bandIds[\"]').length;")).longValue();
    }

    private Object js(String script, Object... args) {
        return browser.js(script, args);
    }

    static boolean sameName(String a, String b) {
        return a != null && b != null
                && a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }
}
