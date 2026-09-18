
import common.InsertTest;
import common.SelectTest;
import dao.UserDao;
import io.github.str4ng3r.common.*;
import io.github.str4ng3r.exceptions.InvalidCurrentPageException;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import io.github.str4ng3r.sql.Runner;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.com.google.common.io.Resources;

import java.io.IOException;

import java.nio.charset.Charset;
import java.sql.*;
import java.util.List;
import java.util.Objects;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PostgresTest {
    @ClassRule
    public static PostgreSQLContainer<?> postgresContainer;
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
        System.setProperty("api.version", "1.44");

        postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
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
    public void a_selectUsersMapManual() throws SQLException, InvalidSqlGenerationException {
        List<UserDao> list = new Runner<UserDao>(getConnection())
                .enableLogs()
                .enableMetrics("get_users_manual_map")
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
    public void b_selectUsersMapper() throws SQLException, InvalidSqlGenerationException {
        List<UserDao> list = new Runner<UserDao>(getConnection())
                .withDeleted(false)
                .enableLogs()
                .enableMetrics("insert_user")
                .select(
                        SelectTest.baseQueryUsers("o", null, null),
                        UserDao.class);
        System.out.println(list);
    }

    @Test
    public void c_selectUsersPaginationMapper()
            throws SQLException, InvalidCurrentPageException, InvalidSqlGenerationException {
        Template<List<UserDao>> paginated = new Runner<UserDao>(getConnection())
                .enableLogs()
                .enableMetrics("get_list_users_paginated")
                .selectPaginated(
                        1,
                        10,
                        SelectTest.baseQueryUsers("o", null, null),
                        UserDao.class);

        System.out.println(paginated);
    }

    @Test
    public void d_insert() throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        new Runner<UserDao>(getConnection())
                .enableLogs()
                .enableMetrics("insert_user")
                .insert(UserDao.class, InsertTest.generateUser());
    }

    @Test
    public void e_updateDuplicated() throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        UserDao user = getUser(true, SelectTest.getUserById(6)).get(0);
        System.out.println(user);
        assertEquals("Check original value", user.getName(), "Daisy Green");

        user.setName("Pablo");
        new Runner<UserDao>(getConnection())
                .enableLogs()
                .enableMetrics("update_user")
                .insert(UserDao.class, user);

        user = getUser(true, SelectTest.getUserById(6)).get(0);
        System.out.println(user);
        assertEquals("Check name was updated correctly", user.getName(), "Pablo");
    }

    List<UserDao> getUser(boolean withDeleted, Selector s) throws SQLException, InvalidSqlGenerationException {
        return new Runner<UserDao>(getConnection())
                .enableLogs()
                .enableMetrics("get_user")
                .withDeleted(withDeleted)
                .select(
                        s,
                        UserDao.class);
    }

    int deleteCoreScenarios(boolean hardDelete, int id) throws SQLException, InvalidSqlGenerationException {
        return new Runner<Void>(getConnection())
                .enableLogs()
                .enableMetrics("delete_user")
                .delete(
                        new Delete()
                                .from("users")
                                .where("id = :id", p -> p.put("id", id)),
                        hardDelete);
    }

    int deleteCoreScenarioEntity(UserDao user, boolean hardDelete) throws SQLException, InvalidSqlGenerationException {
        return new Runner<UserDao>(getConnection())
                .enableLogs()
                .enableMetrics("delete_user_entity")
                .delete(
                        user,
                        hardDelete);
    }

    @Test
    public void f_testSoftDelete() throws SQLException, InvalidSqlGenerationException {
        UserDao u2 = new UserDao();
        u2.setId(2);
        deleteCoreScenarioEntity(u2, false);
        UserDao user = getUser(true, SelectTest.getUserById(2)).get(0);
        assertNotNull(user.getDeletedAt());
        assertEquals("No user found", getUser(false, SelectTest.getUserById(2)).size(), 0);

        deleteCoreScenarios(false, 3);
        user = getUser(true, SelectTest.getUserById(3)).get(0);
        assertNotNull(user.getDeletedAt());
        assertEquals("No user found", getUser(false, SelectTest.getUserById(3)).size(), 0);
    }

    @Test
    public void g_testHardDelete() throws SQLException, InvalidSqlGenerationException {
        deleteCoreScenarios(true, 1);
        assertEquals("No user found hard deleted", getUser(true, SelectTest.getUserById(1)).size(), 0);

        UserDao u = new UserDao();
        u.setId(2);
        deleteCoreScenarioEntity(u, true);
        assertEquals("No user found hard deleted", getUser(true, SelectTest.getUserById(2)).size(), 0);
    }

}
