package sk.drabikp.gigsynchub.bandzone.portal.http;

import org.springframework.web.util.UriComponentsBuilder;
import sk.drabikp.gigsynchub.bandzone.BandzonePortalException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A concert's "Lineup a časy" tab, through the calls the tab itself makes. Makes the performers equal the
 * gig's lineup plus the publishing band itself: performers not in the lineup are removed (the band itself
 * never is); a missing name is added as the Bandzone profile whose name matches exactly (ignoring case),
 * otherwise as a guest — a performer without a profile, which exists only on this concert. What the
 * concert holds is always read from the concert itself, never from the search.
 */
final class LineupTab {

    private final BandzoneWeb web;
    private final long concertId;

    LineupTab(BandzoneWeb web, long concertId) {
        this.web = web;
        this.concertId = concertId;
    }

    /**
     * Makes the concert's performers the lineup plus the band itself, then checks what Bandzone stored.
     *
     * @param stored the concert as it is now
     * @param reread the concert as it is after the changes
     */
    void sync(StoredConcert stored, List<String> lineup, HttpBandzoneSession.OwnBand own,
              Reread reread) throws BandzonePortalException {
        List<String> desired = desired(lineup, own.name());
        for (StoredConcert.Band band : stored.bands()) {
            if (band.bandId() != own.id() && desired.stream().noneMatch(d -> sameName(d, band.name()))) {
                web.send("DELETE", path("/lineup/" + band.bandId()), null, "remove band '" + band.name()
                        + "' from concert " + concertId);
            }
        }
        for (StoredConcert.Guest guest : stored.guests()) {
            if (desired.stream().noneMatch(d -> sameName(d, guest.name()))) {
                web.send("DELETE", path("/guests/" + guest.guestId()), null, "remove '" + guest.name()
                        + "' from concert " + concertId);
            }
        }
        List<String> present = stored.performers();
        for (String name : desired) {
            if (present.stream().noneMatch(p -> sameName(p, name))) {
                add(name);
            }
        }
        verify(reread.concert(), desired, own);
    }

    /** The concert as read again. */
    @FunctionalInterface
    interface Reread {
        StoredConcert concert() throws BandzonePortalException;
    }

    /** Adds one performer: its exact-name profile, or a guest without one. */
    private void add(String name) throws BandzonePortalException {
        JsonNode found = web.send("GET", UriComponentsBuilder.fromPath("/api/gig/search-bands")
                .queryParam("query", name).queryParam("limit", 10).build().encode().toUriString(), null,
                "search its bands for '" + name + "'");
        Optional<Long> profile = Optional.empty();
        for (JsonNode band : found) {
            if (sameName(band.path("name").asString(""), name)) {
                profile = Optional.of(band.path("bandId").asLong());
                break;
            }
        }
        if (profile.isPresent()) {
            web.send("POST", path("/lineup"), Map.of("bandId", profile.get()), "add band '" + name
                    + "' to concert " + concertId);
        } else {
            Map<String, Object> guest = new LinkedHashMap<>();
            guest.put("name", name);
            guest.put("url", null);
            web.send("POST", path("/guests"), guest, "add '" + name + "' to concert " + concertId);
        }
    }

    /** The lineup without the band itself, each name once. */
    private static List<String> desired(List<String> lineup, String ownBandName) {
        List<String> desired = new ArrayList<>();
        for (String name : lineup) {
            if (name == null || name.isBlank()) {
                continue;                                 // a lineup comes from a form and a platform's page alike
            }
            if (!sameName(name, ownBandName) && desired.stream().noneMatch(d -> sameName(d, name))) {
                desired.add(name.trim());
            }
        }
        return desired;
    }

    /** The stored performers are the lineup plus the band itself (if it was listed before). */
    private void verify(StoredConcert stored, List<String> desired, HttpBandzoneSession.OwnBand own)
            throws BandzonePortalException {
        List<String> names = new ArrayList<>();
        stored.bands().stream().filter(b -> b.bandId() != own.id()).forEach(b -> names.add(b.name()));
        stored.guests().forEach(g -> names.add(g.name()));
        List<String> missing = desired.stream().filter(d -> names.stream().noneMatch(s -> sameName(s, d))).toList();
        List<String> extra = names.stream().filter(s -> desired.stream().noneMatch(d -> sameName(d, s))).toList();
        if (!missing.isEmpty() || !extra.isEmpty()) {
            throw BandzonePortalException.temporary("Bandzone lineup did not sync (missing " + missing
                    + ", unexpected " + extra + ").");
        }
    }

    private String path(String rest) {
        return "/api/gig/" + concertId + rest;
    }

    static boolean sameName(String a, String b) {
        return a != null && b != null && a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }
}
