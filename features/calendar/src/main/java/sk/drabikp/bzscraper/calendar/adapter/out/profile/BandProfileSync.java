package sk.drabikp.bzscraper.calendar.adapter.out.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.calendar.application.port.out.BandProfileStore;
import sk.drabikp.bzscraper.calendar.config.CalendarProperties;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.calendar.domain.rules.RuleOrigin;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Brings the stored band profile up to date on start. The shipped rules
 * ({@code calendar/base.profile} + the configured language presets) replace the
 * {@code PRESET} rules (everyone's), so a new app version's presets take effect. The band rules file
 * ({@code bzscraper.calendar.profile-file}) — where band #1 kept its own rules before each band edited
 * them on the page — becomes band #1's {@code USER} rules once, while it has none; after that the page
 * is where they change. A broken file is reported and skipped. {@code LEARNED} rules are never touched here.
 */
@Component
class BandProfileSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BandProfileSync.class);

    private final BandProfileStore store;
    private final List<String> presets;
    private final String profileFile;

    BandProfileSync(BandProfileStore store, CalendarProperties properties) {
        this.store = store;
        this.presets = properties.presetNames();
        this.profileFile = properties.profileFile();
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ProfileRule> shipped = new ArrayList<>(ProfileFile.parse(resource("base"), RuleOrigin.PRESET));
        presets.forEach(preset -> shipped.addAll(ProfileFile.parse(resource(preset), RuleOrigin.PRESET)));
        store.replaceShipped(shipped);

        if (profileFile.isEmpty()) {
            return;
        }
        Path path = Path.of(profileFile);
        if (!Files.isRegularFile(path)
                || store.rules(BandId.FIRST).stream().anyMatch(rule -> rule.origin() == RuleOrigin.USER)) {
            return;
        }
        try {
            List<ProfileRule> own = ProfileFile.parse(Files.readString(path, StandardCharsets.UTF_8), RuleOrigin.USER);
            store.replace(BandId.FIRST, RuleOrigin.USER, own);
            log.info("Band #1's own calendar rules: {} from {} (edited on the page from now on)", own.size(), path);
        } catch (IOException | IllegalArgumentException e) {
            log.error("Band profile file {} not loaded ({})", path, e.getMessage());
        }
    }

    private static String resource(String name) {
        try {
            return new ClassPathResource("calendar/" + name + ".profile").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unknown calendar preset " + name, e);
        }
    }
}
