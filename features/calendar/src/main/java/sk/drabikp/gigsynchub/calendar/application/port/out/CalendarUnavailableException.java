package sk.drabikp.gigsynchub.calendar.application.port.out;

/**
 * The calendar could not be read (not configured, unreachable, not a calendar). The message is
 * shown to the user; the API answers 502 with the code {@code calendarUnavailable}.
 */
public class CalendarUnavailableException extends RuntimeException {

    public CalendarUnavailableException(String message) {
        super(message);
    }

    public CalendarUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
