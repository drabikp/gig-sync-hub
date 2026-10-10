package sk.drabikp.bzscraper.calendar.application.port.in;

import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleKind;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;

/**
 * A band's calendar set up on the page: which calendar it is (its private address — never shown again,
 * only its host — and zone) and the band's own rules for sorting its events.
 */
public interface CalendarSetupUseCase {

    /** The band's calendar as the page sees it: connected or not, whose ({@code host}), its zone. */
    record Connection(boolean connected, String host, String zone) {
    }

    /** One of the band's own rules as the page sends it. */
    record RuleDraft(String kind, String value, int weight, boolean enabled) {
    }

    Connection connection(BandId band);

    /**
     * Reads the band's calendar from {@code address} from now on, its times without a zone in {@code zone}
     * (blank: the server's default). Refused when either isn't one ({@link CalendarErrorCode#INVALID_CALENDAR}).
     */
    Connection connect(BandId band, String address, String zone);

    /** Forgets the address; the copy read so far stays. */
    void disconnect(BandId band);

    /** What kinds of rule there are (for the editor). */
    List<RuleKind> ruleKinds();

    /**
     * Replaces the band's own rules (the shipped ones stay); refused, nothing changed, when one isn't a rule
     * ({@link CalendarErrorCode#INVALID_RULE}). Answers every rule the band's profile now has.
     */
    List<ProfileRule> replaceOwnRules(BandId band, List<RuleDraft> rules);
}
