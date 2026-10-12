package sk.drabikp.gigsynchub.gig.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.gigsynchub.gig.adapter.out.persistence.RowScopedDataSource;
import sk.drabikp.gigsynchub.gig.adapter.out.persistence.ThreadRowScope;
import sk.drabikp.gigsynchub.gig.application.port.out.RowScope;
import sk.drabikp.gigsynchub.gig.domain.platform.PlatformTraits;
import sk.drabikp.gigsynchub.gig.domain.platform.Platforms;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;

/**
 * The shared kernel's beans: the time, the platforms as their adapters describe them, and which band's rows
 * the database lets the work on a thread see ({@link RowScope}; the app's data source tells it, see
 * {@link RowScopedDataSource}).
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

    @Bean
    RowScope rowScope() {
        return new ThreadRowScope();
    }

    /** The app's data source, telling the database at every checkout which band's rows may be seen. */
    @Bean
    static BeanPostProcessor rowScopedDataSource() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                return bean instanceof DataSource dataSource && !(bean instanceof RowScopedDataSource)
                        && name.equals("dataSource") ? new RowScopedDataSource(dataSource) : bean;
            }
        };
    }
}
