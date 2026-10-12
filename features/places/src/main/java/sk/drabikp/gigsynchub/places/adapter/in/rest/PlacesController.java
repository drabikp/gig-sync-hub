package sk.drabikp.gigsynchub.places.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import sk.drabikp.gigsynchub.gig.api.CountryValue;
import sk.drabikp.gigsynchub.gig.api.GigFormMapper;
import sk.drabikp.gigsynchub.places.adapter.in.rest.api.PlacesApi;
import sk.drabikp.gigsynchub.places.adapter.in.rest.api.TownResponse;
import sk.drabikp.gigsynchub.places.application.port.in.FindPlacesUseCase;

import java.util.List;

/** Serves the town picker (places.yaml). */
@RestController
class PlacesController implements PlacesApi {

    private final FindPlacesUseCase places;
    private final TownMapper towns;
    private final GigFormMapper forms;

    PlacesController(FindPlacesUseCase places, TownMapper towns, GigFormMapper forms) {
        this.places = places;
        this.towns = towns;
        this.forms = forms;
    }

    @Override
    public ResponseEntity<List<TownResponse>> searchTowns(String q) {
        return ResponseEntity.ok(towns.toResponse(places.search(q)));
    }

    @Override
    public ResponseEntity<TownResponse> resolveTown(String name, CountryValue country, String postalCode) {
        return places.resolve(name, forms.toCountry(country), postalCode)
                .map(towns::toResponse).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
