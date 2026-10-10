package sk.drabikp.bzscraper.accounts.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.accounts.application.port.out.BandStore;
import sk.drabikp.bzscraper.accounts.application.port.out.MembershipStore;
import sk.drabikp.bzscraper.accounts.application.port.out.UserStore;
import sk.drabikp.bzscraper.accounts.domain.Band;
import sk.drabikp.bzscraper.accounts.domain.Membership;
import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The accounts' stores on JPA (tables {@code app_user}, {@code band}, {@code membership}); a row's
 * {@code created_at} is set when it is first saved and kept after.
 */
@Component
@Transactional
class JpaAccountsStores implements UserStore, BandStore, MembershipStore {

    private final AppUserJpaRepository users;
    private final BandJpaRepository bands;
    private final MembershipJpaRepository memberships;
    private final AccountsEntityMapper mapper;
    private final Clock clock;

    JpaAccountsStores(AppUserJpaRepository users, BandJpaRepository bands,
                      MembershipJpaRepository memberships, AccountsEntityMapper mapper, Clock clock) {
        this.users = users;
        this.bands = bands;
        this.memberships = memberships;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> bySubject(String subject) {
        return users.findByRauthySub(subject).map(mapper::toUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> byId(UserId id) {
        return users.findById(id.value()).map(mapper::toUser);
    }

    @Override
    public void save(User user) {
        AppUserEntity entity = mapper.toEntity(user);
        entity.setCreatedAt(users.findById(user.id().value()).map(AppUserEntity::getCreatedAt).orElse(clock.instant()));
        users.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Band> byId(BandId id) {
        return bands.findById(id.value()).map(mapper::toBand);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Band> bySlug(String slug) {
        return bands.findBySlug(slug).map(mapper::toBand);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Band> all() {
        return bands.findAll().stream().map(mapper::toBand).sorted(Comparator.comparing(Band::slug)).toList();
    }

    @Override
    public void save(Band band) {
        BandEntity entity = mapper.toEntity(band);
        entity.setCreatedAt(bands.findById(band.id().value()).map(BandEntity::getCreatedAt).orElse(clock.instant()));
        bands.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membership> of(UserId user, BandId band) {
        return memberships.findById(new MembershipEntity.Key(user.value(), band.value())).map(mapper::toMembership);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Membership> ofUser(UserId user) {
        return memberships.findByUserId(user.value()).stream().map(mapper::toMembership).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Membership> ofBand(BandId band) {
        return memberships.findByBandId(band.value()).stream().map(mapper::toMembership).toList();
    }

    @Override
    public void save(Membership membership) {
        MembershipEntity entity = mapper.toEntity(membership);
        entity.setCreatedAt(memberships.findById(new MembershipEntity.Key(membership.user().value(),
                        membership.band().value())).map(MembershipEntity::getCreatedAt).orElse(clock.instant()));
        memberships.save(entity);
    }
}
