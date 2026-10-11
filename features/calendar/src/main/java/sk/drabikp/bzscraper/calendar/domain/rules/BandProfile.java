package sk.drabikp.bzscraper.calendar.domain.rules;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.List;

/**
 * How one band writes its calendar: the rules (data, per band) and the score thresholds.
 * An event scoring at least {@code gigScore} is a gig, at most {@code notGigScore} not a
 * gig, anything between is for the user to decide. A rule weighing {@code strongNegative}
 * or less is strong evidence against a gig (a rehearsal, travel, a member's absence).
 */
@ValueObject
public record BandProfile(List<ProfileRule> rules, Thresholds thresholds) {

    public record Thresholds(int gigScore, int notGigScore, int strongNegative) {

        public Thresholds {
            if (gigScore <= notGigScore) {
                throw new IllegalArgumentException("gig score must be above the not-a-gig score");
            }
        }
    }

    public BandProfile {
        rules = List.copyOf(rules);
    }

    public List<ProfileRule> enabled(RuleKind kind) {
        return rules.stream().filter(r -> r.enabled() && r.kind() == kind).toList();
    }
}
