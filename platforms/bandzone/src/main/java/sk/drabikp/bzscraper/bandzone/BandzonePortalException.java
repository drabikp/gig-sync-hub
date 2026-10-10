package sk.drabikp.bzscraper.bandzone;

import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;
import sk.drabikp.bzscraper.sync.application.port.out.PlatformException;

/**
 * A Bandzone operation failed — logging in, reading the band page, creating, editing, cancelling
 * or deleting a concert; {@link #kind()} says whether to try again, another way, or ask the user.
 * Made only through the factories, each named after what the failure means.
 */
public final class BandzonePortalException extends PlatformException {

    private BandzonePortalException(String message, Throwable cause, FailureKind kind) {
        super(message, cause, kind);
    }

    /** It may work later (a timeout, a page that didn't load): this step again, later. */
    public static BandzonePortalException temporary(String message) {
        return temporary(message, null);
    }

    /** Like {@link #temporary(String)}, with what caused it. */
    public static BandzonePortalException temporary(String message, Throwable cause) {
        return new BandzonePortalException(message, cause, FailureKind.TEMPORARY);
    }

    /** Bandzone said no to doing it this way; nothing happened there. */
    public static BandzonePortalException refused(String message) {
        return new BandzonePortalException(message, null, FailureKind.REFUSED);
    }

    /** Only the user can sort it out (switched off, a wrong login, data to correct). */
    public static BandzonePortalException needsUser(String message) {
        return needsUser(message, null);
    }

    /** Like {@link #needsUser(String)}, with what caused it. */
    public static BandzonePortalException needsUser(String message, Throwable cause) {
        return new BandzonePortalException(message, cause, FailureKind.NEEDS_USER);
    }

    /** The browser is busy with another operation (or the app is stopping) — not tried; again later. */
    public static BandzonePortalException busy(String message) {
        return new BandzonePortalException(message, null, FailureKind.BUSY);
    }
}
