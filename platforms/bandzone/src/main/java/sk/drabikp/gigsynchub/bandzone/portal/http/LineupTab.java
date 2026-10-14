package sk.drabikp.gigsynchub.bandzone.portal.http;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A concert's "performing bands" tab ({@code updateTabs-at=updateBands}). Makes the performer list
 * equal the gig's lineup plus the publishing band itself: performers not in the lineup are removed
 * (the band itself never is); a missing name is added as the Bandzone profile whose name matches
 * exactly (ignoring case), otherwise as a new "band without a profile", which exists only on this
 * concert — so another concert's such stub, offered by the search, is never linked. What the tab holds
 * is always read from the tab itself, never from the search.
 */
final class LineupTab {

    private static final String ADD = "addBandForm-submit";
    private static final String SEARCH = "1";

    /** One performer: its name, and the address of its "remove from the concert" dialog. */
    private record Performer(String name, String removeLink) {
    }

    private final BandzoneWeb web;
    private final String path;

    LineupTab(BandzoneWeb web, String bandzoneId) {
        this.web = web;
        this.path = "/koncert/" + bandzoneId + "/update?updateTabs-at=updateBands";
    }

    /** Makes the tab's performers the lineup plus the band itself, and checks what Bandzone stored. */
    void sync(List<String> lineup, String ownBandName) throws BandzonePortalException {
        List<String> desired = desired(lineup, ownBandName);
        Document tab = web.get(path).document();
        boolean ownListed = performers(tab).values().stream().anyMatch(p -> sameName(p.name(), ownBandName));
        for (Performer performer : performers(tab).values()) {
            if (!sameName(performer.name(), ownBandName) && desired.stream().noneMatch(d -> sameName(d, performer.name()))) {
                remove(performer);
            }
        }
        addMissing(web.get(path).document(), desired);
        verify(desired, ownBandName, ownListed);
    }

    /** The lineup without the band itself, each name once. */
    private static List<String> desired(List<String> lineup, String ownBandName) {
        List<String> desired = new ArrayList<>();
        for (String name : lineup) {
            if (name == null || name.isBlank()) {
                continue;                                 // a lineup comes from a form and a platform's page alike
            }
            if (!sameName(name, ownBandName) && desired.stream().noneMatch(d -> sameName(d, name))) {
                desired.add(name.trim());
            }
        }
        return desired;
    }

    /** Opens the performer's remove dialog and confirms it — that performer's own form only. */
    private void remove(Performer performer) throws BandzonePortalException {
        String id = query(performer.removeLink(), "ei");
        Answer dialog = web.ajaxGet(performer.removeLink());
        // an answer that only redirects (the concert's address changed with its name or place) is
        // loaded as a whole page, as the page's script does — it opens with the dialog
        List<Element> pages = dialog.redirect() != null && dialog.snippets().isEmpty()
                ? List.of(web.get(dialog.redirect()).document()) : List.copyOf(dialog.snippets());
        // that performer's form only: em=delete and ei exactly its id (50 is not 501)
        NetteForm confirm = pages.stream()
                .flatMap(s -> s.select("form[action]").stream())
                .filter(f -> "delete".equals(query(f.absUrl("action"), "em")) && id.equals(query(f.absUrl("action"), "ei")))
                .findFirst()
                .map(NetteForm::of)
                .orElseThrow(() -> BandzonePortalException.temporary("Bandzone showed no remove dialog for performer '"
                        + performer.name() + "' (" + id + ")."));
        Answer removed = web.post(confirm.action(), confirm.pressing("delete"));
        if (removed.redirect() == null) {
            throw BandzonePortalException.temporary("Bandzone did not remove performer '" + performer.name() + "': "
                    + removed.problems());
        }
    }

