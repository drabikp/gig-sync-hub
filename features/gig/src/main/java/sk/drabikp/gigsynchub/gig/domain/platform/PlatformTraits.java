package sk.drabikp.gigsynchub.gig.domain.platform;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a platform is like, as far as the core needs to know — told by the platform's adapter.
 *
 * @param displayName          how people write its name
 * @param keepsCancelledEvents a cancelled gig stays listed there as cancelled (otherwise
 *                             cancelling removes it)
 * @param listsBandSlot        it lists when the band plays (the band's slot of a longer event)
 *                             rather than the whole event
 * @param carriesAdmission     it shows the entry (fee or free)
 * @param importPrecedence     whose details win on import when versions differ: lower first
 *                             (the catalog's always win)
 * @param eventUrlTemplate     the public page of an event, {@code {id}} standing for its id
 * @param accountFields        what a band's account there is made of (empty: the platform needs none)
 */
@ValueObject
public record PlatformTraits(Platform platform, String displayName, boolean keepsCancelledEvents,
                             boolean listsBandSlot, boolean carriesAdmission, int importPrecedence,
                             String eventUrlTemplate, List<AccountField> accountFields) {

    public PlatformTraits {
        if (platform == null || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("a platform needs its id and name");
        }
        accountFields = accountFields == null ? List.of() : List.copyOf(accountFields);
        if (accountFields.stream().map(AccountField::name).distinct().count() != accountFields.size()) {
            throw new IllegalArgumentException(platform + " names an account field twice");
        }
    }

    /** The account field called {@code name}, if the platform has it. */
    public Optional<AccountField> accountField(String name) {
        return accountFields.stream().filter(f -> f.name().equals(name)).findFirst();
    }

    /**
     * Traits named one by one: {@code PlatformTraits.of(platform, "Name").listsBandSlot(true)…build()}.
     * What isn't named is the plain platform's: keeps cancelled events, lists the whole event, shows
     * the entry, its details win last on import, no public event page, no account.
     */
    public static Builder of(Platform platform, String displayName) {
        return new Builder(platform, displayName);
    }

    /** The event's public page, or null when the platform has none. */
    public String eventUrl(String externalRef) {
        return eventUrlTemplate == null || externalRef == null || externalRef.isBlank() ? null
                : eventUrlTemplate.replace("{id}", externalRef);
    }

    public static final class Builder {

        private final Platform platform;
        private final String displayName;
        private boolean keepsCancelledEvents = true;
        private boolean listsBandSlot;
        private boolean carriesAdmission = true;
        private int importPrecedence = Integer.MAX_VALUE;
        private String eventUrlTemplate;
        private final List<AccountField> accountFields = new ArrayList<>();

        private Builder(Platform platform, String displayName) {
            this.platform = platform;
            this.displayName = displayName;
        }

        public Builder keepsCancelledEvents(boolean keeps) {
            this.keepsCancelledEvents = keeps;
            return this;
        }

        public Builder listsBandSlot(boolean lists) {
            this.listsBandSlot = lists;
            return this;
        }

        public Builder carriesAdmission(boolean carries) {
            this.carriesAdmission = carries;
            return this;
        }

        public Builder importPrecedence(int precedence) {
            this.importPrecedence = precedence;
            return this;
        }

        public Builder eventUrl(String template) {
            this.eventUrlTemplate = template;
            return this;
        }

        /** A field of a band's account there, in the order the form shows them. */
        public Builder accountField(AccountField field) {
            this.accountFields.add(field);
            return this;
        }

        public PlatformTraits build() {
            return new PlatformTraits(platform, displayName, keepsCancelledEvents, listsBandSlot, carriesAdmission,
                    importPrecedence, eventUrlTemplate, accountFields);
        }
    }
}
