package sk.drabikp.bzscraper.accounts.domain;

import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.Objects;

/**
 * Someone managing a band, as what — the "persona": one per user and band. {@code displayName}
 * (optional) is how they appear in that band.
 */
public record Membership(UserId user, BandId band, Role role, String displayName) {

    public Membership {
        Objects.requireNonNull(user, "the member");
        Objects.requireNonNull(band, "the band");
        Objects.requireNonNull(role, "the member's role");
        displayName = displayName == null || displayName.isBlank() ? null : displayName.strip();
    }
}
