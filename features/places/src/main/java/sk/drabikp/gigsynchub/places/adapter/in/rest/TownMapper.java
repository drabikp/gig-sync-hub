package sk.drabikp.gigsynchub.places.adapter.in.rest;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.gigsynchub.gig.api.GigFormMapper;
import sk.drabikp.gigsynchub.places.adapter.in.rest.api.TownResponse;
import sk.drabikp.gigsynchub.places.domain.Town;

import java.util.List;

/** Towns as the API sends them (places.yaml). */
@Mapper(uses = GigFormMapper.class)
interface TownMapper {

    @Mapping(target = "latitude", source = "coordinates.latitude")
    @Mapping(target = "longitude", source = "coordinates.longitude")
    TownResponse toResponse(Town town);

    List<TownResponse> toResponse(List<Town> towns);
}
