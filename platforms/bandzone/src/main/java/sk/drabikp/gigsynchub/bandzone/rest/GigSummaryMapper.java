package sk.drabikp.gigsynchub.bandzone.rest;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sk.drabikp.gigsynchub.bandzone.rest.api.GigSummaryResponse;
import sk.drabikp.gigsynchub.gig.domain.EntryType;
import sk.drabikp.gigsynchub.gig.domain.Gig;

/** A gig in the band's public list (bandzone.yaml), with its Bandzone concert when it is there. */
@Mapper
interface GigSummaryMapper {

    @Mapping(target = "bzId", source = "bzId")
    @Mapping(target = "url", source = "url")
    @Mapping(target = "title", source = "gig.title")
    @Mapping(target = "start", expression = "java(gig.schedule().start().toOffsetDateTime())")
    @Mapping(target = "end", expression = "java(gig.schedule().end() == null ? null : gig.schedule().end().toOffsetDateTime())")
    @Mapping(target = "city", source = "gig.location.city")
    @Mapping(target = "venue", expression = "java(gig.location().displayVenue())")
    @Mapping(target = "bands", source = "gig.lineup")
    @Mapping(target = "entryFee", expression = "java(entryFee(gig))")
    @Mapping(target = "isCancelled", source = "gig.cancelled")
    GigSummaryResponse toResponse(Gig gig, String bzId, String url);

    /** The price, "voluntary" or "free". */
    default String entryFee(Gig gig) {
        return gig.admission().type() == EntryType.PAID ? gig.admission().amount()
                : gig.admission().type() == EntryType.VOLUNTARY ? "voluntary" : "free";
    }
}
