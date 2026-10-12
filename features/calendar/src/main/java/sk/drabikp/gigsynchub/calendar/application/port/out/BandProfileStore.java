package sk.drabikp.gigsynchub.calendar.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleOrigin;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;

/** The band profiles' rules: the shipped ones and each band's own. Each origin is replaced as a whole by whoever owns it. */
@Repository
public interface BandProfileStore {

    /** The band's rules: the shipped ones (everyone's) and the band's own, in the order they were stored. */
    List<ProfileRule> rules(BandId band);

    /** Replaces the rules shipped with the app ({@code PRESET}, everyone's) with {@code rules}. */
    void replaceShipped(List<ProfileRule> rules);

    /** Replaces the band's own rules of {@code origin} ({@code USER}, {@code LEARNED}) with {@code rules}. */
    void replace(BandId band, RuleOrigin origin, List<ProfileRule> rules);
}