    /** Queues every lineup name the tab doesn't list yet and adds them in one go. */
    private void addMissing(Document tab, List<String> desired) throws BandzonePortalException {
        List<String> present = performers(tab).values().stream().map(Performer::name).toList();
        List<String> toAdd = desired.stream().filter(d -> present.stream().noneMatch(p -> sameName(p, d))).toList();
        if (toAdd.isEmpty()) {
            return;
        }
        NetteForm form = NetteForm.find(tab, ADD)
                .orElseThrow(() -> BandzonePortalException.temporary("Bandzone's performers tab has no add form."));
        for (String name : toAdd) {
            form = queue(form, name);
        }
        Answer added = web.post(form.action(), form.pressing("add"));
        if (added.redirect() == null) {
            throw BandzonePortalException.temporary("Bandzone did not add the performers " + toAdd + ": "
                    + added.problems());
        }
    }

    /** Queues one band in the add form: its exact-name profile, or a new profile-less one. */
    private NetteForm queue(NetteForm form, String name) throws BandzonePortalException {
        long before = form.count("bandIds[");
        form.set("bandIds__addCompleter", "").set("bandIds__addCompleter__container[textInput]", name);
        Answer found = web.post(form.action(), form.pressing("bandIds__addCompleter__container[searchButton]", SEARCH));
        Optional<Answer.Item> profile = found.items().stream()
                .filter(item -> !item.stub())
                .filter(item -> sameName(titleOf(item), name))
                .findFirst();
        NetteForm queued;
        if (profile.isPresent()) {
            form.set("bandIds__addCompleter", profile.get().id());
            queued = shown(web.post(form.action(), form.pressing("bandIds__addCompleter__container[selectButton]")), name);
        } else if (found.create() != null) {
            NetteForm creating = shown(web.post(form.action(),
                    form.pressing("bandIds__addCompleter__container[createItemButton]")), name);
            creating.set("bandIds__addCompleter__createItem[name]", name);
            queued = shown(web.post(creating.action(), creating.pressing("bandIds__addCompleter__createItem[create]")), name);
        } else {
            throw BandzonePortalException.temporary("Bandzone offered no way to add band '" + name + "'.");
        }
        if (queued.count("bandIds[") <= before) {
            throw BandzonePortalException.temporary("Bandzone did not queue band '" + name + "'.");
        }
        return queued;
    }

    private static NetteForm shown(Answer answer, String name) throws BandzonePortalException {
        return answer.form(ADD).orElseThrow(() -> BandzonePortalException.temporary(
                "Bandzone did not show the performers form while adding '" + name + "': " + answer.problems()));
    }

    /** The stored performers are the lineup plus the band itself (if it was listed before). */
    private void verify(List<String> desired, String ownBandName, boolean ownListed) throws BandzonePortalException {
        List<String> stored = performers(web.get(path).document()).values().stream().map(Performer::name).toList();
        List<String> missing = desired.stream().filter(d -> stored.stream().noneMatch(s -> sameName(s, d))).toList();
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

    /** The tab's performers in page order, by their element id. */
    private static Map<String, Performer> performers(Document tab) {
        Map<String, Performer> byId = new LinkedHashMap<>();
        for (Element editable : tab.select("#gigEditLineupBands .editable")) {
            Element name = editable.selectFirst(".profileLink h4");
            Element remove = editable.selectFirst("a.delete");
            byId.put(editable.id(), new Performer(name == null ? "" : name.text().trim(),
                    remove == null ? "" : remove.absUrl("href")));
        }
        return byId;
    }

    /** A band's name as the suggestion shows it (its {@code h4.title}), else the value it fills in. */
    private static String titleOf(Answer.Item item) {
        String title = item.part("h4.title");
        return title.isEmpty() ? item.value() : title;
    }

    private static String query(String address, String parameter) {
        String query = URI.create(address.replace(" ", "%20")).getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && pair.substring(0, eq).equals(parameter)) {
                    return pair.substring(eq + 1);
                }
            }
        }
        return "";
    }

    static boolean sameName(String a, String b) {
        return a != null && b != null && a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }
}
