package sk.drabikp.gigsynchub.gig.application;

/**
 * What was asked for isn't there (a gig deleted meanwhile, a task that never was); nothing was
 * changed. The API answers 404 with the code {@code notFound}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
