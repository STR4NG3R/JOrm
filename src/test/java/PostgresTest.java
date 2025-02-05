

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

import static junit.framework.TestCase.assertEquals;

public class PostgresTest {
    @ClassRule
    public static PostgreSQLContainer postgresContainer;

    Connection connection;

    Connection getConnection() throws SQLException {
        if (connection == null)
            connection = DriverManager.getConnection(
                    postgresContainer.getJdbcUrl(),
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
                Charset.defaultCharset()
        );
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
        List<UserDao> list = new Runner<UserDao>()
                .setConnection(getConnection())
                .select(
                        SelectTest.baseQueryUsers("o", null, null),
                        rs -> {
                            try {
                                return new UserDao(rs.getInt("id"), rs.getString("name"), rs.getString("email"));
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }
                );
        System.out.println(list);
    }

    @Test
    public void selectUsersMapper() throws SQLException, InvalidSqlGenerationException {
        List<UserDao> list = new Runner<UserDao>()
                .setConnection(getConnection())
                .select(
                        SelectTest.baseQueryUsers("o", null, null), UserDao.class
                );
    }

    @Test
    public void selectUsersPaginationMapper() throws SQLException, InvalidCurrentPageException, InvalidSqlGenerationException {
        Template<List<UserDao>> paginated = new Runner<UserDao>()
                .setConnection(getConnection())
                .selectPaginated(
                        1,
                        10,
                        SelectTest.baseQueryUsers("o", null, null),
                        UserDao.class
                );

        System.out.println(paginated);
    }


    @Test
    public void insert() throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        new Runner<UserDao>()
                .setConnection(getConnection())
                .insert(UserDao.class, InsertTest.generateUser());
    }

    @Test
    public void updateDuplicated() throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        UserDao user = new Runner<UserDao>()
                .setConnection(getConnection())
                .select(
                        new Selector()
                                .select("users", "id", "name", "email", "password", "role")
                                .where("id = :id", (p) -> p.put("id", 1)),
                        UserDao.class
                ).get(0);

        System.out.println(user);

        new Runner<UserDao>()
                .setConnection(getConnection())
                .insert(UserDao.class, InsertTest.duplicatedUserUpdate());

        user = new Runner<UserDao>()
                .setConnection(getConnection())
                .select(
                        new Selector()
                                .select("users", "id", "name", "email", "password", "role")
                                .where("id = :id", (p) -> p.put("id", 1)),
                        UserDao.class
                ).get(0);

        System.out.println(user);
    }

    @Test
    public void testDelete() throws SQLException, InvalidSqlGenerationException {
        Delete d = new Delete()
                .from("users ")
                .where("id = :id", p -> p.put("id", 1));
        System.out.println(d.getSqlAndParameters());

        int result = new Runner<UserDao>()
                .setConnection(getConnection())
                .delete(d);
        System.out.println(result);
    }
}
