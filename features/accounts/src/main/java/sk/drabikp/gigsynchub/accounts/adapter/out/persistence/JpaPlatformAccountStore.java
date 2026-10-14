package sk.drabikp.gigsynchub.accounts.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import sk.drabikp.gigsynchub.accounts.application.port.in.SecretBox;
import sk.drabikp.gigsynchub.accounts.application.port.out.PlatformAccountStore;
import sk.drabikp.gigsynchub.accounts.domain.AccountCheck;
import sk.drabikp.gigsynchub.accounts.domain.PlatformAccount;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * {@link PlatformAccountStore} on JPA (table {@code band_platform_account}): the settings as JSON, the
 * secrets as JSON sealed by the {@link SecretBox} for the row ({@code platform-account:<band>:<platform>}),
 * so a ciphertext copied to another band's row doesn't open. Mapped by hand, not by MapStruct: a row is
 * sealed and opened, not copied.
 */
@Component
@Transactional(readOnly = true)
class JpaPlatformAccountStore implements PlatformAccountStore {

    private static final TypeReference<TreeMap<String, String>> MAP = new TypeReference<>() {
    };

    /** How many rows a rotation holds at once — a page, not a table. */
    private static final int RESEAL_BATCH = 200;

    private final PlatformAccountJpaRepository accounts;
    private final SecretBox box;
    private final Clock clock;
    private final int resealBatch;
    private final TransactionTemplate transactions;
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    JpaPlatformAccountStore(PlatformAccountJpaRepository accounts, SecretBox box, Clock clock,
                            PlatformTransactionManager transactionManager) {
        this(accounts, box, clock, transactionManager, RESEAL_BATCH);
    }

    /** The page size is the test's to set: proving a walk takes more rows than one page. */
    JpaPlatformAccountStore(PlatformAccountJpaRepository accounts, SecretBox box, Clock clock,
                            PlatformTransactionManager transactionManager, int resealBatch) {
        this.accounts = accounts;
        this.box = box;
        this.clock = clock;
        this.resealBatch = resealBatch;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public Optional<PlatformAccount> of(BandId band, Platform platform) {
        return accounts.findById(new PlatformAccountEntity.Key(band.value(), platform.id())).map(this::toAccount);
    }

    @Override
    public List<PlatformAccount> ofBand(BandId band) {
        return accounts.findByBandIdOrderByPlatform(band.value()).stream().map(this::toAccount).toList();
    }

    @Override
    @Transactional
    public void save(PlatformAccount account) {
        PlatformAccountEntity entity = new PlatformAccountEntity();
        entity.setBandId(account.band().value());
        entity.setPlatform(account.platform().id());
        entity.setSettings(json.writeValueAsString(new TreeMap<>(account.settings())));
        if (!account.secrets().isEmpty()) {
            SecretBox.Sealed sealed = box.seal(json.writeValueAsString(new TreeMap<>(account.secrets())),
                    context(account.band(), account.platform()));
            entity.setSecrets(sealed.data());
            entity.setKeyId(sealed.keyId());
        }
        account.check().ifPresent(check -> {
            entity.setCheckedAt(check.at());
            entity.setCheckOk(check.ok());
            entity.setCheckMessage(check.message() == null ? null : abbreviate(check.message()));
        });
        entity.setUpdatedAt(clock.instant());
        accounts.save(entity);
    }

    @Override
    @Transactional
    public void delete(BandId band, Platform platform) {
        accounts.deleteById(new PlatformAccountEntity.Key(band.value(), platform.id()));
    }

    /**
     * Seals again, a page at a time, whatever an older key sealed.
     *
     * <p>Not one transaction, deliberately. This runs while the app starts and it is about EVERY
     * band's row, so one transaction means holding the whole table — sealed blobs and all — in the
     * heap before anything is written. A page is saved row by row and each save stands on its own,
     * and that is safe here because a half-rotated table is a perfectly good table: every row says
     * which key sealed it, every key the app knows still opens what it sealed, and this runs again
     * at the next start. The pages walk in key order, and a row that has been sealed again still
     * matches, so nothing is skipped by resealing it.
     *
     * <p>{@code NOT_SUPPORTED} and not "no annotation": the class is {@code readOnly = true}, which
     * a method without one inherits — and a write inside a read-only transaction is not a write.
     *
     * <p>And each row is saved through {@code transactions}, not by calling this class's own
     * {@link #save} — which would be a call on {@code this}, past the proxy, so the
     * {@code @Transactional} on it would mean nothing and the write would ride on whatever
     * transaction happened to be open. It worked only because Spring Data's own {@code save} opens
     * one; that is a fact about a library, not a decision, and it is the kind that holds until it
     * does not. The template says what is meant.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int reseal() {
        int resealed = 0;
        for (int page = 0; ; page++) {
            List<PlatformAccountEntity> batch = accounts.findBySecretsIsNotNullOrderByBandIdAscPlatformAsc(
                    PageRequest.of(page, resealBatch));
            if (batch.isEmpty()) {
                return resealed;
            }
            for (PlatformAccountEntity entity : batch) {
                if (!box.current(new SecretBox.Sealed(entity.getKeyId(), entity.getSecrets()))) {
                    PlatformAccount opened = toAccount(entity);
                    transactions.executeWithoutResult(status -> save(opened));
                    resealed++;
                }
            }
        }
    }

    private PlatformAccount toAccount(PlatformAccountEntity entity) {
        BandId band = new BandId(entity.getBandId());
        Platform platform = Platform.of(entity.getPlatform());
        Map<String, String> secrets = entity.getSecrets() == null ? Map.of()
                : json.readValue(box.open(new SecretBox.Sealed(entity.getKeyId(), entity.getSecrets()),
                        context(band, platform)), MAP);
        AccountCheck check = entity.getCheckedAt() == null ? null
                : new AccountCheck(Boolean.TRUE.equals(entity.getCheckOk()), entity.getCheckMessage(),
                        entity.getCheckedAt());
        return new PlatformAccount(band, platform, json.readValue(entity.getSettings(), MAP), secrets, check);
    }

    private static String context(BandId band, Platform platform) {
        return "platform-account:" + band + ":" + platform.id();
    }

    private static String abbreviate(String message) {
        return message.length() <= 1000 ? message : message.substring(0, 999) + "…";
    }
}
