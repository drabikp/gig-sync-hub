package sk.drabikp.bzscraper.bandzone.step;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandzone.BandzonePortalException;
import sk.drabikp.bzscraper.bandzone.portal.BandzonePortalClient;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.sync.domain.StepType;

import java.util.Optional;

/** Bandzone's cancel: the concert stays listed, marked cancelled. */
@Component
class BandzoneCancel extends BandzoneWithdrawal {

    public BandzoneCancel(BandzonePortalClient portalClient) {
        super(portalClient);
    }

    @Override
    public StepType type() {
        return StepType.CANCEL;
    }

    @Override
    Optional<String> withdraw(BandzoneSession session, String bandzoneId) throws BandzonePortalException {
        session.cancelGig(bandzoneId);
        return Optional.empty();
    }
}
