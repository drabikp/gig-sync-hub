package sk.drabikp.bzscraper.accounts.domain;

import org.jmolecules.ddd.annotation.ValueObject;

/** What a member may do in a band. */
@ValueObject
public enum Role {
    /** Everything, the band itself too (its name and address, later its members and accounts). */
    OWNER,
    /** The band's gigs, platforms' work, calendar, import and check. */
    MANAGER;

    public boolean mayChangeTheBand() {
        return this == OWNER;
    }
}
