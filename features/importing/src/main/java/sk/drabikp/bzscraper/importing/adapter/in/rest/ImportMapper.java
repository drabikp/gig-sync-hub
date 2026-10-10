package sk.drabikp.bzscraper.importing.adapter.in.rest;

import org.mapstruct.Context;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;
import sk.drabikp.bzscraper.gig.api.RefMapper;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportDecisionRequest;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportPlanResponse;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportProposalResponse;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportResultResponse;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportStateResponse;
import sk.drabikp.bzscraper.importing.adapter.in.rest.api.ImportVersionResponse;
import sk.drabikp.bzscraper.importing.application.port.in.ImportSessionUseCase.Choice;
import sk.drabikp.bzscraper.importing.application.port.in.ImportSessionUseCase.State;
import sk.drabikp.bzscraper.importing.domain.ImportPlan;
import sk.drabikp.bzscraper.importing.domain.ImportProposal;
import sk.drabikp.bzscraper.importing.domain.ImportResult;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

/** The import as the API has it (import.yaml); a proposal is named by its index in the plan. */
@Mapper(uses = RefMapper.class)
interface ImportMapper {

    ImportStateResponse toResponse(State state);

    ImportPlanResponse toResponse(ImportPlan plan);

    @Mapping(target = "index", expression = "java(index)")
    @Mapping(target = "date", expression = "java(proposal.date())")
    @Mapping(target = "inCatalog", expression = "java(proposal.inCatalog())")
    @Mapping(target = "hasConflict", expression = "java(proposal.hasConflict())")
    @Mapping(target = "foundOn", expression = "java(proposal.copies().stream().map(c -> c.platform().id()).distinct().toList())")
    @Mapping(target = "versions", expression = "java(toVersions(proposal.versions()))")
    ImportProposalResponse toResponse(ImportProposal proposal, @Context int index);

    @Mapping(target = "title", source = "gig.title")
    @Mapping(target = "start", expression = "java(version.gig().schedule().start().toLocalDateTime().toString())")
    @Mapping(target = "end", expression = "java(version.gig().schedule().end() == null ? null "
            + ": version.gig().schedule().end().toLocalDateTime().toString())")
    @Mapping(target = "venue", source = "gig.location.venue")
    @Mapping(target = "city", source = "gig.location.city")
    @Mapping(target = "cancelled", source = "gig.cancelled")
    ImportVersionResponse toResponse(ImportProposal.Version version);

    List<ImportVersionResponse> toVersions(List<ImportProposal.Version> versions);

    ImportResultResponse toResponse(ImportResult result);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<Choice> toChoices(List<ImportDecisionRequest> decisions);

    Choice toChoice(ImportDecisionRequest decision);

    /** The plan's proposals, each with its index (how the user's choices name it). */
    default List<ImportProposalResponse> toProposals(List<ImportProposal> proposals) {
        return IntStream.range(0, proposals.size()).mapToObj(i -> toResponse(proposals.get(i), i)).toList();
    }

    /** Per platform that couldn't be read, why — by the platform's id. */
    default Map<String, String> toFailures(Map<Platform, String> failures) {
        Map<String, String> byId = new TreeMap<>();
        failures.forEach((platform, why) -> byId.put(platform.id(), why));
        return byId;
    }
}
