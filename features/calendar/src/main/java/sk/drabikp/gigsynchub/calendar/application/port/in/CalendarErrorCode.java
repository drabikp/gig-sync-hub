package sk.drabikp.gigsynchub.calendar.application.port.in;

import sk.drabikp.gigsynchub.gig.application.ErrorCode;

/** What the band calendar says no with. */
public enum CalendarErrorCode implements ErrorCode {

    /** The calendar couldn't be read (unreachable, not a calendar). */
    CALENDAR_UNAVAILABLE("calendarUnavailable"),
    /** A calendar address or zone that isn't one; {@code fields}: address, zone. */
    INVALID_CALENDAR("invalidCalendar"),
    /** One of the band's own rules isn't one; {@code rule} (its number, from 1), {@code reason}. */
    INVALID_RULE("invalidRule");

    private final String code;

    CalendarErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
