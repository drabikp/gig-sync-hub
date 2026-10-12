package sk.drabikp.gigsynchub.bandzone.portal.http;

import org.jsoup.nodes.Element;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.portal.BandzoneTowns;
import sk.drabikp.gigsynchub.gig.domain.EntryType;
import sk.drabikp.gigsynchub.gig.domain.Gig;
import sk.drabikp.gigsynchub.gig.domain.Location;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A concert's fields in one of Bandzone's forms — the wizard's steps and the edit form share their
 * names: date and time, the town (city picker), the club (venue picker), the info fields, the poster.
 * A picker is the form's own buttons: its search answers suggestions, its pick answers the form again,
 * which then is the form ({@link #form()}) — as in the page, where a pick re-renders it.
 */
final class ConcertForm {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    /** A picker's search button is pressed as "1", as the page's script does. */
    private static final String SEARCH = "1";

    private final BandzoneWeb web;
    private final String signal;
    private NetteForm form;

    ConcertForm(BandzoneWeb web, NetteForm form, String signal) {
        this.web = web;
        this.form = form;
        this.signal = signal;
    }

    NetteForm form() {
        return form;
    }

    void setStart(ZonedDateTime start) {
        form.set("start[date]", start.format(DATE)).set("start[time]", start.format(TIME));
    }

    /** The end, or none (cleared). */
    void setEnd(ZonedDateTime end) {
        form.set("end[date]", end == null ? "" : end.format(DATE)).set("end[time]", end == null ? "" : end.format(TIME));
    }

    /**
     * Picks the town through the city search: the suggestion that fits by name, country and district
     * ({@link BandzoneTowns}); several fits or none → the user corrects the town, never a guess. A town
     * the form shows already is kept only when it was merely typed (a picked one is chosen again: the
     * name alone may be another town of the same name).
     */
    void selectCity(Location location) throws BandzonePortalException {
        String city = location.city();
        if (location.district() == null && city.equalsIgnoreCase(form.value("cityId__container[textInput]"))
                && !form.value("cityId").isBlank()) {
            return;
        }
        form.set("cityId", "").set("cityId__container[textInput]", city);
        Answer found = web.post(form.action(), form.pressing("cityId__container[searchButton]", SEARCH));
        List<BandzoneTowns.Suggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < found.items().size(); i++) {
            Answer.Item item = found.items().get(i);
            String name = item.part(".name");
            suggestions.add(new BandzoneTowns.Suggestion(i, name.isEmpty() ? item.value() : name, item.part(".description")));
        }
        BandzoneTowns.Choice choice = BandzoneTowns.choose(city, location.country(), location.district(), suggestions);
        if (choice.pick() == null) {
            throw BandzonePortalException.needsUser(choice.problem());
        }
        Answer.Item town = found.items().get(choice.pick().index());
        form.set("cityId", town.id()).set("cityId__container[textInput]", town.value());
        pick("cityId__container[selectButton]", "cityId", town.id());
    }

    /**
     * Sets the club. An exact (case-insensitive) match in Bandzone's club database links the club;
     * anything else is kept as free text, which Bandzone accepts. No venue clears it.
     */
    void selectVenue(String venue) throws BandzonePortalException {
        if (venue != null && venue.equalsIgnoreCase(form.value("venueId__container[textInput]"))) {
            return;
        }
        form.set("venueId", "").set("venueId__container[textInput]", venue == null ? "" : venue);
        if (venue == null) {
            return;
        }
        Answer found = web.post(form.action(), form.pressing("venueId__container[searchButton]", SEARCH));
        Optional<Answer.Item> club = found.items().stream()
                .filter(item -> venue.equalsIgnoreCase(titleOf(item)))
                .findFirst();
        if (club.isPresent()) {
            form.set("venueId", club.get().id()).set("venueId__container[textInput]", club.get().value());
            pick("venueId__container[selectButton]", "venueId", club.get().id());
        }
    }

    /** Sets every info field, blanking the optional ones the gig does not have (for edits). */
    void fillInfo(Gig gig) {
        form.set("name", gig.title())
                .set("entryType", entryTypeValue(gig.admission().type()))
                .set("entry", gig.admission().isPaid() ? gig.admission().amount() : "")
                .set("info", orEmpty(gig.description()))
                .set("facebookUrl", orEmpty(gig.facebookUrl()));
    }

    /**
     * Uploads the poster through the form's uploader (Bandzone keeps one image; a new upload replaces
     * it), then reloads the form as the page does, which then shows the image.
     */
    void uploadPoster(Path file) throws BandzonePortalException {
        Element uploader = form.element().selectFirst(".fileupload[data-url]");
        Element input = form.element().selectFirst("input[type=file]");
        if (uploader == null || input == null) {
            throw BandzonePortalException.temporary("Bandzone's edit form has no poster uploader.");
        }
        web.upload(uploader.absUrl("data-url"), input.attr("name"), file);
        Answer reloaded = web.post(form.action(), form.pressing("_FileUploadReloadSubmit_", "reload"));
        form = reloaded.form(signal).orElseThrow(() -> BandzonePortalException.temporary(
                "Bandzone did not show the form again after the poster upload."));
    }

    /** Sends the form with {@code button}; Bandzone answers where to go next, or the form with its errors. */
    String send(String button, String what) throws BandzonePortalException {
        Answer answer = web.post(form.action(), form.pressing(button));
        if (answer.redirect() == null) {
            throw BandzonePortalException.temporary("Bandzone did not take " + what + ": " + answer.problems());
        }
        return answer.redirect();
    }

    /** The {@code entryType} radio value Bandzone stores (0 = paid, 1 = voluntary, 2 = free). */
    static String entryTypeValue(EntryType entryType) {
        return switch (entryType == null ? EntryType.FREE : entryType) {
            case PAID -> "0";
            case VOLUNTARY -> "1";
            case FREE -> "2";
        };
    }

    /** Presses a picker's pick and takes the form Bandzone answers with, which must hold the pick. */
    private void pick(String button, String idField, String id) throws BandzonePortalException {
        Answer picked = web.post(form.action(), form.pressing(button));
        NetteForm shown = picked.form(signal).orElseThrow(() -> BandzonePortalException.temporary(
                "Bandzone did not show the form again after picking " + idField + " " + id + "."));
        if (!id.equals(shown.value(idField))) {
            throw BandzonePortalException.temporary("Bandzone did not take the pick " + idField + " " + id + ".");
        }
        form = shown;
    }

    /** A club's name as the suggestion shows it (its {@code h4.title}), else the value it fills in. */
    private static String titleOf(Answer.Item item) {
        String title = item.part("h4.title");
        return title.isEmpty() ? item.value() : title;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
