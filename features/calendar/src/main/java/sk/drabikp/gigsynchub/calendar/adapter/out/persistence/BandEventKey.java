package sk.drabikp.gigsynchub.calendar.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** The key of a band's calendar event rows (saved copy, verdict, link): the band and the event's uid. */
final class BandEventKey implements Serializable {

    private UUID bandId;
    private String eventUid;

    BandEventKey() {
        // for JPA
    }

    BandEventKey(UUID bandId, String eventUid) {
        this.bandId = bandId;
        this.eventUid = eventUid;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BandEventKey k && Objects.equals(bandId, k.bandId) && Objects.equals(eventUid, k.eventUid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bandId, eventUid);
    }
}
