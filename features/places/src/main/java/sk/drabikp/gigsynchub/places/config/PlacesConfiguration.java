package sk.drabikp.gigsynchub.places.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sk.drabikp.gigsynchub.places.application.PlaceService;
import sk.drabikp.gigsynchub.places.application.port.out.PlaceSearch;

@Configuration
class PlacesConfiguration {

    @Bean
    PlaceService placeService(PlaceSearch placeSearch) {
        return new PlaceService(placeSearch);
    }
}
