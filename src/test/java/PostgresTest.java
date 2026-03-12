
import common.InsertTest;
import common.SelectTest;
import dao.UserDao;
import io.github.str4ng3r.common.*;
import io.github.str4ng3r.exceptions.InvalidCurrentPageException;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import org.example.sql.Runner;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.com.google.common.io.Resources;

import java.io.IOException;

import java.nio.charset.Charset;
import java.sql.*;
import java.util.List;
import java.util.Objects;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;

public class PostgresTest {
    @ClassRule
    public static PostgreSQLContainer postgresContainer;

    Connection connection;

    Connection getConnection() throws SQLException {
        if (connection == null)
            connection = DriverManager.getConnection(
                    postgresContainer.getJdbcUrl() + "?stringtype=unspecified",
                    postgresContainer.getUsername(),
                    postgresContainer.getPassword());
        return connection;
    }

    @Before
    public void setup() throws IOException, SQLException {
        postgresContainer = new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("integration-tests-db")
                .withUsername("sa")
                .withPassword("sa");
        postgresContainer.start();

        String initDb = Resources.toString(
                Objects.requireNonNull(
                        PostgresTest.class.getClassLoader().getResource("mock/postgresmock.sql")),
                Charset.defaultCharset());
        Connection con = getConnection();
        try {
            Statement statement = con.createStatement();
            statement.execute(initDb);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        assertEquals("test", 1, 1);
    }

    @Test
    public void selectUsersMapManual() throws SQLException, InvalidSqlGenerationException {
        List<UserDao> list = new Runner<UserDao>(getConnection())
                .select(
                        SelectTest.baseQueryUsers("o", null, null),
                        rs -> {
                            try {
                                return new UserDao(rs.getInt("id"), rs.getString("name"), rs.getString("email"));
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
        System.out.println(list);
    }

    @Test
    public void selectUsersMapper() throws SQLException, InvalidSqlGenerationException {
        List<UserDao> list = new Runner<UserDao>(getConnection())
                .withDeleted(false)
                .select(
                        SelectTest.baseQueryUsers("o", null, null)
                                .setWithDeleted(false),
                        UserDao.class);
    }

    @Test
    public void selectUsersPaginationMapper()
            throws SQLException, InvalidCurrentPageException, InvalidSqlGenerationException {
        Template<List<UserDao>> paginated = new Runner<UserDao>(getConnection())
                .selectPaginated(
                        1,
                        10,
                        SelectTest.baseQueryUsers("o", null, null),
                        UserDao.class);

        System.out.println(paginated);
    }

    @Test
    public void insert() throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        new Runner<UserDao>(getConnection())
                .insert(UserDao.class, InsertTest.generateUser());
    }

    @Test
    public void updateDuplicated() throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        UserDao user = getUser(false, SelectTest.getUserById(1)).get(0);
        System.out.println(user);
        assertEquals("Check original value", user.getName(), "John Doe");

        new Runner<UserDao>(getConnection())
                .insert(UserDao.class, InsertTest.duplicatedUserUpdate());

        user = getUser(false, SelectTest.getUserById(1)).get(0);
        assertEquals("Check name was updated correctly", user.getName(), "Duplicated user");
        System.out.println(user);
    }

    List<UserDao> getUser(boolean withDeleted, Selector s) throws SQLException, InvalidSqlGenerationException {
        return new Runner<UserDao>(getConnection())
                .withDeleted(withDeleted)
                .select(
                        s,
                        UserDao.class);
    }

    void deleteCoreScenarios(boolean hardDelete) throws SQLException, InvalidSqlGenerationException {
        new Runner<Void>(getConnection())
                .delete(
                        new Delete()
                                .from("users")
                                .where("id = :id", p -> p.put("id", 1)),
                        hardDelete);
    }

    void deleteCoreScenarioEntity(UserDao user, boolean hardDelete) throws SQLException, InvalidSqlGenerationException {
        new Runner<UserDao>(getConnection())
                .delete(
                        user,
                        hardDelete);
    }

    @Test
    public void testSoftDelete() throws SQLException, InvalidSqlGenerationException {
        UserDao u2 = new UserDao();
        u2.setId(2);
        deleteCoreScenarioEntity(u2, false);
        UserDao user = getUser(true, SelectTest.getUserById(2)).get(0);
        assertNotNull(user.getDeletedAt());
        assertEquals("No user found", getUser(false, SelectTest.getUserById(2)).size(), 0);

        deleteCoreScenarios(false);
        user = getUser(true, SelectTest.getUserById(1)).get(0);
        assertNotNull(user.getDeletedAt());
        assertEquals("No user found", getUser(false, SelectTest.getUserById(1)).size(), 0);
    }

    // @Test
    // public void testHardDelete() throws SQLException,
    // InvalidSqlGenerationException {
    // deleteCoreScenarios(true);
    // assertEquals("No user found", getUser(false,
    // SelectTest.getUserById(1)).size(), 0);
    // assertEquals("No user found", getUser(true,
    // SelectTest.getUserById(1)).size(), 0);
    // }

}
