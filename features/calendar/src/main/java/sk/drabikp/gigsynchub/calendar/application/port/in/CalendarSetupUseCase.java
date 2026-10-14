package sk.drabikp.gigsynchub.calendar.application.port.in;

import sk.drabikp.gigsynchub.accounts.domain.UserId;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleKind;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;

/**
 * A band's calendar set up on the page: which calendar it is (its private address — never shown again,
 * only its host — and zone) and the band's own rules for sorting its events.
 *
 * <p>Connecting and disconnecting are the band's OWNERS', as connecting a platform is: both hand the
 * server a credential of the band's that it keeps sealed and never gives back. A MANAGER — the role
 * an owner gives a lower-trust collaborator — could point the band's calendar at a feed of their own
 * and drive gigs of their choosing into the catalog, which the outbox then publishes under the band's
 * name; and could destroy the owner's address irrecoverably with one DELETE. The rules stay a
 * manager's to edit: they sort events, they are shown with every reason they matched, and they hold
 * nothing secret.
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
    Connection connect(UserId user, BandId band, String address, String zone);

    /**
     * Forgets the address; the copy read so far stays. The address is a secret only the band has,
     * and it cannot be read back out of here, so losing it means asking the calendar's owner for it
     * again — which is why this, like connecting, is the band's OWNERS'.
     */
    void disconnect(UserId user, BandId band);

    /** What kinds of rule there are (for the editor). */
    List<RuleKind> ruleKinds();

    /**
     * Replaces the band's own rules (the shipped ones stay); refused, nothing changed, when one isn't a rule
     * ({@link CalendarErrorCode#INVALID_RULE}). Answers every rule the band's profile now has.
     */
    List<ProfileRule> replaceOwnRules(BandId band, List<RuleDraft> rules);
}
