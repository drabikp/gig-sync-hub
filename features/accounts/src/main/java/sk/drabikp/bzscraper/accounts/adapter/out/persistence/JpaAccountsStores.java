package sk.drabikp.bzscraper.accounts.adapter.out.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.accounts.application.port.out.AuditStore;
import sk.drabikp.bzscraper.accounts.application.port.out.BandStore;
import sk.drabikp.bzscraper.accounts.application.port.out.InvitationStore;
import sk.drabikp.bzscraper.accounts.application.port.out.MembershipStore;
import sk.drabikp.bzscraper.accounts.application.port.out.UserStore;
import sk.drabikp.bzscraper.accounts.domain.AuditEvent;
import sk.drabikp.bzscraper.accounts.domain.Band;
import sk.drabikp.bzscraper.accounts.domain.Invitation;
import sk.drabikp.bzscraper.accounts.domain.Membership;
import sk.drabikp.bzscraper.accounts.domain.User;
import sk.drabikp.bzscraper.accounts.domain.UserId;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The accounts' stores on JPA (tables {@code app_user}, {@code band}, {@code membership}, {@code invitation},
 * {@code audit_event}); a row's {@code created_at} is set when it is first saved and kept after.
 */
@Component
@Transactional(readOnly = true)
class JpaAccountsStores implements UserStore, BandStore, MembershipStore, InvitationStore, AuditStore {

    private final AppUserJpaRepository users;
    private final BandJpaRepository bands;
    private final MembershipJpaRepository memberships;
    private final InvitationJpaRepository invitations;
    private final AuditEventJpaRepository audit;
    private final AccountsEntityMapper mapper;
    private final Clock clock;

    JpaAccountsStores(AppUserJpaRepository users, BandJpaRepository bands, MembershipJpaRepository memberships,
                      InvitationJpaRepository invitations, AuditEventJpaRepository audit,
                      AccountsEntityMapper mapper, Clock clock) {
        this.users = users;
        this.bands = bands;
        this.memberships = memberships;
        this.invitations = invitations;
        this.audit = audit;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public Optional<User> bySubject(String subject) {
        return users.findByRauthySub(subject).map(mapper::toUser);
    }

    @Override
    public Optional<User> byId(UserId id) {
        return users.findById(id.value()).map(mapper::toUser);
    }

    @Override
    @Transactional
    public void save(User user) {
        AppUserEntity entity = mapper.toEntity(user);
        entity.setCreatedAt(users.findById(user.id().value()).map(AppUserEntity::getCreatedAt).orElse(clock.instant()));
        users.save(entity);
    }

    @Override
    @Transactional
    public void delete(UserId user) {
        users.deleteById(user.value());
        users.flush();
    }

    @Override
    public Optional<Band> byId(BandId id) {
        return bands.findById(id.value()).map(mapper::toBand);
    }

    @Override
    public Optional<Band> bySlug(String slug) {
        return bands.findBySlug(slug).map(mapper::toBand);
    }

    @Override
    public List<Band> all() {
        return bands.findAll().stream().map(mapper::toBand).sorted(Comparator.comparing(Band::slug)).toList();
    }

    @Override
    @Transactional
    public void save(Band band) {
        BandEntity entity = mapper.toEntity(band);
        entity.setCreatedAt(bands.findById(band.id().value()).map(BandEntity::getCreatedAt).orElse(clock.instant()));
        bands.save(entity);
    }

    @Override
    @Transactional
    public void delete(BandId band) {
        bands.deleteById(band.value());
        bands.flush();
    }

    @Override
    public Optional<Membership> of(UserId user, BandId band) {
        return memberships.findById(new MembershipEntity.Key(user.value(), band.value())).map(mapper::toMembership);
    }

    @Override
    public List<Membership> ofUser(UserId user) {
        return memberships.findByUserId(user.value()).stream().map(mapper::toMembership).toList();
    }

    @Override
    public List<Membership> ofBand(BandId band) {
        return memberships.findByBandId(band.value()).stream().map(mapper::toMembership).toList();
    }

    @Override
    @Transactional
    public void save(Membership membership) {
        MembershipEntity entity = mapper.toEntity(membership);
        entity.setCreatedAt(memberships.findById(new MembershipEntity.Key(membership.user().value(),
                        membership.band().value())).map(MembershipEntity::getCreatedAt).orElse(clock.instant()));
        memberships.save(entity);
    }

    @Override
    @Transactional
    public void delete(UserId user, BandId band) {
        memberships.deleteById(new MembershipEntity.Key(user.value(), band.value()));
    }

    @Override
    @Transactional
    public void save(Invitation invitation, String tokenHash) {
        InvitationEntity entity = mapper.toEntity(invitation);
        entity.setTokenHash(tokenHash);
        invitations.save(entity);
    }

    @Override
    @Transactional
    public void save(Invitation invitation) {
        InvitationEntity entity = invitations.findById(invitation.id()).orElseThrow();
        entity.setAcceptedAt(invitation.acceptedAt());
        invitations.save(entity);
    }

    @Override
    public Optional<Invitation> byTokenHash(String tokenHash) {
        return invitations.findByTokenHash(tokenHash).map(mapper::toInvitation);
    }

    @Override
    public Optional<Invitation> of(BandId band, UUID id) {
        return invitations.findByBandIdAndId(band.value(), id).map(mapper::toInvitation);
    }

    @Override
    public List<Invitation> open(BandId band, Instant now) {
        return invitations.findByBandIdAndAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(band.value(), now)
                .stream().map(mapper::toInvitation).toList();
    }

    @Override
    @Transactional
    public void delete(UUID invitation) {
        invitations.deleteById(invitation);
    }

    @Override
    @Transactional
    public void add(AuditEvent event) {
        audit.save(mapper.toEntity(event));
    }

    @Override
    public List<AuditEvent> recent(BandId band, int limit) {
        return audit.findByBandIdOrderByAtDescIdDesc(band.value(), PageRequest.of(0, limit)).stream()
                .map(mapper::toEvent).toList();
    }
}
