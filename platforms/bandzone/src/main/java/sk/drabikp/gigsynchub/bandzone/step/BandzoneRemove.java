package sk.drabikp.gigsynchub.bandzone.step;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.portal.BandzonePortalClient;
import sk.drabikp.gigsynchub.bandzone.portal.BandzoneSession;
import sk.drabikp.gigsynchub.sync.domain.StepType;

import java.util.Optional;

/** Bandzone's remove: the concert is deleted; one already gone is done too. */
@Component
class BandzoneRemove extends BandzoneWithdrawal {

    public BandzoneRemove(BandzonePortalClient portalClient) {
        super(portalClient);
    }

    @Override
    public StepType type() {
        return StepType.REMOVE;
    }

    @Override
    Optional<String> withdraw(BandzoneSession session, String bandzoneId) throws BandzonePortalException {
        return session.deleteGig(bandzoneId) ? Optional.empty() : Optional.of("already removed from Bandzone");
    }
}
