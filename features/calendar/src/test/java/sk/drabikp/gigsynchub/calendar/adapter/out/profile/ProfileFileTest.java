package sk.drabikp.gigsynchub.calendar.adapter.out.profile;

import org.junit.jupiter.api.Test;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleKind;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleOrigin;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileFileTest {

    @Test
    void reads_one_rule_per_line_skipping_comments_and_blank_lines() {
        List<ProfileRule> rules = ProfileFile.parse("""
                # band rules

                TITLE_STARTS_WITH  -6  skuska|reh
                notes_label         +5  cas predbezne|showtime
                LONGER_THAN_DAYS   -3  3
                REPEATING         -10
                TRAVEL              0  cesta
                """, RuleOrigin.USER);

        assertThat(rules).containsExactly(
                new ProfileRule(RuleKind.TITLE_STARTS_WITH, "skuska|reh", -6, RuleOrigin.USER, true),
                new ProfileRule(RuleKind.NOTES_LABEL, "cas predbezne|showtime", 5, RuleOrigin.USER, true),
                new ProfileRule(RuleKind.LONGER_THAN_DAYS, "3", -3, RuleOrigin.USER, true),
                new ProfileRule(RuleKind.REPEATING, null, -10, RuleOrigin.USER, true),
                new ProfileRule(RuleKind.TRAVEL, "cesta", 0, RuleOrigin.USER, true));
        assertThat(rules.get(1).alternatives()).containsExactly("cas predbezne", "showtime");
    }

    @Test
    void a_mistake_names_its_line() {
        assertThatThrownBy(() -> ProfileFile.parse("REPEATING -10\nTITLE_START -6 x", RuleOrigin.USER))
                .hasMessageContaining("line 2").hasMessageContaining("unknown rule kind TITLE_START");
        assertThatThrownBy(() -> ProfileFile.parse("MEMBER minus jana", RuleOrigin.USER))
                .hasMessageContaining("line 1").hasMessageContaining("not a whole number");
        assertThatThrownBy(() -> ProfileFile.parse("MEMBER -4", RuleOrigin.USER))
                .hasMessageContaining("line 1").hasMessageContaining("needs one or more words");
        assertThatThrownBy(() -> ProfileFile.parse("TITLE_REPEATED -3 often", RuleOrigin.USER))
                .hasMessageContaining("needs a whole number");
        assertThatThrownBy(() -> ProfileFile.parse("CONFIRMED_FIELD 0 stav", RuleOrigin.USER))
                .hasMessageContaining("label=prefix");
    }
}
