package sk.drabikp.gigsynchub.bandsintown.portal.http;

import org.springframework.stereotype.Component;
import sk.drabikp.gigsynchub.accounts.application.port.out.PlatformSignIn;
import sk.drabikp.gigsynchub.accounts.domain.PlatformAccount;
import sk.drabikp.gigsynchub.bandsintown.BandsintownAccount;
import sk.drabikp.gigsynchub.bandsintown.BandsintownPlatform;
import sk.drabikp.gigsynchub.bandsintown.BandsintownProperties;
import sk.drabikp.gigsynchub.bandsintown.BitPortalException;
import sk.drabikp.gigsynchub.bandsintown.portal.BitEvent;
import sk.drabikp.gigsynchub.bandsintown.portal.PortalReply;
import sk.drabikp.gigsynchub.portal.OnPlatformClient;
import sk.drabikp.gigsynchub.portal.PlatformClient;
import sk.drabikp.gigsynchub.gig.domain.TextFold;
import sk.drabikp.gigsynchub.gig.domain.platform.Platform;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What is kept when a band connects Bandsintown: it signed in at the portal in its OWN browser (the
 * portal's sign-in allows nothing else) and pasted the line its page printed; this reads that line, tries
 * it at once — renewing the sign-in and asking the portal for a session — and keeps only what works, so a
 * band never leaves Settings believing a paste that doesn't.
 *
 * <p>Kept: the refresh token (sealed), the manager and artist the portal's addresses need, and the
 * fingerprint made up for this band. Never kept: the password, nor the authenticator secret — they stay
 * with the band.
 */
@Component
@OnPlatformClient(platform = "bandsintown", value = PlatformClient.HTTP)
class HttpBitSignIn implements PlatformSignIn {

    /**
     * What the portal asks its own list of managed actors for. Without it the list answers EMPTY —
     * which is what once made us believe an account like this could not be asked for its artists.
     */
    private static final String ACTOR_TYPE = "artists";

    private final String baseUrl;
    private final Clock clock;

    HttpBitSignIn(BandsintownProperties properties, Clock clock) {
        this.baseUrl = properties.baseUrl();
        this.clock = clock;
    }

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public Kept signIn(PlatformAccount typed) {
        BitConnection connection;
        BitSignIn signIn;
        String actorId;
        try {
            connection = BitConnection.read(typed.secret(BandsintownAccount.CONNECTION));
            signIn = new BitSignIn(baseUrl, connection, clock);
            signIn.sessionToken();
            actorId = connection.actorId().isBlank()
                    ? theArtist(artists(new BitWeb(baseUrl, signIn)), typed.setting(BandsintownAccount.ARTIST_NAME))
                    : connection.actorId();
        } catch (BitPortalException e) {
            return Kept.failed(e.getMessage());
        }
        Map<String, String> settings = new LinkedHashMap<>(typed.settings());
        settings.put(BandsintownAccount.MANAGER_ID, signIn.managerId());
        settings.put(BandsintownAccount.ACTOR_ID, actorId);
        settings.put(BandsintownAccount.FINGERPRINT, connection.fingerprint());
        return Kept.of(settings, Map.of(BandsintownAccount.REFRESH_TOKEN, connection.refreshToken()));
    }

    /**
     * The artists this sign-in looks after, as the portal's own page asks for them — the one call that
     * turns a sign-in into the artist every address is built from, so a band never goes looking for that
     * number. The {@code type} is not decoration: asked without it, the portal answers an EMPTY list
     * whatever the account has, which is what once had us believing it could not be asked at all.
     */
    static List<Actor> artists(BitApi api) throws BitPortalException {
        PortalReply reply = api.get(api.managerPath("managed-actors?type=" + ACTOR_TYPE));
        if (!reply.ok()) {
            throw BitPortalException.temporary("Bandsintown would not say which artists this sign-in looks "
                    + "after — " + reply.describe());
        }
        List<Actor> artists = new ArrayList<>();
        for (Map<String, Object> row : reply.payloadRows()) {
            String id = BitEvent.idOf(row.get("managed_actor_id"));
            if (id != null) {
                artists.add(new Actor(id, String.valueOf(row.getOrDefault("name", ""))));
            }
        }
        return artists;
    }

    /** One artist a sign-in looks after: the id the portal's addresses want, and its name. */
    record Actor(String actorId, String name) {
    }

    /**
     * Which artist of this sign-in the band means: the only one it looks after, or — for a manager with
     * several — the one whose name the band gave. Named back in the problem when it is neither, so the
     * band is told what it may choose from rather than left with an account pointing nowhere.
     */
    static String theArtist(List<Actor> artists, String artistName) throws BitPortalException {
        if (artists.isEmpty()) {
            throw BitPortalException.needsUser("That Bandsintown sign-in looks after no artist — sign in there as "
                    + "the account that manages the band.");
        }
        if (artists.size() == 1) {
            return artists.getFirst().actorId();
        }
        String wanted = TextFold.fold(artistName == null ? "" : artistName);
        return artists.stream().filter(artist -> TextFold.fold(artist.name()).equals(wanted)).findFirst()
                .map(Actor::actorId)
                .orElseThrow(() -> BitPortalException.needsUser("That Bandsintown sign-in looks after "
                        + artists.stream().map(Actor::name).collect(Collectors.joining(", "))
                        + " — write the artist's name exactly as one of those."));
    }
}
