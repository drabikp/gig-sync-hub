package sk.drabikp.bzscraper.accounts.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.accounts.application.port.in.SecretBox;
import sk.drabikp.bzscraper.accounts.application.port.out.PlatformAccountStore;
import sk.drabikp.bzscraper.accounts.domain.AccountCheck;
import sk.drabikp.bzscraper.accounts.domain.PlatformAccount;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
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

    private final PlatformAccountJpaRepository accounts;
    private final SecretBox box;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    JpaPlatformAccountStore(PlatformAccountJpaRepository accounts, SecretBox box, Clock clock) {
        this.accounts = accounts;
        this.box = box;
        this.clock = clock;
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

    @Override
    @Transactional
    public int reseal() {
        List<PlatformAccountEntity> old = accounts.findBySecretsIsNotNull().stream()
                .filter(entity -> !box.current(new SecretBox.Sealed(entity.getKeyId(), entity.getSecrets())))
                .toList();
        old.forEach(entity -> save(toAccount(entity)));
        return old.size();
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
