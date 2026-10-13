package sk.drabikp.gigsynchub.accounts.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.accounts.domain.Invitation;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The bands' invitations; a link's token is kept only as its hash. */
@Repository
public interface InvitationStore {

    void save(Invitation invitation, String tokenHash);

    /** Updates an invitation (accepted). */
    void save(Invitation invitation);

    Optional<Invitation> byTokenHash(String tokenHash);

    Optional<Invitation> of(BandId band, UUID id);

    /** The band's invitations still open at {@code now}, newest first. */
    List<Invitation> open(BandId band, Instant now);

    void delete(UUID id);

    /**
     * Forgets invitations that ran out before {@code when}; answers how many went. An invitation is
     * good for seven days and holds an e-mail address: once it is long over it is a row with
     * somebody's address in it and no purpose. Whoever accepted one is recorded by their membership,
     * not by this.
     */
    int forgetExpiredBefore(Instant when);
}
