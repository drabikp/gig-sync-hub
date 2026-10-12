package sk.drabikp.gigsynchub.importing.application.port.out;

import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

/**
 * Reading a platform ({@link GigImporter}) failed in a way its adapter didn't expect: logged in full,
 * the user gets a short line.
 */
public final class PlatformReads {

    private static final System.Logger log = System.getLogger(PlatformReads.class.getName());

    private PlatformReads() {
    }

    public static String unexpected(Platform platform, RuntimeException e) {
        log.log(System.Logger.Level.ERROR, "Reading " + platform.id() + " failed unexpectedly", e);
        return "an unexpected error (" + e.getClass().getSimpleName() + ") — details are in the app's log";
    }
}
