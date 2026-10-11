package sk.drabikp.bzscraper.importing.domain;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The user's answer for one {@link ImportProposal}.
 *
 * @param include whether to import this proposal at all
 * @param sameGig for a suggested match: true = one gig, false = import each copy on its own
 *                (ignored for proposals that are not suggestions)
 * @param chosen  the version whose details the catalog gig takes
 */
@ValueObject
public record ImportDecision(ImportProposal proposal, boolean include, boolean sameGig,
                             ImportProposal.Version chosen) {
}
