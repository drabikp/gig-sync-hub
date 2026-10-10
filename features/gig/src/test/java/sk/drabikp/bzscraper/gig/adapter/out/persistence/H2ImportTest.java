package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one-time move from H2: a database as the H2 app left it (its last schema, enum columns,
 * ids already used), written as the app's daily SQL backup was, goes into an empty PostgreSQL.
 */
@SpringBootTest(classes = GigPersistenceTestApplication.class)
class H2ImportTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        Path script = Files.createTempDirectory("h2-import").resolve("bzscraper-2026-10-09.sql");
        writeBackup(script);
        // a database of its own: the import only goes into an empty one
        registry.add("spring.datasource.url", () -> "jdbc:tc:postgresql:18-alpine:///h2import?TC_DAEMON=true");
        registry.add("bzscraper.db.import-h2", script::toString);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void every_table_comes_over_with_its_values() {
        assertThat(jdbc.queryForMap("select title, country, entry_type, start_date_time, latitude, version, cancelled"
                + " from gig where id = '2026-08-22|@lukova'"))
                .containsEntry("title", "Moto fest")
                .containsEntry("country", "CZECHIA")
                .containsEntry("entry_type", "FREE")
                .containsEntry("latitude", 49.876)
                .containsEntry("version", 3L)
                .containsEntry("cancelled", false);
        assertThat(jdbc.queryForObject("select start_date_time from gig", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 8, 22, 18, 0));
        assertThat(jdbc.queryForObject("select external_ref from published_gig where platform = 'BANDZONE'",
                String.class)).isEqualTo("563406");
        assertThat(jdbc.queryForList("select distinct band_id::text from gig", String.class))
                .as("the one band of H2 is band #1").containsExactly("00000000-0000-0000-0000-000000000001");
        assertThat(jdbc.queryForObject("select created_at from sync_task where id = 15", OffsetDateTime.class)
                .toInstant()).isEqualTo(OffsetDateTime.of(2026, 10, 9, 9, 54, 36, 0, ZoneOffset.UTC).toInstant());
        assertThat(jdbc.queryForObject("select message from sync_log where task_id = 15", String.class))
                .isEqualTo("done");
        assertThat(jdbc.queryForObject("select notes from calendar_event", String.class)).isEqualTo("fee: private");
        assertThat(jdbc.queryForObject("select setting_value from app_setting where name = 'sync.paused'",
                String.class)).isEqualTo("false");
    }

    @Test
    void new_rows_get_ids_after_the_copied_ones() {
        Long task = jdbc.queryForObject("insert into sync_task (band_id, gig_id, gig_label, platform, action, status,"
                + " attempts, created_at, updated_at) values ('00000000-0000-0000-0000-000000000001', 'g', 'g', 'X',"
                + " 'UPDATE', 'PENDING', 0, now(), now()) returning id", Long.class);
        Long rule = jdbc.queryForObject("insert into calendar_rule (kind, weight, origin, enabled)"
                + " values ('TITLE', 1, 'USER', true) returning id", Long.class);

        assertThat(task).isEqualTo(16L);
        assertThat(rule).isEqualTo(8L);
    }

    /** The H2 database's last schema (V12) with a row in every table, as {@code SCRIPT TO} writes it. */
    private static void writeBackup(Path script) throws SQLException {
        try (Connection h2 = new org.h2.Driver().connect("jdbc:h2:mem:", new Properties());
             Statement sql = h2.createStatement()) {
            sql.execute("""
                    create table gig (id varchar(255) not null primary key, title varchar(255),
                      start_date_time timestamp(6), end_date_time timestamp(6), venue varchar(255), city varchar(255),
                      country enum ('CZECHIA','SLOVAKIA'), lineup varchar(2000),
                      entry_type enum ('FREE','PAID','VOLUNTARY'), entry_fee varchar(255), description varchar(4000),
                      facebook_url varchar(255), ticket_url varchar(255), poster_image_url varchar(255),
                      cancelled boolean not null, slot_start timestamp(6), slot_end timestamp(6), street varchar(255),
                      postal_code varchar(20), district varchar(100), region varchar(100), latitude double precision,
                      longitude double precision, version bigint default 0 not null);
                    create table published_gig (platform varchar(40) not null, gig_id varchar(255) not null,
                      external_ref varchar(255), primary key (platform, gig_id));
                    create table sync_task (id bigint generated by default as identity primary key,
                      gig_id varchar(255) not null, gig_label varchar(500) not null, platform varchar(40) not null,
                      action varchar(20) not null, status varchar(20) not null, attempts integer not null,
                      created_at timestamp(6) with time zone not null, next_attempt_at timestamp(6) with time zone,
                      updated_at timestamp(6) with time zone not null, message varchar(2000), step varchar(40),
                      version bigint default 0 not null);
                    create table sync_log (id bigint generated by default as identity primary key,
                      task_id bigint not null references sync_task (id), logged_at timestamp(6) with time zone not null,
                      message varchar(2000) not null);
                    create table app_setting (name varchar(100) not null primary key, setting_value varchar(1000));
                    create table calendar_rule (id bigint generated by default as identity primary key,
                      kind varchar(40) not null, rule_value varchar(1000), weight integer not null,
                      origin varchar(20) not null, enabled boolean not null);
                    create table calendar_decision (event_uid varchar(512) not null primary key,
                      verdict varchar(20) not null, decided_at timestamp(6) not null);
                    create table calendar_event (event_uid varchar(512) not null primary key,
                      title varchar(2000) not null, location varchar(2000) not null, notes varchar(1000000) not null,
                      starts_at timestamp(6) not null, ends_at timestamp(6) not null, all_day boolean not null,
                      repeating boolean not null, shown_as_free boolean not null, calendar_status varchar(20) not null,
                      last_modified timestamp(6) with time zone, suggested varchar(20) not null,
                      first_seen timestamp(6) with time zone not null, last_seen timestamp(6) with time zone not null,
                      removed_at timestamp(6) with time zone, change_type varchar(20), change_fields varchar(200),
                      suggested_before varchar(20), changed_at timestamp(6) with time zone);
                    create table calendar_link (event_uid varchar(512) not null primary key,
                      gig_id varchar(255) not null, linked_at timestamp(6) with time zone not null);
                    create table "flyway_schema_history" ("installed_rank" int not null primary key, "version" varchar(50));

                    insert into gig (id, title, start_date_time, city, country, entry_type, cancelled, latitude, version)
                      values ('2026-08-22|@lukova', 'Moto fest', timestamp '2026-08-22 18:00:00', 'Luková',
                              'CZECHIA', 'FREE', false, 49.876, 3);
                    insert into published_gig values ('BANDZONE', '2026-08-22|@lukova', '563406');
                    insert into sync_task (id, gig_id, gig_label, platform, action, status, attempts, created_at,
                      updated_at, version) values (15, '2026-08-22|@lukova', 'Moto fest', 'BANDZONE', 'PUBLISH',
                      'DONE', 1, timestamp with time zone '2026-10-09 11:54:36+02:00',
                      timestamp with time zone '2026-10-09 11:54:36+02:00', 2);
                    insert into sync_log (id, task_id, logged_at, message)
                      values (40, 15, timestamp with time zone '2026-10-09 11:54:36+02:00', 'done');
                    insert into app_setting values ('sync.paused', 'false');
                    insert into calendar_rule (id, kind, rule_value, weight, origin, enabled)
                      values (7, 'TITLE_WORD', 'koncert', 3, 'PRESET', true);
                    insert into calendar_decision values ('uid-1', 'GIG', timestamp '2026-10-01 10:00:00');
                    insert into calendar_event values ('uid-1', 'Moto fest', 'Luková', 'fee: private',
                      timestamp '2026-08-22 16:00:00', timestamp '2026-08-23 02:00:00', false, false, false,
                      'CONFIRMED', null, 'GIG', timestamp with time zone '2026-10-01 10:00:00+02:00',
                      timestamp with time zone '2026-10-09 10:00:00+02:00', null, null, null, null, null);
                    insert into calendar_link values ('uid-1', '2026-08-22|@lukova',
                      timestamp with time zone '2026-10-09 11:24:19+02:00');
                    insert into "flyway_schema_history" values (1, '12');
                    """);
            sql.execute("SCRIPT TO '" + script.toString().replace("'", "''") + "'");
        }
    }
}
