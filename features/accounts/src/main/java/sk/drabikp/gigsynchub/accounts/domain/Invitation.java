package sk.drabikp.gigsynchub.accounts.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An owner's invitation into a band: for one e-mail address, as a {@link Role}, until {@code expiresAt}.
 * Whoever signs in with that address and opens the link becomes a member — once ({@code acceptedAt}).
 */
@AggregateRoot
public record Invitation(@Identity UUID id, BandId band, String email, Role role, UserId invitedBy, Instant createdAt,
                         Instant expiresAt, Instant acceptedAt) {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public Invitation {
        Objects.requireNonNull(id, "the invitation's id");
        Objects.requireNonNull(band, "the band");
        Objects.requireNonNull(role, "the role");
        if (!validEmail(email)) {
            throw new IllegalArgumentException("not an e-mail address");
        }
        email = normalized(email);
    }

    public static boolean validEmail(String email) {
        return email != null && email.strip().length() <= 320 && EMAIL.matcher(email.strip()).matches();
    }

    public static String normalized(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Still open: not accepted and not expired. */
    public boolean open(Instant now) {
        return acceptedAt == null && now.isBefore(expiresAt);
    }

    /** For the signed-in user with {@code email}. */
    public boolean isFor(String email) {
        return email != null && this.email.equals(normalized(email));
    }

    /** The address as a stranger with the link may see it: "j***@example.com". */
    public String maskedEmail() {
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }

    public Invitation accepted(Instant at) {
        return new Invitation(id, band, email, role, invitedBy, createdAt, expiresAt, at);
    }
}
