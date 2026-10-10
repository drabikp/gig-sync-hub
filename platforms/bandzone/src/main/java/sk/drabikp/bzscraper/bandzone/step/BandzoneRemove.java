package sk.drabikp.bzscraper.bandzone.step;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.bandzone.portal.BandzonePortalClient;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.sync.domain.StepType;

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
