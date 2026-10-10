package sk.drabikp.bzscraper.browser;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The server's browser pool, shared by every platform's browsers. */
@Configuration
public class BrowserConfiguration {

    @Bean
    BrowserPool browserPool(BrowserProperties properties) {
        return new BrowserPool(properties.max());
    }
}
