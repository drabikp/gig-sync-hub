package sk.drabikp.bzscraper.gig.domain;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Objects;
import java.util.UUID;

/**
 * Which band something belongs to — every gig, publication, sync task, calendar event and band
 * setting is one band's, and everything that reads or writes them names the band. A UUID: it never
 * changes (the band's slug, for addresses, may).
 */
@ValueObject
public record BandId(UUID value) {

    /**
     * The band the app ran for before it knew bands (migration V2 gave it every existing row). Until
     * bands connect their own accounts, the configured platform accounts and calendar are its.
     */
    public static final BandId FIRST = new BandId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    public BandId {
        Objects.requireNonNull(value, "a band's id");
    }

    /** The band an id names; {@link UnknownReferenceException} when it isn't one. */
    public static BandId of(String text) {
        try {
            return new BandId(UUID.fromString(text));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnknownReferenceException("not a band: " + text);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
