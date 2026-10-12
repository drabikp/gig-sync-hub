package sk.drabikp.gigsynchub.gig.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Where a page carries on from: the last row's show time and id. A list is paged by this rather than
 * by counting rows ("give me the 400th onwards"), so the store always lands on the next row through
 * its index, however deep the band scrolls, and rows that arrive or leave meanwhile cannot make one
 * slide past unseen or repeat.
 *
 * <p>It travels as one opaque string, because what it holds is the store's business.
 */
@ValueObject
public record GigCursor(LocalDateTime showAt, GigId id) {

    public GigCursor {
        if (showAt == null || id == null) {
            throw new IllegalArgumentException("a cursor is a row's show time and id");
        }
    }

    /** The cursor that reads on after this row. */
    public static GigCursor after(GigRow row) {
        return new GigCursor(row.showAt(), row.id());
    }

    public String encoded() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((showAt + "\u001f" + id.key()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A cursor the page sent back, or null when there is none. Anything that isn't one is refused
     * rather than quietly read as "from the beginning", which would silently re-send the first page.
     */
    public static GigCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        String[] parts = text(encoded).split("\u001f", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("not a cursor from this list");
        }
        try {
            return new GigCursor(LocalDateTime.parse(parts[0]), GigId.fromKey(parts[1]));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not a cursor from this list", e);
        }
    }

    private static String text(String encoded) {
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("not a cursor from this list", e);
        }
    }
}
