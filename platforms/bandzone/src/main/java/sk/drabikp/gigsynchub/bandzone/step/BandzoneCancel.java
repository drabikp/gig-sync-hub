package sk.drabikp.gigsynchub.bandzone.step;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import sk.drabikp.gigsynchub.bandzone.portal.BandzonePortalClient;
import sk.drabikp.gigsynchub.bandzone.portal.BandzoneSession;
import sk.drabikp.gigsynchub.sync.domain.StepType;

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
