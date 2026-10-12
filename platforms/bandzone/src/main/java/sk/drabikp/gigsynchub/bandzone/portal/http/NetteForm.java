package sk.drabikp.gigsynchub.bandzone.portal.http;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One of Bandzone's forms as the browser would send it: its address and the fields a browser posts
 * (enabled inputs, the checked radio, the textareas and selects — no buttons, no file inputs), in page
 * order, plus its buttons' values. Bandzone's widgets (the town, club and band pickers, the poster
 * uploader) are buttons of the same form: pressing one sends the whole form with that button.
 */
final class NetteForm {

    private final Element element;
    private final String action;
    private final List<Map.Entry<String, String>> fields = new ArrayList<>();
    private final Map<String, String> buttons = new LinkedHashMap<>();

    private NetteForm(Element element) {
        this.element = element;
        this.action = element.absUrl("action");
        for (Element input : element.select("input, textarea, select")) {
            String name = input.attr("name");
            if (name.isEmpty()) {
                continue;
            }
            String type = input.attr("type").toLowerCase();
            if (type.equals("submit") || type.equals("button") || type.equals("image")) {
                buttons.putIfAbsent(name, input.attr("value"));     // pressed by name, disabled or not
                continue;
            }
            if (input.hasAttr("disabled") || type.equals("file") || type.equals("reset")) {
                continue;
            }
            if ((type.equals("radio") || type.equals("checkbox")) && !input.hasAttr("checked")) {
                continue;
            }
            fields.add(new SimpleEntry<>(name, valueOf(input)));
        }
        for (Element button : element.select("button[name]")) {
            buttons.putIfAbsent(button.attr("name"), button.attr("value"));
        }
    }

    /** The form whose address holds {@code signal} (e.g. {@code updateForm-submit}) under {@code root}. */
    static Optional<NetteForm> find(Element root, String signal) {
        Elements forms = root.select("form[action*=\"" + signal + "\"]");
        return forms.isEmpty() ? Optional.empty() : Optional.of(new NetteForm(forms.first()));
    }

    /** {@code form} as the browser would send it. */
    static NetteForm of(Element form) {
        return new NetteForm(form);
    }

    String action() {
        return action;
    }

    Element element() {
        return element;
    }

    /** The field's value as it would be sent; "" when the form doesn't send it. */
    String value(String name) {
        return fields.stream().filter(f -> f.getKey().equals(name)).map(Map.Entry::getValue).findFirst().orElse("");
    }

    /** How many fields are sent under names starting with {@code prefix} (e.g. the queued {@code bandIds[}). */
    long count(String prefix) {
        return fields.stream().filter(f -> f.getKey().startsWith(prefix)).count();
    }

    /**
     * Sets a field (a radio group too); a field the form doesn't have is an error, never silently
     * added — Bandzone would ignore it.
     */
    NetteForm set(String name, String value) {
        boolean known = element.selectFirst("[name=\"" + name + "\"]") != null
                || fields.stream().anyMatch(f -> f.getKey().equals(name));
        if (!known) {
            throw new IllegalStateException("Bandzone field " + name);
        }
        int at = -1;
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).getKey().equals(name)) {
                if (at < 0) {
                    at = i;
                    fields.set(i, new SimpleEntry<>(name, value));
                } else {
                    fields.remove(i--);
                }
            }
        }
        if (at < 0) {
            fields.add(new SimpleEntry<>(name, value));
        }
        return this;
    }

    /** The fields with {@code button} pressed, with the value the page gives it. */
    List<Map.Entry<String, String>> pressing(String button) {
        String value = buttons.get(button);
        if (value == null) {
            throw new IllegalStateException("Bandzone button " + button);
        }
        return pressing(button, value);
    }

    /** The fields with {@code button} pressed as {@code value} (the pickers' search buttons send "1"). */
    List<Map.Entry<String, String>> pressing(String button, String value) {
        if (!buttons.containsKey(button)) {
            throw new IllegalStateException("Bandzone button " + button);
        }
        List<Map.Entry<String, String>> sent = new ArrayList<>(fields);
        sent.add(new SimpleEntry<>(button, value));
        return sent;
    }

    private static String valueOf(Element input) {
        if (input.nameIs("textarea")) {
            String text = input.wholeText();        // as typed: line breaks kept (a browser drops one leading)
            return text.startsWith("\n") ? text.substring(1) : text;
        }
        if (input.nameIs("select")) {
            Element selected = input.selectFirst("option[selected]");
            if (selected == null) {
                selected = input.selectFirst("option");
            }
            return selected == null ? "" : selected.hasAttr("value") ? selected.attr("value") : selected.text();
        }
        return input.attr("value");
    }
}
