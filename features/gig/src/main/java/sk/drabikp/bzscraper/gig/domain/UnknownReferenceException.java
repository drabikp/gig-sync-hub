package sk.drabikp.bzscraper.gig.domain;

/**
 * A reference that names nothing this app knows — a gig's token that isn't one, a platform's id
 * no adapter provides. Where it comes from a request, the request was wrong (the API answers 400).
 */
public class UnknownReferenceException extends IllegalArgumentException {

    public UnknownReferenceException(String message) {
        super(message);
    }

    public UnknownReferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
