package sk.drabikp.gigsynchub.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import sk.drabikp.gigsynchub.gig.api.ProblemCodeValue;
import sk.drabikp.gigsynchub.gig.api.ProblemResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How much one user (or, before signing in, one address — behind the proxy, the client's) may ask of the
 * API in a minute ({@link LimitsProperties}); more is answered 429 {@code tooManyRequests} with
 * {@code Retry-After}. Counted per calendar minute, in memory: the API, the public list ({@code /gigs/}) and
 * signing in ({@code /oauth2/}, {@code /login/}); the page itself isn't.
 */
class RateLimits extends OncePerRequestFilter {

    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    /** One key's requests in one minute. */
    private record Window(long minute, int requests, int changes) {
    }

    private final LimitsProperties limits;
    private final JsonMapper json;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    RateLimits(LimitsProperties limits, JsonMapper json, Clock clock) {
        this.limits = limits;
        this.json = json;
        this.clock = clock;
    }

    /** What is counted: the API, the public list, and signing in (each start makes a session). */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/") || uri.startsWith("/gigs/") || uri.startsWith("/oauth2/")
                || uri.startsWith("/login/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long millis = clock.millis();
        long minute = millis / 60_000;
        boolean change = !READS.contains(request.getMethod());
        Window now = windows.compute(key(request), (key, before) -> {
            Window window = before == null || before.minute() != minute ? new Window(minute, 0, 0) : before;
            return new Window(minute, window.requests() + 1, window.changes() + (change ? 1 : 0));
        });
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> w.minute() < minute);
        }
        if (now.requests() > limits.requestsPerMinute() || now.changes() > limits.changesPerMinute()) {
            long seconds = 60 - (millis / 1000) % 60;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(seconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ProblemResponse problem = new ProblemResponse(ProblemCodeValue.fromValue(WebErrorCode.TOO_MANY_REQUESTS
                    .code()), Map.of("seconds", String.valueOf(seconds)), "Too many requests — try again in a minute.");
            response.getWriter().write(json.writeValueAsString(problem));
            return;
        }
        chain.doFilter(request, response);
    }

    private static String key(HttpServletRequest request) {
        Authentication user = SecurityContextHolder.getContext().getAuthentication();
        return user != null && user.isAuthenticated() && !(user instanceof AnonymousAuthenticationToken)
                ? "user:" + user.getName() : "address:" + request.getRemoteAddr();
    }
}
