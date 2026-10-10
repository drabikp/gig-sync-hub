package sk.drabikp.bzscraper.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import sk.drabikp.bzscraper.gig.application.port.out.RowScope;

import java.io.IOException;

/**
 * A request sees no band's rows in the database (row-level security) — until {@link BandAccessInterceptor}
 * has let a band's member through to that band's address, or a public address opened its band. Whatever
 * was opened on the way is closed with the request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RowScopeFilter extends OncePerRequestFilter {

    private final RowScope rows;

    RowScopeFilter(RowScope rows) {
        this.rows = rows;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try (RowScope.Scope ignored = rows.nothing()) {
            chain.doFilter(request, response);
        }
    }
}
