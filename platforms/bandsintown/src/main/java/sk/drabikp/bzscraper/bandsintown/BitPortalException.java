package sk.drabikp.bzscraper.bandsintown;

import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;
import sk.drabikp.bzscraper.sync.application.port.out.PlatformException;

/**
 * A Bandsintown artist-portal operation failed — logging in, listing, uploading, editing or
 * deleting events; {@link #kind()} says whether to try again, another way, or ask the user.
 * Made only through the factories, each named after what the failure means.
 */
public final class BitPortalException extends PlatformException {

    private BitPortalException(String message, Throwable cause, FailureKind kind) {
        super(message, cause, kind);
    }

    /** It may work later (a timeout, a page that didn't load): this step again, later. */
    public static BitPortalException temporary(String message) {
        return temporary(message, null);
    }

    /** Like {@link #temporary(String)}, with what caused it. */
    public static BitPortalException temporary(String message, Throwable cause) {
        return new BitPortalException(message, cause, FailureKind.TEMPORARY);
    }

    /** Bandsintown said no to doing it this way; nothing happened there. */
    public static BitPortalException refused(String message) {
        return new BitPortalException(message, null, FailureKind.REFUSED);
    }

    /**
     * Only the user can sort it out: switched off or not configured, a login or authenticator
     * secret to check, data to correct, a draft left behind there.
     */
    public static BitPortalException needsUser(String message) {
        return needsUser(message, null);
    }

    /** Like {@link #needsUser(String)}, with what caused it. */
    public static BitPortalException needsUser(String message, Throwable cause) {
        return new BitPortalException(message, cause, FailureKind.NEEDS_USER);
    }

    /** The browser is busy with another operation (or the app is stopping) — not tried; again later. */
    public static BitPortalException busy(String message) {
        return new BitPortalException(message, null, FailureKind.BUSY);
    }
}
