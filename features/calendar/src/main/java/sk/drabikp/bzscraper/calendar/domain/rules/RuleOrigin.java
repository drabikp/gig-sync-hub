package sk.drabikp.bzscraper.calendar.domain.rules;

import org.jmolecules.ddd.annotation.ValueObject;

/** Where a band-profile rule comes from — decides who may replace it. */
@ValueObject
public enum RuleOrigin {
    /** Shipped with the app (language-independent signals, language presets). */
    PRESET,
    /** Learned from the band's own history; replaced on every re-learn. */
    LEARNED,
    /** Set by the band; never changed by learning. */
    USER
}
