package sk.drabikp.gigsynchub.gig.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.gigsynchub.gig.domain.platform.PlatformTraits;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;

/**
 * The shared kernel's beans: the time, and the platforms as their adapters describe them.
 */
@Configuration
class GigConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    Platforms platforms(List<PlatformTraits> traits) {
        return new Platforms(traits);
    }

}
