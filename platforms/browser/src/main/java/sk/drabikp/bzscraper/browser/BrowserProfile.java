package sk.drabikp.bzscraper.browser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Where a platform's browser keeps its saved login, and how it is protected. A saved
 * login (the platform's session cookies) spares a password login — and for Bandsintown
 * an authenticator code — on every task; whoever can read it can act as the band, so:
 * <ul>
 *   <li>the profile directory is private to the user ({@code 700});</li>
 *   <li>Chromium encrypts the cookies with a key kept in the desktop keyring
 *       ({@code --password-store=gnome-libsecret}; cookies are then stored as {@code v11}),
 *       not with its built-in fallback key ({@code v10}, readable by anyone who can read the
 *       files). {@code bzscraper.browser.password-store=auto} (default) uses the keyring
 *       when a desktop session bus is reachable and Chromium's own store otherwise (a
 *       server: there the private directory — on an encrypted volume, owned by the service
 *       user — is the protection);</li>
 *   <li>one profile per ACCOUNT — a band's login on the platform: a profile only ever holds one
 *       band's login, so a test account can't act as the real band, nor one band (who typed
 *       another band's login, without its password) reuse another band's saved login.</li>
 * </ul>
 * The platform decides how long a login stays valid; when it expired, the client logs in
 * with the password again. Deleting the directory forgets the saved login.
 */
final class BrowserProfile {

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");

    private BrowserProfile() {
    }

    /**
     * The account's own directory: {@code <root>/<account key>}, the root {@code configured} when set,
     * else {@code ~/.bzscraper/<platform>-browser} — the key is a hash, so the account is not spelled out
     * on disk.
     */
    public static Path dir(String configured, String platform, String account) {
        Path root = configured != null && !configured.isBlank() ? Paths.get(configured)
                : Paths.get(System.getProperty("user.home"), ".bzscraper", platform + "-browser");
        return root.resolve(accountKey(account));
    }

    /** Creates the directory (and missing parents) readable by the owner only, and tightens an existing one. */
    public static Path ensurePrivate(Path dir) throws IOException {
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        if (posix) {
            Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
            Files.setPosixFilePermissions(dir, OWNER_ONLY);
        } else {
            Files.createDirectories(dir);
        }
        return dir;
    }

    /**
     * The Chromium switch for where saved logins are encrypted: {@code auto} = the keyring
     * when a desktop session bus is reachable, else Chromium's own store; any other value is
     * passed on ({@code gnome-libsecret}, {@code kwallet5}, {@code basic}); blank = Chromium's default.
     */
    public static List<String> secretStoreArgs(String store) {
        return secretStoreArgs(store, System.getenv("DBUS_SESSION_BUS_ADDRESS"));
    }

    static List<String> secretStoreArgs(String store, String sessionBus) {
        if (store == null || store.isBlank()) {
            return List.of();
        }
        String chosen = store.strip();
        if (chosen.equals("auto")) {
            chosen = sessionBus == null || sessionBus.isBlank() ? "basic" : "gnome-libsecret";
        }
        return List.of("--password-store=" + chosen);
    }

    static String accountKey(String account) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(account.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
