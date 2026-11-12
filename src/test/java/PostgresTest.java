import io.github.str4ng3r.common.Constants;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Runs the full {@link AbstractDialectTest} suite against a real PostgreSQL
 * instance provided by Testcontainers.
 *
 * <p>The container is started once and the schema created once, in
 * {@code @BeforeClass}. Starting/stopping a fresh container per test method (as
 * JUnit 4 does with {@code @Before}/{@code @After}) multiplied the boot cost by
 * the number of tests. All tests share the same container.
 */
public class PostgresTest extends AbstractDialectTest {

    /** Shared across all test methods — started once, stopped once. */
    private static PostgreSQLContainer<?> container;
    private static Connection connection;
    private static Connection strictConnection;

    @Override
    protected Constants.SqlDialect dialect() {
        return Constants.SqlDialect.Postgres;
    }

    @Override
    protected Connection getConnection() throws SQLException {
        if (connection == null)
            connection = DriverManager.getConnection(
                    container.getJdbcUrl() + "?stringtype=unspecified",
                    container.getUsername(),
                    container.getPassword());
        return connection;
    }

    @Override
    protected Connection getStrictConnection() throws SQLException {
        if (strictConnection == null)
            strictConnection = DriverManager.getConnection(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword());
        return strictConnection;
    }

    @BeforeClass
    public static void setup() throws IOException, SQLException {
        System.setProperty("api.version", "1.44");

        container = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("integration-tests-db")
                .withUsername("sa")
                .withPassword("sa");
        container.start();

        String initDb = Resources.toString(
                Objects.requireNonNull(PostgresTest.class.getClassLoader().getResource("mock/postgresmock.sql")),
                Charset.defaultCharset());
        Connection con = DriverManager.getConnection(
                container.getJdbcUrl() + "?stringtype=unspecified",
                container.getUsername(),
                container.getPassword());
        try (Statement statement = con.createStatement()) {
            statement.execute(initDb);
        }
        con.close();
    }

    @AfterClass
    public static void tearDown() throws SQLException {
        if (connection != null) connection.close();
        if (strictConnection != null) strictConnection.close();
        if (container != null) container.stop();
    }
}
