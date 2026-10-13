package sk.drabikp.gigsynchub.accounts.application.port.in;

import java.util.Arrays;

/**
 * Keeps the bands' secrets (platform passwords, authenticator secrets, a calendar's private address)
 * encrypted at rest with keys kept outside the database. Each secret is sealed for one place
 * ({@code context}, e.g. its row's band and platform): opened anywhere else, it fails — a copied
 * ciphertext can't be read as another band's.
 */
public interface SecretBox {

    /** Sealed data and the key it was sealed with. */
    record Sealed(String keyId, byte[] data) {

        public Sealed {
            if (keyId == null || keyId.isBlank() || data == null) {
                throw new IllegalArgumentException("sealed data names its key");
            }
        }

        /**
         * By content, not by which array object it is — the three a record would have generated
         * compare {@code data} by identity, so two Sealed holding the same bytes are unequal, hash
         * differently, and print as {@code [B@1f2a3b}. None of that is what anyone means by two
         * sealed values being the same, and it is the kind of thing that is only ever discovered
         * by a set quietly holding two of something.
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof Sealed(String id, byte[] bytes)
                    && keyId.equals(id) && Arrays.equals(data, bytes);
        }

        @Override
        public int hashCode() {
            return 31 * keyId.hashCode() + Arrays.hashCode(data);
        }

        /** The key's name and how much there is — never the ciphertext itself. */
        @Override
        public String toString() {
            return "Sealed[keyId=" + keyId + ", data=" + data.length + " bytes]";
        }
    }

    /** False when this server has no key: nothing secret can be stored (nor read). */
    boolean ready();

    /**
     * {@code plain} sealed for {@code context} with the active key; refused
     * ({@link AccountsErrorCode#SECRETS_UNAVAILABLE}) when this server has no key.
     */
    Sealed seal(String plain, String context);

    /**
     * The plain text; {@link IllegalStateException} when its key is gone or it was sealed for another
     * context (or changed).
     */
    String open(Sealed sealed, String context);

    /** True when {@code sealed} was sealed with the active key (else it is sealed again — key rotation). */
    boolean current(Sealed sealed);
}
