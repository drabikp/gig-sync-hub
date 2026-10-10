package sk.drabikp.bzscraper.gig.domain;

import java.util.UUID;

/** Bands for tests: the first band (made by migration V2) and another one — the same gig in both is two gigs. */
public final class TestBands {

    public static final BandId BAND = BandId.FIRST;
    public static final BandId OTHER = new BandId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    /** Makes {@link #OTHER} in a test database that has only the first band (plain SQL, no module needed). */
    public static final String INSERT_OTHER = "insert into band (id, slug, name, created_at) values "
            + "('00000000-0000-0000-0000-000000000002', 'other', 'Other band', now()) on conflict do nothing";

    private TestBands() {
    }
}
