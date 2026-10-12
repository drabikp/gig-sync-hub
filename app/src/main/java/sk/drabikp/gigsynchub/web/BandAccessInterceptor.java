package sk.drabikp.gigsynchub.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import sk.drabikp.gigsynchub.accounts.application.port.in.BandAccess;
import sk.drabikp.gigsynchub.accounts.application.port.in.UsersUseCase;
import sk.drabikp.gigsynchub.accounts.domain.User;
import sk.drabikp.gigsynchub.gig.application.NotFoundException;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.Map;

/**
 * Every band's address ({@code /api/bands/{bandId}/…}) is for its members only: anyone else gets
 * the band's "not found" (404) — a band you aren't in doesn't exist for you, so its id can't be
 * probed. What a member may do in the band (an owner's actions) is the use cases' to check. A member's
 * request reads and writes its band's rows because every query over a band's table names the band —
 * which {@code BandScopedQueriesTest} enumerates the repositories to insist on, rather than the
 * database being told whose rows to show (row-level security, dropped in V12).
 */
class BandAccessInterceptor implements HandlerInterceptor {

    static final String PATTERN = "/api/bands/{bandId}/**";
    /** The request attribute naming the member the request was let through for (see {@link AuditInterceptor}). */
    static final String MEMBER = BandAccessInterceptor.class.getName() + ".member";

    private final UsersUseCase users;
    private final BandAccess access;

    BandAccessInterceptor(UsersUseCase users, BandAccess access) {
        this.users = users;
        this.access = access;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String bandId = variables == null ? null : variables.get("bandId");
        if (bandId == null) {
            throw new NotFoundException("No such band.");    // a band's address names its band — fail closed
        }
        BandId band = BandId.of(bandId);
        User user = users.current().orElseThrow(() -> new NotFoundException("No such band."));
        if (access.role(user.id(), band).isEmpty()) {
            throw new NotFoundException("No such band.");
        }
        request.setAttribute(MEMBER, user.id());
        return true;
    }
}
