import common.DeleteTest;
import common.InsertTest;
import common.SelectTest;
import common.UpdateTest;
import dao.UserDao;
import io.github.str4ng3r.common.Constants;
import io.github.str4ng3r.common.Selector;
import io.github.str4ng3r.common.Template;
import io.github.str4ng3r.exceptions.InvalidCurrentPageException;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import io.github.str4ng3r.sql.Runner;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.*;

/**
 * Dialect-agnostic integration test suite. Each concrete subclass wires up a
 * database (via Testcontainers), provides its {@link Connection}, its
 * {@link Constants.SqlDialect} and loads its own mock schema. All the actual
 * test cases live here and run against every supported database.
 *
 * @author Pablo Eduardo Martinez Solis
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class AbstractDialectTest {

    /** Live JDBC connection to the running container. */
    protected abstract Connection getConnection() throws SQLException;

    /** SQL dialect used to build the queries for this database. */
    protected abstract Constants.SqlDialect dialect();

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    protected List<UserDao> getUser(boolean withDeleted, Selector s)
            throws SQLException, InvalidSqlGenerationException {
        return new Runner<UserDao>(getConnection())
                .withDeleted(withDeleted)
                .select(s, UserDao.class);
    }

    // -------------------------------------------------------------------------
    // SELECT
    // -------------------------------------------------------------------------

    @Test
    public void a_selectManualMapping() throws SQLException, InvalidSqlGenerationException {
        List<UserDao> list = new Runner<UserDao>(getConnection())
                .enableMetrics("get_users_manual_map")
                .select(SelectTest.getUserById(dialect(), 4), rs -> {
                    try {
                        return new UserDao(rs.getInt("id"), rs.getString("name"), rs.getString("email"));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
        assertFalse("manual mapping should return the user", list.isEmpty());
        assertEquals(Integer.valueOf(4), list.get(0).getId());
    }

    @Test
    public void b_selectAutoMapping() throws SQLException, InvalidSqlGenerationException {
        List<UserDao> list = new Runner<UserDao>(getConnection())
                .select(SelectTest.allUsers(dialect()), UserDao.class);
        assertTrue("auto mapping should return users", list.size() > 0);
    }

    @Test
    public void c_selectPaginated()
            throws SQLException, InvalidCurrentPageException, InvalidSqlGenerationException {
        Template<List<UserDao>> paginated = new Runner<UserDao>(getConnection())
                .selectPaginated(1, 5, SelectTest.allUsers(dialect()), UserDao.class);
        assertNotNull(paginated);
        assertFalse(paginated.getData().isEmpty());
        assertEquals(Integer.valueOf(5), paginated.getPageSize());
    }

    @Test
    public void d_selectWhereIn() throws SQLException, InvalidSqlGenerationException {
        List<UserDao> list = getUser(true, SelectTest.usersByIds(dialect(), Arrays.asList(1, 2, 3)));
        assertEquals("WHERE IN should return exactly 3 users", 3, list.size());
    }

    // -------------------------------------------------------------------------
    // INSERT / UPSERT / BATCH
    // -------------------------------------------------------------------------

    @Test
    public void e_insertSetsCreatedAt()
            throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        new Runner<UserDao>(getConnection())
                .insert(UserDao.class, InsertTest.newUser("InsertOne", "insertone@example.com"));

        List<UserDao> found = getUser(true, SelectTest.userByEmail(dialect(), "insertone@example.com"));
        assertFalse(found.isEmpty());
        assertNotNull("createdAt must be set on insert", found.get(0).getCreatedAt());
    }

    @Test
    public void f_batchInsert()
            throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        new Runner<UserDao>(getConnection())
                .insert(UserDao.class, InsertTest.userBatch("Batch", 5), 3);

        List<UserDao> found = getUser(true, SelectTest.userByEmail(dialect(), "batch1@example.com"));
        assertFalse("first batch user should exist", found.isEmpty());
        assertEquals("Batch1", found.get(0).getName());
    }

    @Test
    public void g_upsertUpdatesWhenIdPresent()
            throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        UserDao user = getUser(true, SelectTest.getUserById(dialect(), 5)).get(0);
        user.setName("UpsertUpdated");

        new Runner<UserDao>(getConnection())
                .insert(UserDao.class, user);

        UserDao updated = getUser(true, SelectTest.getUserById(dialect(), 5)).get(0);
        assertEquals("UpsertUpdated", updated.getName());
        assertNotNull("updatedAt must be set on update", updated.getUpdatedAt());
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @Test
    public void h_updateBuilder() throws SQLException, InvalidSqlGenerationException {
        int affected = new Runner<Void>(getConnection())
                .update(UpdateTest.updateName(dialect(), 1, "UpdatedByBuilder"));
        assertEquals(1, affected);

        UserDao user = getUser(true, SelectTest.getUserById(dialect(), 1)).get(0);
        assertEquals("UpdatedByBuilder", user.getName());
    }

    @Test
    public void i_updateExcludeColumns() throws SQLException, InvalidSqlGenerationException {
        UserDao before = getUser(true, SelectTest.getUserById(dialect(), 2)).get(0);
        String originalPassword = before.getPassword();

        int affected = new Runner<Void>(getConnection())
                .update(UpdateTest.updateExcludingPassword(dialect(), 2, "ExcludedColsUser", "should-not-save"));
        assertEquals(1, affected);

        UserDao after = getUser(true, SelectTest.getUserById(dialect(), 2)).get(0);
        assertEquals("ExcludedColsUser", after.getName());
        assertEquals("password must be unchanged (excluded)", originalPassword, after.getPassword());
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @Test
    public void j_softDeleteByBuilder() throws SQLException, InvalidSqlGenerationException {
        new Runner<Void>(getConnection())
                .delete(DeleteTest.deleteUserById(dialect(), 7), false);

        UserDao user = getUser(true, SelectTest.getUserById(dialect(), 7)).get(0);
        assertNotNull("deletedAt should be set after soft delete", user.getDeletedAt());
        assertTrue("soft-deleted user must be hidden without withDeleted",
                getUser(false, SelectTest.getUserById(dialect(), 7)).isEmpty());
    }

    @Test
    public void k_softDeleteByEntity() throws SQLException, InvalidSqlGenerationException {
        new Runner<UserDao>(getConnection())
                .delete(DeleteTest.userWithId(8), false);

        UserDao user = getUser(true, SelectTest.getUserById(dialect(), 8)).get(0);
        assertNotNull(user.getDeletedAt());
        assertTrue(getUser(false, SelectTest.getUserById(dialect(), 8)).isEmpty());
    }

    @Test
    public void l_hardDeleteByBuilder() throws SQLException, InvalidSqlGenerationException {
        new Runner<Void>(getConnection())
                .delete(DeleteTest.deleteUserById(dialect(), 9), true);
        assertTrue("hard-deleted user must not exist",
                getUser(true, SelectTest.getUserById(dialect(), 9)).isEmpty());
    }

    @Test
    public void m_hardDeleteByEntity() throws SQLException, InvalidSqlGenerationException {
        new Runner<UserDao>(getConnection())
                .delete(DeleteTest.userWithId(10), true);
        assertTrue(getUser(true, SelectTest.getUserById(dialect(), 10)).isEmpty());
    }

    // -------------------------------------------------------------------------
    // TRANSACTIONS
    // -------------------------------------------------------------------------

    @Test
    public void n_transactionCommits() throws Exception {
        new Runner<UserDao>(getConnection()).transaction(runner ->
                runner.insert(UserDao.class, InsertTest.newUser("TxUser", "txuser@example.com")));

        assertFalse(getUser(true, SelectTest.userByEmail(dialect(), "txuser@example.com")).isEmpty());
    }

    @Test
    public void o_transactionRollsBack() throws Exception {
        try {
            new Runner<UserDao>(getConnection()).transaction(runner -> {
                runner.insert(UserDao.class, InsertTest.newUser("Rollback", "rollback@example.com"));
                throw new RuntimeException("boom");
            });
        } catch (SQLException ignored) {
            // expected: wrapped and rethrown after rollback
        }
        assertTrue("rolled-back user must not exist",
                getUser(true, SelectTest.userByEmail(dialect(), "rollback@example.com")).isEmpty());
    }

    @Test
    public void p_transactionCommitsWithIsolationLevel() throws Exception {
        new Runner<UserDao>(getConnection()).transaction(Runner.ISOLATION.READ_COMMITTED, runner ->
                runner.insert(UserDao.class, InsertTest.newUser("TxIsolation", "tx.isolation@example.com")));

        assertFalse("committed user with isolation level must exist",
                getUser(true, SelectTest.userByEmail(dialect(), "tx.isolation@example.com")).isEmpty());
    }

    @Test
    public void q_transactionRollsBackWithIsolationLevel() throws Exception {
        try {
            new Runner<UserDao>(getConnection()).transaction(Runner.ISOLATION.SERIALIZABLE, runner -> {
                runner.insert(UserDao.class, InsertTest.newUser("TxIsoRollback", "tx.iso.rollback@example.com"));
                throw new RuntimeException("boom");
            });
        } catch (SQLException ignored) {
            // expected: wrapped and rethrown after rollback
        }
        assertTrue("rolled-back user (isolation level) must not exist",
                getUser(true, SelectTest.userByEmail(dialect(), "tx.iso.rollback@example.com")).isEmpty());
    }

    @Test
    public void r_manualTransactionCommits() throws Exception {
        Runner<UserDao> runner = new Runner<UserDao>(getConnection());
        runner.beginTransaction();
        try {
            runner.insert(UserDao.class, InsertTest.newUser("ManualCommit", "manual.commit@example.com"));
            runner.commit();
        } catch (Exception e) {
            runner.rollback();
            throw e;
        }
        assertFalse("manually committed user must exist",
                getUser(true, SelectTest.userByEmail(dialect(), "manual.commit@example.com")).isEmpty());
    }

    @Test
    public void s_manualTransactionRollsBack() throws Exception {
        Runner<UserDao> runner = new Runner<UserDao>(getConnection());
        runner.beginTransaction();
        try {
            runner.insert(UserDao.class, InsertTest.newUser("ManualRollback", "manual.rollback@example.com"));
            throw new RuntimeException("boom");
        } catch (Exception e) {
            runner.rollback();
        }
        assertTrue("manually rolled-back user must not exist",
                getUser(true, SelectTest.userByEmail(dialect(), "manual.rollback@example.com")).isEmpty());
    }

    @Test
    public void t_rollbackDiscardsAllOperationsInTransaction() throws Exception {
        try {
            new Runner<UserDao>(getConnection()).transaction(runner -> {
                runner.insert(UserDao.class, InsertTest.newUser("MultiA", "multi.a@example.com"));
                runner.insert(UserDao.class, InsertTest.newUser("MultiB", "multi.b@example.com"));
                throw new RuntimeException("boom after two inserts");
            });
        } catch (SQLException ignored) {
            // expected
        }
        assertTrue("first insert must be rolled back",
                getUser(true, SelectTest.userByEmail(dialect(), "multi.a@example.com")).isEmpty());
        assertTrue("second insert must be rolled back",
                getUser(true, SelectTest.userByEmail(dialect(), "multi.b@example.com")).isEmpty());
    }

    @Test
    public void u_autoCommitRestoredAfterTransaction() throws Exception {
        Connection con = getConnection();
        boolean autoCommitBefore = con.getAutoCommit();

        new Runner<UserDao>(getConnection()).transaction(runner ->
                runner.insert(UserDao.class, InsertTest.newUser("AutoCommitCheck", "autocommit.check@example.com")));

        assertEquals("autoCommit must be restored to its original value after the transaction",
                autoCommitBefore, con.getAutoCommit());

        // A subsequent insert outside any transaction must persist immediately,
        // proving the connection is back in autoCommit mode.
        new Runner<UserDao>(getConnection())
                .insert(UserDao.class, InsertTest.newUser("PostTx", "posttx@example.com"));
        assertFalse("insert after transaction (autoCommit) must persist",
                getUser(true, SelectTest.userByEmail(dialect(), "posttx@example.com")).isEmpty());
    }
}
