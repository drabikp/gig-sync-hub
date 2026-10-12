package sk.drabikp.gigsynchub.sync.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import sk.drabikp.gigsynchub.gig.application.port.out.AcrossBands;

import java.util.List;

/** Spring Data repository for {@link BandSettingEntity}; used only by {@link JpaSettingsStore}. */
interface BandSettingJpaRepository extends JpaRepository<BandSettingEntity, BandSettingEntity.Key> {

    @AcrossBands("the worker needs every band's pause at once, to know whose work it may not start")
    List<BandSettingEntity> findByName(String name);
}
