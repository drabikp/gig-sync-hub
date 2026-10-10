package sk.drabikp.bzscraper.accounts.application;

import sk.drabikp.bzscraper.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.bzscraper.accounts.application.port.in.SecretBox;
import sk.drabikp.bzscraper.accounts.domain.PlatformAccount;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.AccountField;
import sk.drabikp.bzscraper.gig.domain.platform.PlatformTraits;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** A platform account as it was filled in, checked against the platform's {@link AccountField}s. */
final class AccountForm {

    private AccountForm() {
    }

    /**
     * The account the page sent, checked against the platform's fields: unknown names and missing
     * required ones refused, blanks dropped, a secret not given kept from {@code kept}.
     */
    static PlatformAccount valid(BandId band, PlatformTraits traits, Map<String, String> settings,
                                 Map<String, String> secrets, PlatformAccount kept, SecretBox box) {
        Map<String, String> newSettings = new HashMap<>();
        Map<String, String> newSecrets = new HashMap<>(kept == null ? Map.of() : kept.secrets());
        Set<String> wrong = new TreeSet<>();
        (settings == null ? Map.<String, String>of() : settings).forEach((name, value) -> {
            Optional<AccountField> field = traits.accountField(name).filter(f -> !f.secret());
            if (field.isEmpty()) {
                wrong.add(name);
            } else if (field.get().kind() == AccountField.Kind.FLAG) {
                newSettings.put(name, String.valueOf(Boolean.parseBoolean(value)));
            } else if (value != null && !value.isBlank()) {
                newSettings.put(name, value.strip());
            }
        });
        (secrets == null ? Map.<String, String>of() : secrets).forEach((name, value) -> {
            if (traits.accountField(name).filter(AccountField::secret).isEmpty()) {
                wrong.add(name);
            } else if (value != null && !value.isBlank()) {
                newSecrets.put(name, value.strip());
            }
        });
        newSecrets.keySet().retainAll(traits.accountFields().stream().filter(AccountField::secret)
                .map(AccountField::name).toList());
        for (AccountField field : traits.accountFields()) {
            Map<String, String> holder = field.secret() ? newSecrets : newSettings;
            if (field.required() && !holder.containsKey(field.name())) {
                wrong.add(field.name());
            }
        }
        if (!wrong.isEmpty()) {
            throw new UserFacingException(AccountsErrorCode.INVALID_ACCOUNT,
                    Map.of("fields", String.join(",", wrong)),
                    "Check the " + traits.displayName() + " account's " + String.join(", ", wrong) + ".");
        }
        if (!newSecrets.isEmpty() && !box.ready()) {
            throw new UserFacingException(AccountsErrorCode.SECRETS_UNAVAILABLE, Map.of(),
                    "This server has no key to keep secrets with (bzscraper.accounts.data-keys).");
        }
        return new PlatformAccount(band, traits.platform(), newSettings, newSecrets, null);
    }
}
