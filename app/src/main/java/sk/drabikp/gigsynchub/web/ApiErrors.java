package sk.drabikp.gigsynchub.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;
import sk.drabikp.gigsynchub.gig.api.ProblemResponse;
import sk.drabikp.gigsynchub.gig.api.Problems;
import sk.drabikp.gigsynchub.gig.application.BadRequestException;
import sk.drabikp.gigsynchub.gig.application.ErrorCode;
import sk.drabikp.gigsynchub.gig.application.GigErrorCode;
import sk.drabikp.gigsynchub.gig.application.NotFoundException;
import sk.drabikp.gigsynchub.gig.application.UserFacingException;
import sk.drabikp.gigsynchub.gig.domain.UnknownReferenceException;


/**
 * How the API says no: a {@link ProblemResponse} ({@code {code, args, message}}, gig.yaml). A rule that
 * refused ({@link UserFacingException}) is a 409 with its code and arguments (the page says it in
 * the user's language, the message is the English fallback); something not there is a 404; a
 * request that makes no sense is a 400 ({@link BadRequestException}, a
 * reference that names nothing); anything else — a stray {@link IllegalArgumentException} too — is a fault —
 * logged in full, the page gets a short line.
 */
@RestControllerAdvice
class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(UserFacingException.class)
    ResponseEntity<ProblemResponse> refused(UserFacingException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Problems.of(e.code(), e.args(), e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemResponse> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Problems.of(GigErrorCode.NOT_FOUND, e.getMessage()));
    }


    /**
     * What the request itself got wrong: a check of its input, a reference that names nothing, a body
     * or parameter that can't be read. Any other {@link IllegalArgumentException} is the app's own
     * fault ({@link #fault}), logged in full.
     */
    @ExceptionHandler({BadRequestException.class, UnknownReferenceException.class,
            HttpMessageNotReadableException.class, TypeMismatchException.class})
    ResponseEntity<ProblemResponse> badRequest(Exception e) {
        log.debug("Bad request", e);
        return ResponseEntity.badRequest().body(Problems.of(GigErrorCode.BAD_REQUEST, e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemResponse> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Problems.of(byStatus(e.getStatusCode().value()), e.getReason()));
    }

    /** The page went away mid-answer (a phone closing the live updates): nobody to tell. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void gone(AsyncRequestNotUsableException e) {
        log.debug("The page went away", e);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemResponse> fault(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;                                      // Spring Security answers these itself
        }
        if (e instanceof ErrorResponse response) {          // Spring's own: not found, wrong method, …
            int status = response.getStatusCode().value();
            return ResponseEntity.status(status).body(Problems.of(byStatus(status), response.getBody().getDetail()));
        }
        log.error("An API call failed", e);
        return ResponseEntity.internalServerError().body(Problems.of(WebErrorCode.UNEXPECTED,
                "Something went wrong — nothing may have changed. Details are in the log."));
    }

    /** Spring's own answers by their status: not found, the request's fault, or the app's. */
    private static ErrorCode byStatus(int status) {
        return status == 404 ? GigErrorCode.NOT_FOUND : status < 500 ? GigErrorCode.BAD_REQUEST : WebErrorCode.UNEXPECTED;
    }
}
