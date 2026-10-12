package sk.drabikp.gigsynchub.accounts.application;

import sk.drabikp.gigsynchub.accounts.application.port.in.AccountsErrorCode;
import sk.drabikp.gigsynchub.accounts.application.port.in.SecretBox;
import sk.drabikp.gigsynchub.gig.application.UserFacingException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link SecretBox} with AES-256-GCM: a random 12-byte nonce per seal (stored before the ciphertext),
 * the context as associated data. The keys are {@code id:base64} (32 bytes each), one per line (or
 * comma-separated); the FIRST is the active one — a new key goes on top, the old ones stay until
 * everything was sealed again with the new one. They come from a file outside the database (the Docker
 * secret {@code gigsynchub.accounts.data-keys}); without any, nothing secret can be stored.
 */
public final class AesGcmSecretBox implements SecretBox {

    private static final int NONCE = 12;
    private static final int TAG_BITS = 128;

    private final Map<String, SecretKeySpec> keys = new LinkedHashMap<>();
    private final String active;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretBox(String keyring) {
        for (String entry : (keyring == null ? "" : keyring).split("[,\\s]+")) {
            if (entry.isBlank()) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon < 1) {
                throw new IllegalStateException("A data key is id:base64 — one is not");
            }
            String id = entry.substring(0, colon);
            byte[] key;
            try {
                key = Base64.getDecoder().decode(entry.substring(colon + 1));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Data key " + id + " is not base64");
            }
            if (key.length != 32) {
                throw new IllegalStateException("Data key " + id + " is not 32 bytes (AES-256)");
            }
            if (keys.putIfAbsent(id, new SecretKeySpec(key, "AES")) != null) {
                throw new IllegalStateException("Data key " + id + " is named twice");
            }
            Arrays.fill(key, (byte) 0);
        }
        this.active = keys.isEmpty() ? null : keys.keySet().iterator().next();
    }

    @Override
    public boolean ready() {
        return active != null;
    }

    @Override
    public Sealed seal(String plain, String context) {
        if (!ready()) {
            throw new UserFacingException(AccountsErrorCode.SECRETS_UNAVAILABLE, Map.of(),
                    "This server has no key to keep secrets with (gigsynchub.accounts.data-keys).");
        }
        byte[] nonce = new byte[NONCE];
        random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, keys.get(active), nonce, context);
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return new Sealed(active, ByteBuffer.allocate(NONCE + sealed.length).put(nonce).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Sealing a secret failed", e);
        }
    }

    @Override
    public String open(Sealed sealed, String context) {
        SecretKeySpec key = keys.get(sealed.keyId());
        if (key == null) {
            throw new IllegalStateException("A secret was sealed with key " + sealed.keyId()
                    + ", which this server doesn't have");
        }
        if (sealed.data().length <= NONCE) {
            throw new IllegalStateException("A sealed secret is too short");
        }
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, key, Arrays.copyOf(sealed.data(), NONCE), context);
            return new String(cipher.doFinal(sealed.data(), NONCE, sealed.data().length - NONCE),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("A secret could not be opened (changed, or sealed for another place)");
        }
    }

    @Override
    public boolean current(Sealed sealed) {
        return sealed.keyId().equals(active);
    }

    private static Cipher cipher(int mode, SecretKeySpec key, byte[] nonce, String context)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    @Override
    public String toString() {
        return "AesGcmSecretBox[keys=" + keys.keySet() + ", active=" + active + "]";
    }
}
