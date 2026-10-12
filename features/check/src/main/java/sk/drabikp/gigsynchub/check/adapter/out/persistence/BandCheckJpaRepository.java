package sk.drabikp.gigsynchub.check.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data repository for {@link BandCheckEntity}; used only by {@link JpaCheckResultStore}. */
interface BandCheckJpaRepository extends JpaRepository<BandCheckEntity, UUID> {
}
