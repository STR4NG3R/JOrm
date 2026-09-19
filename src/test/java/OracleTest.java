import io.github.str4ng3r.common.Constants;
import oracle.jdbc.OracleDriver;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.shaded.com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Runs the full {@link AbstractDialectTest} suite against a real Oracle XE
 * instance provided by Testcontainers.
 *
 * <p>Oracle XE takes a long time to boot (tens of seconds). Starting a fresh
 * container in {@code @Before} for every test method — as JUnit 4 does — meant
 * Oracle XE was booted once per test, which made the suite look "frozen" for
 * many minutes.
 *
 * <p>Instead, the container is started once and the schema is created once, in
 * {@code @BeforeClass}. All tests then share the same container, connection and
 * seeded schema.
 */
public class OracleTest extends AbstractDialectTest {

    /** Shared across all test methods — started once, stopped once. */
    private static OracleContainer container;
    private static Connection connection;

    @Override
    protected Constants.SqlDialect dialect() {
        return Constants.SqlDialect.Oracle;
    }

    /**
     * Classic SID-style URL: {@code jdbc:oracle:thin:@host:port:xe}.
     *
     * <p>The SID {@code xe} points at the root CDB, where only privileged users
     * like {@code system} exist — the application user created via
     * {@code withUsername("sa")} lives in the {@code XEPDB1} PDB and is not
     * reachable through the SID. So this connection authenticates as
     * {@code system} (password provided via {@code withPassword}).
     */
    private String jdbcUrl() {
        return "jdbc:oracle:thin:@" + container.getHost() + ":" + container.getOraclePort() + ":xe";
    }

    @Override
    protected Connection getConnection() throws SQLException {
        if (connection == null) {
            connection = DriverManager.getConnection(jdbcUrl(), "system", container.getPassword());
            // Oracle's driver may hand out the connection with autoCommit disabled;
            // JOrm assumes autoCommit is on outside explicit transactions.
            connection.setAutoCommit(true);
        }
        return connection;
    }

    @BeforeClass
    public static void setup() throws IOException, SQLException {
        container = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
                .withUsername("sa")
                .withPassword("sa");
        container.start();

        String initDb = Resources.toString(
                Objects.requireNonNull(OracleTest.class.getClassLoader().getResource("mock/oraclemock.sql")),
                Charset.defaultCharset());
        DriverManager.registerDriver(new OracleDriver());

        // Oracle's JDBC execute() does not accept multiple statements at once, so
        // the mock is split on ';' and each non-empty statement is run separately.
        String url = "jdbc:oracle:thin:@" + container.getHost() + ":" + container.getOraclePort() + ":xe";
        Connection con = DriverManager.getConnection(url, "system", container.getPassword());
        con.setAutoCommit(true);
        for (String stmt : initDb.split(";")) {
            String sql = stmt.trim();
            if (sql.isEmpty()) continue;
            try (Statement statement = con.createStatement()) {
                statement.execute(sql);
            }
        }
        con.close();
    }

    @AfterClass
    public static void tearDown() throws SQLException {
        if (connection != null) connection.close();
        if (container != null) container.stop();
    }
}
