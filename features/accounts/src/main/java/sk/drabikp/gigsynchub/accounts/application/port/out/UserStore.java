package sk.drabikp.gigsynchub.accounts.application.port.out;

import org.jmolecules.ddd.annotation.Repository;
import sk.drabikp.gigsynchub.accounts.domain.User;
import sk.drabikp.gigsynchub.accounts.domain.UserId;

import java.util.Optional;

@Repository
public interface UserStore {

    Optional<User> bySubject(String subject);

    Optional<User> byId(UserId id);

    void save(User user);

    /** Deletes the user with their memberships (invitations and audit events keep no reference to them). */
    void delete(UserId user);
}
