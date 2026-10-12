package sk.drabikp.gigsynchub.gig.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateRangeTest {

    @Test
    void rejectsNullStart() {
        assertThrows(IllegalArgumentException.class,
                () -> new DateRange(null, LocalDate.of(2026, 1, 1)));
    }

    @Test
    void rejectsNullEnd() {
        assertThrows(IllegalArgumentException.class,
                () -> new DateRange(LocalDate.of(2026, 1, 1), null));
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThrows(IllegalArgumentException.class,
                () -> new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void allowsEqualStartAndEnd() {
        LocalDate day = LocalDate.of(2026, 1, 1);
        DateRange range = new DateRange(day, day);
        assertTrue(range.containsInclusive(day));
    }

    @Test
    void containsBoundariesInclusively() {
        DateRange range = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertTrue(range.containsInclusive(LocalDate.of(2026, 1, 1)));
        assertTrue(range.containsInclusive(LocalDate.of(2026, 1, 15)));
        assertTrue(range.containsInclusive(LocalDate.of(2026, 1, 31)));
        assertFalse(range.containsInclusive(LocalDate.of(2025, 12, 31)));
        assertFalse(range.containsInclusive(LocalDate.of(2026, 2, 1)));
    }
}
