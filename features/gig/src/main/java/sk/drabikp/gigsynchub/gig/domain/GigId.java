package sk.drabikp.gigsynchub.gig.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a {@link Gig}: the gig's own, independent of anything it says about itself. A gig
 * that moves to another day or another venue is still that gig — so its publications, its queued
 * platform work and the calendar's link to it need not follow it anywhere.
 *
 * <p>It was the gig's natural key (day + venue) until 2026-10, which is why so much of this app
 * speaks of a gig "moving": an edit to the day changed WHO the gig was, and every record that
 * named it had to be re-keyed in the same transaction. That is what a surrogate identity is for.
 * The natural key is still kept, as {@link GigMatch} — not as identity, but as the question
 * "which concert is this copy about", which is what Import and the nightly check need it for.
 *
 * <p>Nothing reads a part of it: {@link #key()} is how stored rows name a gig, {@link #token()}
 * how addresses do, and both are the plain text of the id.
 */
@ValueObject
public record GigId(UUID value) {

    public GigId {
        Objects.requireNonNull(value, "a gig's id");
    }

    /** An identity no gig has yet — a gig gets one when it is created, and keeps it. */
    public static GigId fresh() {
        return new GigId(UUID.randomUUID());
    }

    /** The identity as one text — how stored records refer to a gig. */
    public String key() {
        return value.toString();
    }

    /** The identity a {@link #key()} stands for; a stored text that isn't one is a broken row. */
    public static GigId fromKey(String key) {
        return new GigId(UUID.fromString(key));
    }

    /** The identity as it travels in addresses that name a gig ({@code /api/gigs/<token>}). */
    public String token() {
        return value.toString();
    }

    /** The identity a {@link #token()} stands for; {@link UnknownReferenceException} when it stands for none. */
    public static GigId fromToken(String token) {
        try {
            return new GigId(UUID.fromString(token));
        } catch (RuntimeException e) {
            throw new UnknownReferenceException("not a gig: " + token, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
