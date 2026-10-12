package sk.drabikp.gigsynchub.bandsintown;

/**
 * Why an event is removed from Bandsintown — its delete dialog asks. The name is the dialog's
 * own value; the text goes into its comment box.
 */
public enum RemovalReason {

    /** The gig was cancelled (Bandsintown keeps no cancelled copy). */
    CANCELED("The concert was cancelled."),

    /** Deleted from the catalog. */
    OTHER("Removed by the band.");

    private final String comment;

    RemovalReason(String comment) {
        this.comment = comment;
    }

    public String comment() {
        return comment;
    }
}
