package sk.drabikp.bzscraper.gig.application;

/** What the shared kernel says no with. */
public enum GigErrorCode implements ErrorCode {

    /** The gig is not as the user saw it ({@link ConcurrentChangeException}). */
    CHANGED_MEANWHILE("changedMeanwhile"),
    /** The gig is gone ({@link ConcurrentChangeException}). */
    DELETED_MEANWHILE("deletedMeanwhile"),
    /** A gig form that isn't a gig yet; {@code fields} names what ({@link InvalidGigException}). */
    INVALID_GIG("invalidGig"),
    /** What was asked for isn't there ({@link NotFoundException}). */
    NOT_FOUND("notFound"),
    /** The request makes no sense ({@link BadRequestException}, a reference naming nothing). */
    BAD_REQUEST("badRequest"),
    /** A setting the work needs isn't set; {@code setting} names it. */
    NOT_CONFIGURED("notConfigured");

    private final String code;

    GigErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
