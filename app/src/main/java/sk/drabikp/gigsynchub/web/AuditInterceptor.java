package sk.drabikp.gigsynchub.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import sk.drabikp.gigsynchub.accounts.application.port.in.AuditUseCase;
import sk.drabikp.gigsynchub.accounts.domain.AuditEvent;
import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes down every change a member makes in a band through the API — a request that isn't a read, let
 * through by {@link BandAccessInterceptor}: who, the operation (the API's operation id, the controller
 * method), what it named (the address's other parts, e.g. a gig's token), how it ended. Strangers' requests
 * (404 before anything) aren't written, nor a band's own deletion (nothing is left to write it to). Never
 * fails the request.
 */
class AuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);
    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    private final AuditUseCase audit;
    private final Clock clock;

    AuditInterceptor(AuditUseCase audit, Clock clock) {
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        if (READS.contains(request.getMethod()) || !(handler instanceof HandlerMethod method)
                || !(request.getAttribute(BandAccessInterceptor.MEMBER) instanceof UserId user)) {
            return;
        }
        String action = method.getMethod().getName();
        int status = ex != null && response.getStatus() < 400 ? 500 : response.getStatus();
        if (action.equals("deleteBand") && status < 300) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Map<String, String> others = new TreeMap<>(variables == null ? Map.of() : variables);
        BandId band = BandId.of(others.remove("bandId"));
        try {
            audit.record(new AuditEvent(band, user, action, others.isEmpty() ? null : String.join(",", others.values()),
                    status, clock.instant()));
        } catch (RuntimeException e) {
            log.warn("The audit log of band {} missed {} ({})", band, action, e.getMessage());
        }
    }
}
