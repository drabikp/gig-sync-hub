package sk.drabikp.gigsynchub.gig.adapter.out.persistence;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * The app's connections, each told at checkout which band's rows the work on this thread may see
 * ({@code app.band}, see {@link ThreadRowScope}); the database's row-level security policies (migration V6)
 * hold every band's table to it. (The migrations run on a connection of their own, as the tables' owner.)
 */
public class RowScopedDataSource extends DelegatingDataSource {

    public RowScopedDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return scoped(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return scoped(super.getConnection(username, password));
    }

    private static Connection scoped(Connection connection) throws SQLException {
        try (PreparedStatement scope = connection.prepareStatement("select set_config('app.band', ?, false)")) {
            scope.setString(1, ThreadRowScope.current());
            scope.execute();
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
