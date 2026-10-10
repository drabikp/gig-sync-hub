package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.calendar.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.util.List;

/** JPA-backed {@link BandProfileStore}: rules in table {@code calendar_rule} (the shipped ones without a band), in insertion order. */
@Component
@Transactional
class JpaBandProfileStore implements BandProfileStore {

    private final CalendarRuleJpaRepository jpaRepository;
    private final CalendarEntityMapper mapper;

    public JpaBandProfileStore(CalendarRuleJpaRepository jpaRepository, CalendarEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileRule> rules(BandId band) {
        return mapper.toRules(jpaRepository.forBand(band.value()));
    }

    @Override
    public void replaceShipped(List<ProfileRule> rules) {
        requireOrigin(RuleOrigin.PRESET, rules);
        jpaRepository.deleteShipped(RuleOrigin.PRESET.name());
        jpaRepository.saveAll(mapper.toEntities(rules));
    }

    @Override
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
