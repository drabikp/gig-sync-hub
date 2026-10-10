package sk.drabikp.bzscraper.bandzone.portal.http;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What Bandzone answers a form's AJAX request with (JSON): a picker's search gives {@code items} (and
 * a {@code create} offer), a pick or an upload gives the form again ({@code snippets}), a finished
 * form tells where to go next ({@code redirect}); {@code flash} holds its messages.
 */
record Answer(List<Item> items, Item create, String redirect, List<Document> snippets, List<String> messages) {

    /** One suggestion of a picker: Bandzone's id, the name it fills in, and its HTML. */
    record Item(String id, String value, String title, String label) {

        /** The label's parts, e.g. a town's {@code .name} and {@code .description}. */
        String part(String cssQuery) {
            Element element = Jsoup.parseBodyFragment(label).selectFirst(cssQuery);
            return element == null ? "" : element.text().trim();
        }

        /** Whether the label shows a profile-less "band without profile" (exists on one concert only). */
        boolean stub() {
            return label.contains("bandStub");
        }
    }

    static Answer of(JsonNode json, String baseUri) {
        List<Item> items = new ArrayList<>();
        for (JsonNode item : json.path("items")) {
            items.add(item(item));
        }
        JsonNode create = json.path("create");
        List<Document> snippets = new ArrayList<>();
        json.path("snippets").forEach(html -> snippets.add(Jsoup.parseBodyFragment(html.asString(""), baseUri)));
        List<String> messages = new ArrayList<>();
        json.path("flash").forEach(flash -> messages.add(flash.path("message").asString("")));
        String redirect = json.path("redirect").asString("");
        return new Answer(items, create.isObject() ? item(create) : null, redirect.isBlank() ? null : redirect,
                snippets, messages);
    }

    /** The form with {@code signal} among the snippets: the form as Bandzone shows it now. */
    Optional<NetteForm> form(String signal) {
        return snippets.stream().map(s -> NetteForm.find(s, signal)).flatMap(Optional::stream).findFirst();
    }

    /**
     * Why the form wasn't taken, as Bandzone's re-rendered form says it (its error messages), else
     * its messages.
     */
    String problems() {
        String errors = snippets.stream()
                .flatMap(s -> s.select(".error, .errors li, ul.error li, .form-error, .flash.error").stream())
                .map(e -> e.text().trim()).filter(t -> !t.isEmpty()).distinct()
                .collect(Collectors.joining("; "));
        return !errors.isEmpty() ? errors : messages.isEmpty() ? "no reason given" : String.join("; ", messages);
    }

    private static Item item(JsonNode item) {
        return new Item(item.path("id").asString(""), item.path("value").asString(""),
                item.path("title").asString(""), item.path("label").asString(""));
    }
}
