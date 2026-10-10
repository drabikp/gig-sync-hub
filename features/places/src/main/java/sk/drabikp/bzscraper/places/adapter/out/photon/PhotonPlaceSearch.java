package sk.drabikp.bzscraper.places.adapter.out.photon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.gig.domain.Coordinates;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.places.application.port.out.PlaceSearch;
import sk.drabikp.bzscraper.places.config.PlacesProperties;
import sk.drabikp.bzscraper.places.domain.Town;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link PlaceSearch} on OpenStreetMap's Photon geocoder (photon.komoot.io — free, no key;
 * fair use: a person typing in a form is far below its limits). Towns and town parts only,
 * inside a box around Czechia and Slovakia, names as written locally. Photon's
 * {@code county} is the district as Czech listings write it ("okres Přerov").
 */
@Component
class PhotonPlaceSearch implements PlaceSearch {

    private static final Logger logger = LoggerFactory.getLogger(PhotonPlaceSearch.class);
    /** min lon, min lat, max lon, max lat — Czechia + Slovakia. */
    private static final String CZ_SK_BOX = "12.09,47.73,22.57,51.06";
    private static final int LIMIT = 8;

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public PhotonPlaceSearch(PlacesProperties properties) {
        this.baseUrl = properties.photonUrl();
    }

    @Override
    public List<Town> towns(String text) {
        if (text == null || text.strip().length() < 2) {
            return List.of();
        }
        String url = baseUrl + "?q=" + URLEncoder.encode(text.strip(), StandardCharsets.UTF_8) + "&limit=" + LIMIT
                + "&bbox=" + CZ_SK_BOX + "&layer=city&layer=locality";
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "bzscraper-gig-sync/1.0 (a band's gig tool)")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (IOException e) {
            logger.warn("Place search failed: {}", e.toString());
            logger.debug("Place search failed", e);
            return List.of();
        }
        if (response.statusCode() != 200) {
            logger.warn("Place search answered HTTP {}", response.statusCode());
            return List.of();
        }
        try {
            return parse(response.body());
        } catch (JacksonException e) {
            logger.warn("Place search answered something that isn't its JSON: {}", e.getOriginalMessage());
            return List.of();
        }
    }

    /** The towns of Photon's answer, one per name and district; a feature that isn't a usable town is left out. */
    List<Town> parse(String body) {
        Map<String, Town> towns = new LinkedHashMap<>();
        for (JsonNode feature : json.readTree(body).path("features")) {
            town(feature).ifPresent(town -> towns.putIfAbsent(town.name() + "|" + town.district(), town));
        }
        return new ArrayList<>(towns.values());
    }

    private static Optional<Town> town(JsonNode feature) {
        JsonNode p = feature.path("properties");
        JsonNode xy = feature.path("geometry").path("coordinates");
        Optional<Country> country = Country.named(p.path("countrycode").asString(""));
        String name = text(p, "name");
        if (country.isEmpty() || name == null || !xy.path(0).isNumber() || !xy.path(1).isNumber()) {
            return Optional.empty();
        }
        return Optional.of(new Town(name, text(p, "county"), text(p, "state"), text(p, "postcode"), country.get(),
                new Coordinates(xy.path(1).asDouble(), xy.path(0).asDouble())));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : null;
    }
}
