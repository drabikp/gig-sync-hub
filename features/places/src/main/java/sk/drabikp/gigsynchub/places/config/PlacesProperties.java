package sk.drabikp.gigsynchub.places.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code gigsynchub.places.*}: the Photon place search to ask ({@code photon-url}). */
@ConfigurationProperties("gigsynchub.places")
public record PlacesProperties(String photonUrl) {

    public PlacesProperties {
        photonUrl = photonUrl == null || photonUrl.isBlank() ? "https://photon.komoot.io/api/" : photonUrl;
    }
}
