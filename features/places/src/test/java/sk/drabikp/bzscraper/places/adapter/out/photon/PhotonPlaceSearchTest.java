package sk.drabikp.bzscraper.places.adapter.out.photon;

import org.junit.jupiter.api.Test;
import sk.drabikp.bzscraper.gig.domain.Coordinates;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.places.config.PlacesProperties;
import sk.drabikp.bzscraper.places.domain.Town;

import static org.assertj.core.api.Assertions.assertThat;

/** Photon's answer, shaped as seen on 2026-10-08 (q=Košice, local names, CZ/SK box). */
class PhotonPlaceSearchTest {

    private static final String KOSICE = """
            {"type":"FeatureCollection","features":[
             {"geometry":{"coordinates":[21.25,48.717],"type":"Point"},"type":"Feature","properties":{
              "countrycode":"SK","name":"Košice","county":"okres Košice I","state":"Košický kraj","postcode":"041 26",
              "type":"city","osm_value":"city"}},
             {"geometry":{"coordinates":[15.151,49.896],"type":"Point"},"type":"Feature","properties":{
              "countrycode":"CZ","name":"Košice","county":"okres Kutná Hora","state":"Středočeský kraj",
              "type":"city","osm_value":"municipality"}},
             {"geometry":{"coordinates":[15.151,49.897],"type":"Point"},"type":"Feature","properties":{
              "countrycode":"CZ","name":"Košice","county":"okres Kutná Hora","state":"Středočeský kraj",
              "type":"locality","osm_value":"locality"}},
             {"geometry":{"coordinates":[16.0,47.0],"type":"Point"},"type":"Feature","properties":{
              "countrycode":"AT","name":"Somewhere","type":"city"}}]}
            """;

    @Test
    void towns_come_with_district_region_postal_code_and_coordinates_once_each() {
        assertThat(new PhotonPlaceSearch(new PlacesProperties("http://unused")).parse(KOSICE)).containsExactly(
                new Town("Košice", "okres Košice I", "Košický kraj", "041 26", Country.SLOVAKIA, new Coordinates(48.717, 21.25)),
                new Town("Košice", "okres Kutná Hora", "Středočeský kraj", null, Country.CZECHIA, new Coordinates(49.896, 15.151)));
    }

    @Test
    void a_feature_without_its_place_or_properties_is_left_out_and_the_others_stay() {
        String answer = """
                {"features":[
                 {"type":"Feature","properties":{"countrycode":"SK","name":"Nowhere"}},
                 {"geometry":{"coordinates":[21.25,48.717]},"type":"Feature"},
                 {"geometry":{"coordinates":["x",48.7]},"properties":{"countrycode":"SK","name":"Odd"}},
                 {"geometry":{"coordinates":[17.73,49.55]},"properties":{"countrycode":"CZ","name":"Hranice",
                  "county":"okres Přerov"}}]}
                """;

        assertThat(new PhotonPlaceSearch(new PlacesProperties("http://unused")).parse(answer)).extracting(Town::name)
                .containsExactly("Hranice");
    }

    @Test
    void an_unreachable_search_gives_no_suggestions() {
        assertThat(new PhotonPlaceSearch(new PlacesProperties("http://127.0.0.1:9/")).towns("Košice")).isEmpty();
    }
}
