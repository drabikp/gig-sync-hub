package sk.drabikp.bzscraper.gig.application;

/**
 * What a caller asked for makes no sense (a proposal that isn't in the plan, a verdict that is
 * no verdict); nothing was changed. The API answers 400 with the code {@code badRequest} — unlike
 * an {@link IllegalArgumentException} from inside, which is a fault of the app.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
