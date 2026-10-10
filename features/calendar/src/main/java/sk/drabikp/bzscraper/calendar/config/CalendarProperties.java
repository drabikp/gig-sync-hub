package sk.drabikp.bzscraper.calendar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * {@code bzscraper.calendar.*}: the zone a band's calendar is in unless it says another, the rule presets,
 * and the scores that sort events into gig / unsure / not a gig. Each band sets up its own calendar and
 * rules on the page; {@code ical-url} (a secret, never in git; {@link #toString} hides it) and
 * {@code profile-file} are band #1's from before that, kept once on start.
 */
@ConfigurationProperties("bzscraper.calendar")
public record CalendarProperties(String icalUrl, String zone, String presets, String profileFile, Integer gigScore,
                                 Integer notGigScore, Integer strongNegative) {

    public CalendarProperties {
        icalUrl = icalUrl == null ? "" : icalUrl.strip();
        zone = zone == null || zone.isBlank() ? "Europe/Prague" : zone;
        presets = presets == null ? "sk-cz" : presets;
        profileFile = profileFile == null ? "./data/calendar-profile.txt" : profileFile.strip();
        gigScore = gigScore == null ? 4 : gigScore;
        notGigScore = notGigScore == null ? -1 : notGigScore;
        strongNegative = strongNegative == null ? -4 : strongNegative;
    }

    /** The preset rule files named in {@code presets} (comma-separated). */
    public List<String> presetNames() {
        return Arrays.stream(presets.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public String toString() {
        return "CalendarProperties[icalUrl=" + (icalUrl.isEmpty() ? "" : "***") + ", zone=" + zone + ", presets="
                + presets + ", profileFile=" + profileFile + ", gigScore=" + gigScore + ", notGigScore=" + notGigScore
                + ", strongNegative=" + strongNegative + "]";
    }
}
