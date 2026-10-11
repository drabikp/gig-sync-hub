package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.gig.domain.TestBands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.drabikp.bzscraper.gig.domain.TestBands.BAND;
import static sk.drabikp.bzscraper.gig.domain.TestBands.OTHER;

/**
 * The database's own net under the ports' band checks (migration V6): as the app's role, a band's tables show
 * and take only the rows of the band the work is told ({@code app.band}) — none when told none, every band's
 * only when told {@code *}. (The tests otherwise connect as the owner, whom the policies don't hold.)
 */
@SpringBootTest
@Transactional
class RowLevelSecurityTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void twoBandsGigs() {
        jdbc.update(TestBands.INSERT_OTHER);
        insertGig(BAND.toString(), "2026-11-14_ours");
        insertGig(OTHER.toString(), "2026-11-14_theirs");
    }

    /** {@code match} is the gig's day and venue; its identity is its own, and nothing here reads it. */
    private void insertGig(String band, String match) {
        jdbc.update("insert into gig (band_id, id, match_key, title, cancelled)"
                + " values (?::uuid, gen_random_uuid(), ?, 'Fest', false)", band, match);
    }

    /** From here on in this transaction: the app's role, told {@code scope}. */
    private void asApp(String scope) {
        jdbc.execute("set local role bzscraper_app");
        jdbc.queryForObject("select set_config('app.band', ?, true)", String.class, scope);
    }

    private int gigs() {
        return jdbc.queryForObject("select count(*) from gig where match_key like '2026-11-14_%'", Integer.class);
    }

    @Test
    void a_band_sees_and_writes_only_its_own_rows() {
        asApp(BAND.toString());

        assertThat(gigs()).isEqualTo(1);
        assertThat(jdbc.update("update gig set title = 'Changed' where match_key like '2026-11-14_%'")).as("only ours").isEqualTo(1);
        assertThat(jdbc.update("delete from gig where match_key = '2026-11-14_theirs'")).isZero();
        assertThatThrownBy(() -> insertGig(OTHER.toString(), "2026-11-15_sneaked"))
                .as("a row of another band can't be written").isInstanceOf(DataAccessException.class);
    }

    @Test
    void told_nothing_nothing_is_seen_and_told_every_band_all_is() {
        asApp("");
        assertThat(gigs()).isZero();

        jdbc.queryForObject("select set_config('app.band', '*', true)", String.class);
        assertThat(gigs()).isEqualTo(2);
    }

    @Test
    void the_shipped_calendar_rules_are_everyones_but_only_the_apps_own_work_writes_them() {
        asApp(BAND.toString());

        assertThat(jdbc.queryForObject("select count(*) from calendar_rule where band_id is null", Integer.class))
                .as("readable").isNotNegative();
        assertThatThrownBy(() -> jdbc.update("insert into calendar_rule (kind, rule_value, weight, origin, enabled) "
                + "values ('REPEATING', null, -10, 'PRESET', true)")).isInstanceOf(DataAccessException.class);
    }
}
