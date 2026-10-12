package sk.drabikp.gigsynchub.check.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.gigsynchub.check.adapter.out.persistence.CheckEntityMapper.StoredCheck;
import sk.drabikp.gigsynchub.check.application.port.out.CheckResultStore;
import sk.drabikp.gigsynchub.check.domain.PlatformCheck;
import sk.drabikp.gigsynchub.gig.domain.BandId;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/** {@link CheckResultStore} on JPA (table {@code band_check}): the check as JSON, one row per band. */
@Component
@Transactional(readOnly = true)
class JpaCheckResultStore implements CheckResultStore {

    private final BandCheckJpaRepository checks;
    private final CheckEntityMapper mapper;
    private final JsonMapper json = JsonMapper.builder().build();

    JpaCheckResultStore(BandCheckJpaRepository checks, CheckEntityMapper mapper) {
        this.checks = checks;
        this.mapper = mapper;
    }

    @Override
    public Optional<PlatformCheck> of(BandId band) {
        return checks.findById(band.value())
                .map(entity -> mapper.toCheck(json.readValue(entity.getResult(), StoredCheck.class)));
    }

    @Override
    @Transactional
    public void save(BandId band, PlatformCheck check) {
        BandCheckEntity entity = new BandCheckEntity();
        entity.setBandId(band.value());
        entity.setResult(json.writeValueAsString(mapper.toStored(check)));
        entity.setCheckedAt(check.checkedAt());
        checks.save(entity);
    }
}
