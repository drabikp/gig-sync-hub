package sk.drabikp.bzscraper.gig.adapter.out.persistence;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.gig.domain.BandId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * The one-time move from H2 (the app's database until 2026-10) to PostgreSQL: with
 * {@code bzscraper.db.import-h2} set to an H2 SQL backup ({@code SCRIPT TO}), right after Flyway
 * has built the schema, loads the backup into an in-memory H2 and copies every table's rows
 * (the columns both have) into PostgreSQL, then moves the id counters past the copied ids.
 * Only into an empty database — never over data; one transaction: anything that fails rolls the
 * whole import back and stops the start, so it can be fixed and run again.
 */
@Component
@ConditionalOnExpression("!'${bzscraper.db.import-h2:}'.isBlank()")
class H2Import implements Callback {

    private static final Logger log = LoggerFactory.getLogger(H2Import.class);

    /** In an order that keeps references valid (a task's log after the task). */
    static final List<String> TABLES = List.of("gig", "published_gig", "sync_task", "sync_log", "app_setting",
            "calendar_rule", "calendar_decision", "calendar_event", "calendar_link");
    private static final Set<String> WITH_ID_COUNTER = Set.of("sync_task", "sync_log", "calendar_rule");

    private final Path script;

    H2Import(@Value("${bzscraper.db.import-h2}") String script) {
        this.script = Path.of(script);
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public String getCallbackName() {
        return "h2-import";
    }

    @Override
    public void handle(Event event, Context context) {
        Connection target = context.getConnection();
        try {
            boolean autoCommit = target.getAutoCommit();
            target.setAutoCommit(false);
            try {
                run(target);
                target.commit();
            } catch (SQLException | RuntimeException e) {
                target.rollback();
                throw e;
            } finally {
                target.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("The import from H2 (" + script + ") failed: " + e.getMessage(), e);
        }
    }

    void run(Connection target) throws SQLException {
        for (String table : TABLES) {
            if (rows(target, table) > 0) {
                log.info("H2 import skipped: the database already has data ({}). Remove bzscraper.db.import-h2.", table);
                return;
            }
        }
        if (!Files.isReadable(script)) {
            throw new IllegalStateException("bzscraper.db.import-h2: cannot read " + script.toAbsolutePath());
        }
        try (Connection source = new org.h2.Driver().connect("jdbc:h2:mem:", new Properties())) {
            try (PreparedStatement load = source.prepareStatement("RUNSCRIPT FROM ?")) {
                load.setString(1, script.toAbsolutePath().toString());
                load.execute();
            }
            Map<String, Integer> copied = new LinkedHashMap<>();
            for (String table : TABLES) {
                copied.put(table, copy(source, target, table));
            }
            for (String table : WITH_ID_COUNTER) {
                try (Statement counter = target.createStatement()) {
                    counter.execute("select setval(pg_get_serial_sequence('" + table + "', 'id'),"
                            + " coalesce(max(id), 0) + 1, false) from " + table);
                }
            }
            log.info("Imported from H2 {}: {}", script.getFileName(), copied);
        }
    }

    private static int copy(Connection source, Connection target, String table) throws SQLException {
        List<String> columns = columns(source, table.toUpperCase(Locale.ROOT));
        if (columns.isEmpty()) {
            return 0;                                   // an older database without this table
        }
        List<String> targetColumns = columns(target, table);
        columns.retainAll(targetColumns);
        // the H2 database knew one band: its rows are band #1's (the shipped calendar rules nobody's)
        boolean toFirstBand = targetColumns.contains("band_id") && !columns.contains("band_id");
        String list = String.join(", ", columns);
        String marks = String.join(", ", columns.stream().map(c -> "?").toList());
        String insertSql = toFirstBand
                ? "insert into " + table + " (" + list + ", band_id) values (" + marks + ", ?)"
                : "insert into " + table + " (" + list + ") values (" + marks + ")";
        int origin = columns.indexOf("origin") + 1;
        int count = 0;
        try (Statement read = source.createStatement();
             ResultSet rows = read.executeQuery("select " + list + " from " + table);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            ResultSetMetaData meta = rows.getMetaData();
            while (rows.next()) {
                for (int i = 1; i <= columns.size(); i++) {
                    insert.setObject(i, value(rows, i, meta.getColumnType(i)));
                }
                if (toFirstBand) {
                    boolean preset = origin > 0 && "PRESET".equals(rows.getString(origin));
                    insert.setObject(columns.size() + 1, preset ? null : BandId.FIRST.value());
                }
                insert.addBatch();
                if (++count % 500 == 0) {
                    insert.executeBatch();
                }
            }
            insert.executeBatch();
        }
        return count;
    }

    /** Times as the calendar day and clock they were (no time zone shifts on the way). */
    private static Object value(ResultSet rows, int column, int type) throws SQLException {
        return switch (type) {
            case Types.TIMESTAMP -> rows.getObject(column, LocalDateTime.class);
            case Types.TIMESTAMP_WITH_TIMEZONE -> rows.getObject(column, OffsetDateTime.class);
            case Types.OTHER -> rows.getString(column);  // H2's enum columns (country, entry type)
            default -> rows.getObject(column);
        };
    }

    private static List<String> columns(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet found = meta.getColumns(null, null, table, null)) {
            while (found.next()) {
                String schema = found.getString("TABLE_SCHEM");
                if (schema != null && schema.equalsIgnoreCase("public")) {
                    columns.add(found.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }

    private static long rows(Connection connection, String table) throws SQLException {
        try (Statement count = connection.createStatement();
             ResultSet result = count.executeQuery("select count(*) from " + table)) {
            result.next();
            return result.getLong(1);
        }
    }
}
