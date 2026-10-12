package sk.drabikp.gigsynchub.gig.api;

import sk.drabikp.gigsynchub.gig.application.ErrorCode;

import java.util.Map;

/** How the API says no ({@link ProblemResponse}, gig.yaml): a feature's {@link ErrorCode} as the spec's {@link ProblemCodeValue}. */
public final class Problems {

    private Problems() {
    }

    /** IllegalArgumentException for a code the spec doesn't list (the app's tests check none is missing). */
    public static ProblemResponse of(ErrorCode code, Map<String, String> args, String message) {
        return new ProblemResponse(ProblemCodeValue.fromValue(code.code()), args, message);
    }

    public static ProblemResponse of(ErrorCode code, String message) {
        return of(code, Map.of(), message);
    }
}
