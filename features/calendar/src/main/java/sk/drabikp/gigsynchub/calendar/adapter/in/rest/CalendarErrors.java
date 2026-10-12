package sk.drabikp.gigsynchub.calendar.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sk.drabikp.gigsynchub.calendar.application.port.in.CalendarErrorCode;
import sk.drabikp.gigsynchub.calendar.application.port.out.CalendarUnavailableException;
import sk.drabikp.gigsynchub.gig.api.ProblemResponse;
import sk.drabikp.gigsynchub.gig.api.Problems;

/** The calendar couldn't be read (unreachable, not a calendar): 502, code {@code calendarUnavailable}. */
@RestControllerAdvice(assignableTypes = CalendarController.class)
class CalendarErrors {

    @ExceptionHandler(CalendarUnavailableException.class)
    ResponseEntity<ProblemResponse> unavailable(CalendarUnavailableException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Problems.of(CalendarErrorCode.CALENDAR_UNAVAILABLE, e.getMessage()));
    }
}
