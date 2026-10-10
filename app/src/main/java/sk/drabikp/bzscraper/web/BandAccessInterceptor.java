package sk.drabikp.bzscraper.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import sk.drabikp.bzscraper.accounts.application.port.in.BandAccess;
import sk.drabikp.bzscraper.accounts.application.port.in.UsersUseCase;
import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.gig.application.NotFoundException;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.Map;

/**
 * Every band's address ({@code /api/bands/{bandId}/…}) is for its members only: anyone else gets
 * the band's "not found" (404) — a band you aren't in doesn't exist for you, so its id can't be
 * probed. What a member may do in the band (an owner's actions) is the use cases' to check.
 */
class BandAccessInterceptor implements HandlerInterceptor {

    static final String PATTERN = "/api/bands/{bandId}/**";

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
            return true;                                  // not a band's address after all
        }
        BandId band = BandId.of(bandId);
        User user = users.current().orElseThrow(() -> new NotFoundException("No such band."));
        if (access.role(user.id(), band).isEmpty()) {
            throw new NotFoundException("No such band.");
        }
        return true;
    }
}
