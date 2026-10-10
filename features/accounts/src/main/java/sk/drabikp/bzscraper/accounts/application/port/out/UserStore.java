package sk.drabikp.bzscraper.accounts.application.port.out;

import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.accounts.domain.UserId;

import java.util.Optional;

public interface UserStore {

    Optional<User> bySubject(String subject);

    Optional<User> byId(UserId id);

    void save(User user);
}
