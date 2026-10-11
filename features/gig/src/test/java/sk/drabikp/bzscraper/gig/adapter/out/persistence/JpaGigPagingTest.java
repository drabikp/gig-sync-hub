package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.gig.application.port.out.GigPagingContract;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.domain.GigQuery;
import sk.drabikp.bzscraper.gig.domain.TestBands;
import sk.drabikp.bzscraper.gig.domain.TestGigs;

import static org.assertj.core.api.Assertions.assertThat;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;

/** The real store answering {@link GigPagingContract} — on PostgreSQL, with the list's own index. */
@SpringBootTest
@Transactional
class JpaGigPagingTest extends GigPagingContract {

    @Autowired
    private JpaGigRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    /** The contract asks about a second band, so this one has to exist (band #1 comes with the schema). */
    @BeforeEach
    void anotherBand() {
        jdbc.update(TestBands.INSERT_OTHER);
    }

    @Override
    protected GigRepository repository() {
        return repository;
    }

    @Test
    void the_database_keeps_the_show_time_and_the_folded_words_itself() {
        repository.save(BAND, TestGigs.gig("Jesenné turné", "Barrák", "Ostrava", at(2026, 11, 20, 21)));
        entityManager.flush();                    // JdbcTemplate reads the table, not the session

        assertThat(jdbc.queryForObject(
                "select search_text from gig where band_id = ? and title = 'Jesenné turné'", String.class,
                BAND.value()))
                .as("folded with the row, so the search is the database's work")
                .isEqualTo("jesenne turne barrak ostrava");
        assertThat(jdbc.queryForObject(
                "select show_at = start_date_time from gig where band_id = ? and title = 'Jesenné turné'",
                Boolean.class, BAND.value()))
                .as("no slot, so the show time is the start — and the column is the database's, generated")
                .isTrue();
    }

    /**
     * That the list's query CAN be answered by walking its index — no sort of the band's catalog to
     * find a page, and the keyset lands inside the scan. With a handful of rows PostgreSQL rightly
     * reads the table instead, which says nothing about the shape, so the sequential scan is taken
     * away and the plan asked for.
     */
    @Test
    void the_lists_query_is_an_index_walk_and_never_a_sort_of_the_catalog() {
        for (int day = 12; day <= 21; day++) {
            repository.save(BAND, TestGigs.gig("gig " + day, "Klub 007", "Praha", at(2026, 10, day, 20)));
        }
        entityManager.flush();
        jdbc.execute("set local enable_seqscan = off");

        String plan = String.join("\n", jdbc.queryForList(
                "explain (costs off) select id from gig where band_id = ? and show_at >= ? "
                        + "and (show_at, id) > (?, ?) order by show_at, id limit 3",
                String.class, BAND.value(), TODAY.atStartOfDay(), TODAY.atStartOfDay(), "aaa"));

        assertThat(plan).as("read in the list's own order off its index:\n" + plan).contains("gig_band_show");
        assertThat(plan).as("nothing sorted to answer a page:\n" + plan).doesNotContain("Sort");
        assertThat(repository.page(BAND, GigQuery.upcoming().sized(3), TODAY).rows()).hasSize(3);
    }
}
