import io.github.str4ng3r.common.Constants;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.shaded.com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Runs the full {@link AbstractDialectTest} suite against a real MySQL instance
 * provided by Testcontainers.
 *
 * <p>The container is started once and the schema created once, in
 * {@code @BeforeClass}. Starting/stopping a fresh container per test method (as
 * JUnit 4 does with {@code @Before}/{@code @After}) made this suite take minutes,
 * since MySQL boots and shuts down slowly. All tests share the same container.
 */
public class MysqlTest extends AbstractDialectTest {

    /** Shared across all test methods — started once, stopped once. */
    private static MySQLContainer<?> container;
    private static Connection connection;

    @Override
    protected Constants.SqlDialect dialect() {
        return Constants.SqlDialect.Mysql;
    }

    @Override
    protected Connection getConnection() throws SQLException {
        if (connection == null)
            // allowMultiQueries lets the mock's multiple statements run in one execute().
            connection = DriverManager.getConnection(
                    container.getJdbcUrl() + "?allowMultiQueries=true",
                    container.getUsername(),
                    container.getPassword());
        return connection;
    }

    @BeforeClass
    public static void setup() throws IOException, SQLException {
        container = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("integration-tests-db")
                .withUsername("sa")
                .withPassword("sa");
        container.start();

        String initDb = Resources.toString(
                Objects.requireNonNull(MysqlTest.class.getClassLoader().getResource("mock/mysqlmock.sql")),
                Charset.defaultCharset());
        Connection con = DriverManager.getConnection(
                container.getJdbcUrl() + "?allowMultiQueries=true",
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
        if (container != null) container.stop();
    }
}
