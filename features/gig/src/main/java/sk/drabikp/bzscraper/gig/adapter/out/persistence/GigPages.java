package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.gig.domain.BandId;
import sk.drabikp.bzscraper.gig.domain.GigCursor;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.GigQuery;
import sk.drabikp.bzscraper.gig.domain.GigRow;
import sk.drabikp.bzscraper.gig.domain.GigSlice;
import sk.drabikp.bzscraper.gig.domain.Location;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One page of a band's gig list, read as a page: the period, the words, the narrowing and the order
 * are the database's work, and only the columns a row shows are fetched ({@link GigRowProjection}) —
 * no entity is loaded, so the session has nothing to dirty-check and the long texts (description,
 * lineup) stay in the table.
 *
 * <p>Paged by KEYSET, not by counting rows past: {@code (showAt, id) > (the last row's)} walks
 * straight down the {@code gig_band_show} index, so the hundredth page costs what the first one does,
 * and a gig added or removed meanwhile cannot push a row past the band unseen or show it twice.
 *
 * <p>The query is assembled from fixed fragments — nothing a band typed is interpolated, every value
 * is bound — and there are only a handful of shapes, so Hibernate's query plan cache keeps them all.
 */
@Component
class GigPages {

    private static final String SELECT = "select new sk.drabikp.bzscraper.gig.adapter.out.persistence"
            + ".GigRowProjection(g.id, g.showAt, g.startDateTime, g.title, g.venue, g.city, g.country, g.cancelled) ";
    private static final String FROM = "from GigEntity g where g.bandId = :band ";

    private final EntityManager entityManager;

    GigPages(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    GigSlice page(BandId band, GigQuery query, LocalDate today) {
        if (query.matchesNothing()) {
            return GigSlice.empty();                      // a narrowing nothing satisfies: don't ask
        }
        boolean upcoming = query.period() == GigQuery.Period.UPCOMING;
        String filters = filters(query, upcoming);
        String order = upcoming ? "order by g.showAt asc, g.id asc" : "order by g.showAt desc, g.id desc";

        TypedQuery<GigRowProjection> ask = bind(entityManager.createQuery(
                SELECT + FROM + filters + keyset(query, upcoming) + order, GigRowProjection.class), band, query, today);
        if (query.after() != null) {                      // only this query carries the cursor; the count does not
            ask.setParameter("cursorAt", query.after().showAt()).setParameter("cursorId", query.after().id());
        }
        ask.setMaxResults(query.size() + 1);              // the extra row answers "is there more?"
        List<GigRowProjection> found = ask.getResultList();

        List<GigRow> page = new ArrayList<>(Math.min(found.size(), query.size()));
        for (int i = 0; i < found.size() && i < query.size(); i++) {
            page.add(row(found.get(i)));
        }
        long total = bind(entityManager.createQuery("select count(g.id) " + FROM + filters, Long.class),
                band, query, today).getSingleResult();
        return new GigSlice(page, found.size() > query.size() ? GigCursor.after(page.getLast()) : null, total);
    }

    private static String filters(GigQuery query, boolean upcoming) {
        StringBuilder jpql = new StringBuilder(upcoming ? "and g.showAt >= :today " : "and g.showAt < :today ");
        if (query.searching()) {
            jpql.append("and g.searchText like :text ");
        }
        if (query.onlyIds() != null) {
            jpql.append("and g.id in :ids ");
        }
        if (query.excludingAny()) {
            jpql.append("and g.id not in :exceptIds ");
        }
        if (query.cancelled() != null) {
            jpql.append("and g.cancelled = :cancelled ");
        }
        return jpql.toString();
    }

    private static String keyset(GigQuery query, boolean upcoming) {
        if (query.after() == null) {
            return "";
        }
        return upcoming
                ? "and (g.showAt > :cursorAt or (g.showAt = :cursorAt and g.id > :cursorId)) "
                : "and (g.showAt < :cursorAt or (g.showAt = :cursorAt and g.id < :cursorId)) ";
    }

    private static <T> TypedQuery<T> bind(TypedQuery<T> query, BandId band, GigQuery asked, LocalDate today) {
        query.setParameter("band", band.value()).setParameter("today", today.atStartOfDay());
        if (asked.searching()) {
            query.setParameter("text", "%" + asked.text() + "%");
        }
        if (asked.onlyIds() != null) {
            query.setParameter("ids", keysOf(asked.onlyIds()));
        }
        if (asked.excludingAny()) {
            query.setParameter("exceptIds", keysOf(asked.exceptIds()));
        }
        if (asked.cancelled() != null) {
            query.setParameter("cancelled", asked.cancelled());
        }
        return query;
    }

    private static Set<String> keysOf(Set<GigId> ids) {
        return ids.stream().map(GigId::key).collect(Collectors.toSet());
    }

    private static GigRow row(GigRowProjection row) {
        return new GigRow(GigId.fromKey(row.id()), row.showAt(),
                row.startDateTime().atZone(Location.zoneOf(row.country())), row.title(), row.venue(), row.city(),
                row.country(), row.cancelled());
    }
}
