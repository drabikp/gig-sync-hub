package sk.drabikp.bzscraper.web;

import sk.drabikp.bzscraper.gig.application.ErrorCode;

/** What the app's own web part says no with. */
public enum WebErrorCode implements ErrorCode {

    /** Anything else — a fault of the app, logged in full. */
    UNEXPECTED("unexpected");

    private final String code;

    WebErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
