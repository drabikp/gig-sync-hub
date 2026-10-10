package sk.drabikp.bzscraper.accounts.application.port.out;

import sk.drabikp.bzscraper.accounts.domain.PlatformAccount;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.Map;
import java.util.TreeMap;

/**
 * How a platform turns the account a band has just typed into what is KEPT for it: it signs in once with
 * what was typed and says what to store — a session instead of the password, so the server never writes a
 * band's platform password down and a break-in yields something the band can revoke. Implemented by the
 * platform; one that has none has its account stored as typed.
 */
public interface PlatformSignIn {

    Platform platform();

    /**
     * Signs in with what was typed and says what to keep, or why it couldn't. Never throws — a platform
     * saying no is an answer, not a fault.
     */
    Kept signIn(PlatformAccount typed);

    /**
     * The account to keep (by the platform's {@link sk.drabikp.bzscraper.gig.domain.platform.AccountField}
     * names), or the reason there is none. {@link #toString} names only which secrets are set.
     */
    record Kept(Map<String, String> settings, Map<String, String> secrets, String problem) {

        public Kept {
            settings = settings == null ? Map.of() : Map.copyOf(settings);
            secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
        }

        /** Signed in: keep these settings and secrets instead of what was typed. */
        public static Kept of(Map<String, String> settings, Map<String, String> secrets) {
            return new Kept(settings, secrets, null);
        }

        /** Couldn't sign in, in the platform's own words; nothing is kept. */
        public static Kept failed(String problem) {
            return new Kept(Map.of(), Map.of(), problem == null || problem.isBlank() ? "it didn't say why" : problem);
        }

        public boolean signedIn() {
            return problem == null;
        }

        @Override
        public String toString() {
            return "Kept[settings=" + new TreeMap<>(settings) + ", secrets=" + new TreeMap<>(secrets).keySet()
                    + ", problem=" + problem + "]";
        }
    }
}
