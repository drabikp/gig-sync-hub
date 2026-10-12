package sk.drabikp.gigsynchub.gig.adapter.out.persistence;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * The app's own login to the database: after the migrations (on Flyway's connection, as the tables' owner),
 * the role {@code gigsynchub_app} — which row-level security holds (migration V6), no superuser — gets a login
 * with the password {@code gigsynchub.db.app-role-password} (a secret). The app's connections then sign in as
 * it: a mistake in the app can't step out of the role (as {@code set role} could be undone by {@code reset
 * role}) nor reach anything the owner can. Only when that password is set (in Docker).
 */
@Component
@ConditionalOnExpression("!'${gigsynchub.db.app-role-password:}'.isBlank()")
class AppRoleLogin implements Callback {

    private static final Logger log = LoggerFactory.getLogger(AppRoleLogin.class);

    private final String password;

    AppRoleLogin(@Value("${gigsynchub.db.app-role-password}") String password) {
        this.password = password.strip();
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
        return "app role login";
    }

    @Override
    public void handle(Event event, Context context) {
        try (Statement statement = context.getConnection().createStatement()) {
            // a role's password can't be a bind parameter: a quoted literal, its quotes doubled
            statement.execute("alter role gigsynchub_app with login nosuperuser nobypassrls password '"
                    + password.replace("'", "''") + "'");
            log.info("The app's database role gigsynchub_app can sign in (its password set)");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not give the role gigsynchub_app its login", e);
        }
    }
}
