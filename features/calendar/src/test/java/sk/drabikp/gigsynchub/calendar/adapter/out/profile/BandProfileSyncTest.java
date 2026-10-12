package sk.drabikp.gigsynchub.calendar.adapter.out.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sk.drabikp.gigsynchub.calendar.application.port.out.BandProfileStore;
import sk.drabikp.gigsynchub.calendar.config.CalendarProperties;
import sk.drabikp.gigsynchub.calendar.domain.rules.ProfileRule;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleKind;
import sk.drabikp.gigsynchub.calendar.domain.rules.RuleOrigin;
import sk.drabikp.gigsynchub.gig.domain.BandId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BandProfileSyncTest {

    /** The stored rules, replaced per origin. */
    private static final class Store implements BandProfileStore {

        private final List<ProfileRule> shipped = new ArrayList<>();
        private final Map<BandId, List<ProfileRule>> own = new HashMap<>();

        @Override
        public List<ProfileRule> rules(BandId band) {
            List<ProfileRule> rules = new ArrayList<>(shipped);
            rules.addAll(own.getOrDefault(band, List.of()));
            return rules;
        }

        @Override
        public void replaceShipped(List<ProfileRule> rules) {
            shipped.clear();
            shipped.addAll(rules);
        }

        @Override
        public void replace(BandId band, RuleOrigin origin, List<ProfileRule> replacement) {
            List<ProfileRule> rules = own.computeIfAbsent(band, b -> new ArrayList<>());
            rules.removeIf(r -> r.origin() == origin);
            rules.addAll(replacement);
        }
    }

    private final Store store = new Store();

    @TempDir
    Path dir;

    private static CalendarProperties properties(String profileFile) {
        return new CalendarProperties(null, null, "sk-cz", profileFile, null, null, null);
    }

    @Test
    void the_shipped_rules_are_stored_as_presets_on_start_and_the_bands_file_as_its_own() throws IOException {
        Path file = Files.writeString(dir.resolve("profile.txt"), "TITLE_STARTS_WITH -6 skuska|reh\n");

        new BandProfileSync(store, properties(file.toString())).run(null);

        assertThat(store.rules(BandId.FIRST)).filteredOn(r -> r.origin() == RuleOrigin.PRESET).extracting(ProfileRule::kind)
                .contains(RuleKind.REPEATING, RuleKind.TITLE_STARTS_WITH, RuleKind.NOTES_LABEL);
        assertThat(store.rules(BandId.FIRST)).filteredOn(r -> r.origin() == RuleOrigin.USER)
                .containsExactly(new ProfileRule(RuleKind.TITLE_STARTS_WITH, "skuska|reh", -6, RuleOrigin.USER, true));
    }

    @Test
    void the_file_is_band_ones_and_the_shipped_rules_everyones() throws IOException {
        Path file = Files.writeString(dir.resolve("profile.txt"), "TITLE_STARTS_WITH -6 skuska\n");

        new BandProfileSync(store, properties(file.toString())).run(null);

        BandId other = new BandId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(store.rules(other)).isNotEmpty().allMatch(r -> r.origin() == RuleOrigin.PRESET);
    }

    @Test
    void the_file_is_read_once_after_that_band_ones_rules_are_edited_on_the_page() throws IOException {
        Path file = Files.writeString(dir.resolve("profile.txt"), "TITLE_STARTS_WITH -6 skuska\n");
        new BandProfileSync(store, properties(file.toString())).run(null);
        store.replace(BandId.FIRST, RuleOrigin.USER,
                List.of(new ProfileRule(RuleKind.TITLE_CONTAINS, "zkouska", -5, RuleOrigin.USER, true)));
        Files.writeString(file, "TITLE_STARTS_WITH -6 something else\n");

        new BandProfileSync(store, properties(file.toString())).run(null);

        assertThat(store.rules(BandId.FIRST)).filteredOn(r -> r.origin() == RuleOrigin.USER).extracting(ProfileRule::value)
                .containsExactly("zkouska");
    }

    @Test
    void a_broken_band_file_is_skipped() throws IOException {
        Path file = Files.writeString(dir.resolve("profile.txt"), "TITLE_START -6 skuska\n");

        new BandProfileSync(store, properties(file.toString())).run(null);

        assertThat(store.rules(BandId.FIRST)).isNotEmpty().allMatch(r -> r.origin() == RuleOrigin.PRESET);
    }
}
