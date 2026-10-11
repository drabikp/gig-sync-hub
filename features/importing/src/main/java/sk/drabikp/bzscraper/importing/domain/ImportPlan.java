package sk.drabikp.bzscraper.importing.domain;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What an import would do, for the user to review before applying.
 *
 * @param proposals     gigs to add or link, newest first
 * @param alreadyLinked platform gigs already linked to the catalog (nothing to do)
 * @param skipped       platform gigs left out, each with the reason
 * @param failures      platforms that could not be read, with the reason
 */
@ValueObject
public record ImportPlan(List<ImportProposal> proposals, int alreadyLinked, List<String> skipped,
                         Map<Platform, String> failures) {

    public ImportPlan {
        proposals = List.copyOf(proposals);
        skipped = List.copyOf(skipped);
        failures = Collections.unmodifiableMap(new TreeMap<>(failures));     // in the platforms' order
    }
}
