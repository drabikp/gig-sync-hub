package sk.drabikp.bzscraper.sync.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for {@link BandSettingEntity}; used only by {@link JpaSettingsStore}. */
interface BandSettingJpaRepository extends JpaRepository<BandSettingEntity, BandSettingEntity.Key> {

    List<BandSettingEntity> findByName(String name);
}
