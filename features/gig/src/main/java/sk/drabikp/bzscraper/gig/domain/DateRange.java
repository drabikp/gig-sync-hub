package sk.drabikp.bzscraper.gig.domain;

import java.time.LocalDate;

public record DateRange(LocalDate start, LocalDate end) {
    public DateRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end must not be null");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }
    }

    /**
     * From this day on — what a reconciliation asks for ("the upcoming gigs and the recent past").
     * The far end is a date no catalog reaches rather than an open end, so the store still has a
     * range to look up in its index instead of reading the band's rows and sifting them afterwards.
     */
    public static DateRange from(LocalDate start) {
        return new DateRange(start, LocalDate.of(9999, 12, 31));
    }

    public boolean containsInclusive(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
