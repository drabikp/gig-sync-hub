package sk.drabikp.bzscraper.sync.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.sync.application.port.out.SettingsStore;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** JPA-backed {@link SettingsStore}: table {@code band_setting}. */
@Component
@Transactional
class JpaSettingsStore implements SettingsStore {

    private final BandSettingJpaRepository jpaRepository;

    public JpaSettingsStore(BandSettingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> get(BandId band, String name) {
        return jpaRepository.findById(new BandSettingEntity.Key(band.value(), name)).map(BandSettingEntity::getValue);
    }

    @Override
    public void put(BandId band, String name, String value) {
        if (value == null) {
            jpaRepository.deleteById(new BandSettingEntity.Key(band.value(), name));
        } else {
            jpaRepository.save(new BandSettingEntity(band.value(), name, value));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<BandId, String> all(String name) {
        return jpaRepository.findByName(name).stream()
                .collect(Collectors.toMap(s -> new BandId(s.getBandId()), BandSettingEntity::getValue));
    }
}
