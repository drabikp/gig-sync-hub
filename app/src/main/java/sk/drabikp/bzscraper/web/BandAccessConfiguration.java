package sk.drabikp.bzscraper.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import sk.drabikp.bzscraper.accounts.application.port.in.BandAccess;
import sk.drabikp.bzscraper.accounts.application.port.in.UsersUseCase;

/** Checks every band's address for membership ({@link BandAccessInterceptor}). */
@Configuration
class BandAccessConfiguration implements WebMvcConfigurer {

    private final UsersUseCase users;
    private final BandAccess access;

    BandAccessConfiguration(UsersUseCase users, BandAccess access) {
        this.users = users;
        this.access = access;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new BandAccessInterceptor(users, access)).addPathPatterns(BandAccessInterceptor.PATTERN);
    }
}
