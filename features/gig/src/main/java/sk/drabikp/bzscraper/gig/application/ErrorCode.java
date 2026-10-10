package sk.drabikp.bzscraper.gig.application;

/**
 * A kind of refusal or problem the API names ({@code ProblemResponse.code}, one of gig.yaml's
 * {@code ProblemCodeValue}): each feature lists its own in an enum ({@code <Feature>ErrorCode}),
 * the page says each in the user's language. A code that isn't in the spec stops the app's tests.
 */
public interface ErrorCode {

    /** The code as the API sends it ({@code gigBusy}). */
    String code();
}
