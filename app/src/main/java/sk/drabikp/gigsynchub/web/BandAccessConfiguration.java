package sk.drabikp.gigsynchub.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import sk.drabikp.gigsynchub.accounts.application.port.in.AuditUseCase;
import sk.drabikp.gigsynchub.accounts.application.port.in.BandAccess;
import sk.drabikp.gigsynchub.accounts.application.port.in.UsersUseCase;
import sk.drabikp.gigsynchub.gig.application.port.out.RowScope;

import java.time.Clock;

/**
 * Checks every band's address for membership ({@link BandAccessInterceptor}), and writes down the changes
 * its members make there ({@link AuditInterceptor}).
 */
@Configuration
class BandAccessConfiguration implements WebMvcConfigurer {

    private final UsersUseCase users;
    private final BandAccess access;
    private final AuditUseCase audit;
    private final Clock clock;
    private final RowScope rows;

    BandAccessConfiguration(UsersUseCase users, BandAccess access, AuditUseCase audit, Clock clock, RowScope rows) {
        this.users = users;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.rows = rows;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new BandAccessInterceptor(users, access, rows)).addPathPatterns(BandAccessInterceptor.PATTERN);
        registry.addInterceptor(new AuditInterceptor(audit, clock)).addPathPatterns(BandAccessInterceptor.PATTERN);
    }
}
