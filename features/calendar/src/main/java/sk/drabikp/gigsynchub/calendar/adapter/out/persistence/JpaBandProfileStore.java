package sk.drabikp.gigsynchub.calendar.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.gigsynchub.calendar.application.port.out.BandProfileStore;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleOrigin;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.util.List;

/** JPA-backed {@link BandProfileStore}: rules in table {@code calendar_rule} (the shipped ones without a band), in insertion order. */
@Component
@Transactional(readOnly = true)
class JpaBandProfileStore implements BandProfileStore {

    private final CalendarRuleJpaRepository jpaRepository;
    private final CalendarEntityMapper mapper;

    public JpaBandProfileStore(CalendarRuleJpaRepository jpaRepository, CalendarEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<ProfileRule> rules(BandId band) {
        return mapper.toRules(jpaRepository.forBand(band.value()));
    }

    @Override
    @Transactional
    public void replaceShipped(List<ProfileRule> rules) {
        requireOrigin(RuleOrigin.PRESET, rules);
        jpaRepository.deleteShipped(RuleOrigin.PRESET.name());
        jpaRepository.saveAll(mapper.toEntities(rules));
    }

    @Override
    @Transactional
    public void replace(BandId band, RuleOrigin origin, List<ProfileRule> rules) {
        if (origin == RuleOrigin.PRESET) {
            throw new IllegalArgumentException("the shipped rules are everyone's, not a band's");
        }
        requireOrigin(origin, rules);
        jpaRepository.deleteOwn(band.value(), origin.name());
        List<CalendarRuleEntity> own = mapper.toEntities(rules);
        own.forEach(rule -> rule.setBandId(band.value()));
        jpaRepository.saveAll(own);
    }

    private static void requireOrigin(RuleOrigin origin, List<ProfileRule> rules) {
        if (rules.stream().anyMatch(r -> r.origin() != origin)) {
            throw new IllegalArgumentException("every rule must have origin " + origin);
        }
    }
}
