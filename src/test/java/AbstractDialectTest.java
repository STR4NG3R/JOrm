import common.DeleteTest;
import common.InsertTest;
import common.SelectTest;
import common.UpdateTest;
import dao.UserDao;
import dao.UuidItem;
import dao.UuidItemText;
import io.github.str4ng3r.common.Constants;
import io.github.str4ng3r.common.Order;
import io.github.str4ng3r.common.Selector;
import io.github.str4ng3r.common.Template;
import io.github.str4ng3r.exceptions.InvalidCurrentPageException;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import io.github.str4ng3r.sql.CursorPage;
import io.github.str4ng3r.sql.Page;
import io.github.str4ng3r.sql.Runner;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
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

    /**
     * Connection used by the UUID tests. Defaults to the shared one; PostgreSQL
     * overrides it with a strict connection (no {@code stringtype=unspecified}),
     * which would otherwise hide uuid-vs-varchar parameter type errors.
     */
    protected Connection getStrictConnection() throws SQLException {
        return getConnection();
    }

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
        assertEquals("page must contain exactly pageSize rows", 5, paginated.getData().size());

        // page 2 must be a different slice, not the same/unbounded rows
        Template<List<UserDao>> second = new Runner<UserDao>(getConnection())
                .selectPaginated(2, 5, SelectTest.allUsers(dialect()).orderBy("id", false), UserDao.class);
        assertEquals(5, second.getData().size());
        assertFalse(second.getData().get(0).getId().equals(paginated.getData().get(0).getId()));
    }

    // -------------------------------------------------------------------------
    // CURSOR (KEYSET) PAGINATION
    // -------------------------------------------------------------------------

    private Selector usersBy(String col, boolean desc) {
        Order order = desc ? Order.DESC : Order.ASC;
        return SelectTest.allUsers(dialect()).orderBy(col, order).orderBy("id", order);
    }

    private List<Integer> ids(List<UserDao> users) {
        List<Integer> ids = new ArrayList<>();
        for (UserDao u : users) ids.add(u.getId());
        return ids;
    }

    /** Walks every page with the cursor and returns the ids in the order received. */
    private List<Integer> walk(int pageSize, String col, boolean desc, boolean manual)
            throws SQLException, InvalidSqlGenerationException {
        List<Integer> seen = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 100; guard++) {
            Runner<UserDao> runner = new Runner<UserDao>(getConnection());
            CursorPage<UserDao> page = manual
                    ? runner.selectCursor(pageSize, cursor, usersBy(col, desc),
                            rs -> {
                                try {
                                    return new UserDao(rs.getInt("id"), rs.getString("name"), rs.getString("email"));
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    : runner.selectCursor(pageSize, cursor, usersBy(col, desc), UserDao.class);
            assertTrue("a page never exceeds pageSize", page.getData().size() <= pageSize);
            seen.addAll(ids(page.getData()));
            assertEquals(page.getNextCursor() != null, page.hasNext());
            if (!page.hasNext()) return seen;
            assertEquals("a page with a next cursor must be full", pageSize, page.getData().size());
            cursor = page.getNextCursor();
        }
        fail("cursor never ended");
        return seen;
    }

    @Test
    public void c2_cursorWalksEveryRowOnceWithTiebreaker()
            throws SQLException, InvalidSqlGenerationException {
        // role has many duplicates, so the id tiebreaker is what keeps pages stable
        List<Integer> expected = ids(getUser(true, usersBy("role", false)));
        assertTrue(expected.size() > 7);
        assertEquals(expected, walk(4, "role", false, false));
        assertEquals("page size dividing the total evenly", expected, walk(expected.size(), "role", false, false));
    }

    @Test
    public void c3_cursorOverTimestampColumnDescending()
            throws SQLException, InvalidSqlGenerationException {
        List<Integer> expected = ids(getUser(true, usersBy("createdAt", true)));
        assertEquals(expected, walk(6, "createdAt", true, false));
    }

    @Test
    public void c4_cursorWithManualMapping()
            throws SQLException, InvalidSqlGenerationException {
        List<Integer> expected = ids(getUser(true, usersBy("role", true)));
        assertEquals(expected, walk(5, "role", true, true));
    }

    @Test
    public void c4b_bothPaginationStylesShareTheSamePageType()
            throws SQLException, InvalidSqlGenerationException, InvalidCurrentPageException {
        Runner<UserDao> runner = new Runner<UserDao>(getConnection());
        Page<UserDao> offset = runner.selectPaginated(1, 5, usersBy("id", false), UserDao.class);
        Page<UserDao> cursor = runner.selectCursor(5, null, usersBy("id", false), UserDao.class);

        // same rows through the same type
        assertEquals(ids(cursor.getData()), ids(offset.getData()));
        assertEquals(Integer.valueOf(5), offset.getPageSize());
        assertEquals(Integer.valueOf(5), cursor.getPageSize());
        assertTrue(offset.hasNext());
        assertTrue(cursor.hasNext());

        // offset-only metadata
        assertEquals(Integer.valueOf(1), offset.getCurrentPage());
        assertNotNull(offset.getCount());
        assertNotNull(offset.getTotalPages());
        assertNull(offset.getNextCursor());
        // cursor-only metadata
        assertNotNull(cursor.getNextCursor());
        assertNull(cursor.getCurrentPage());
        assertNull(cursor.getCount());
        assertNull(cursor.getTotalPages());

        // offset: last page has no next
        Page<UserDao> last = runner.selectPaginated(offset.getTotalPages(), 5, usersBy("id", false), UserDao.class);
        assertFalse(last.hasNext());
    }

    @Test
    public void c4c_cursorWithCountFillsCountAndTotalPages()
            throws SQLException, InvalidSqlGenerationException {
        Runner<UserDao> runner = new Runner<UserDao>(getConnection());
        int total = getUser(true, usersBy("id", false)).size();

        // not requested -> not computed
        assertNull(runner.selectCursor(5, null, usersBy("id", false), UserDao.class).getCount());

        // chained opt-in on the first page
        Page<UserDao> first = runner.selectCursor(5, null, usersBy("id", false), UserDao.class).withCount();
        assertEquals(Integer.valueOf(total), first.getCount());
        assertEquals(Integer.valueOf((int) Math.ceil(total / 5.0)), first.getTotalPages());
        assertNull(first.getCurrentPage());
        assertEquals(5, first.getData().size());

        // the count ignores the cursor: page 2 reports the same total
        Page<UserDao> second = runner.selectCursor(5, first.getNextCursor(), usersBy("id", false), UserDao.class)
                .withCount();
        assertEquals(Integer.valueOf(total), second.getCount());
        assertEquals(first.getTotalPages(), second.getTotalPages());
        assertFalse(ids(second.getData()).contains(first.getData().get(0).getId()));
    }

    @Test
    public void c4d_cursorCountRespectsFiltersAndManualMapping()
            throws SQLException, InvalidSqlGenerationException {
        Runner<UserDao> runner = new Runner<UserDao>(getConnection());
        int admins = getUser(true, usersBy("id", false).andWhere("role = :role", p -> p.put("role", "admin"))).size();
        assertTrue(admins > 0);

        Page<UserDao> page = runner.selectCursor(1,
                null,
                usersBy("id", false).andWhere("role = :role", p -> p.put("role", "admin")),
                rs -> {
                    try {
                        return new UserDao(rs.getInt("id"), rs.getString("name"), rs.getString("email"));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }).withCount();
        assertEquals(Integer.valueOf(admins), page.getCount());
        assertEquals(Integer.valueOf(admins), page.getTotalPages()); // pageSize 1
    }

    @Test
    public void c4e_sameSelectorCanBeReusedAcrossPages()
            throws SQLException, InvalidSqlGenerationException {
        Runner<UserDao> runner = new Runner<UserDao>(getConnection());
        int total = getUser(true, usersBy("id", false)).size();
        Selector selector = usersBy("id", false);

        Page<UserDao> first = runner.selectCursor(5, null, selector, UserDao.class).withCount();
        Page<UserDao> second = runner.selectCursor(5, first.getNextCursor(), selector, UserDao.class).withCount();
        assertEquals(Integer.valueOf(total), first.getCount());
        assertEquals("count must not carry the previous cursor", Integer.valueOf(total), second.getCount());
        assertEquals(ids(getUser(true, usersBy("id", false))).subList(5, 10), ids(second.getData()));
    }

    @Test
    public void c5_cursorLastPageHasNoNextCursor() throws SQLException, InvalidSqlGenerationException {
        int total = getUser(true, usersBy("id", false)).size();
        CursorPage<UserDao> all = new Runner<UserDao>(getConnection())
                .selectCursor(total + 10, null, usersBy("id", false), UserDao.class);
        assertEquals(total, all.getData().size());
        assertFalse(all.hasNext());
        assertNull(all.getNextCursor());
    }

    @Test
    public void c6_invalidCursorIsRejected() throws SQLException, InvalidSqlGenerationException {
        try {
            new Runner<UserDao>(getConnection())
                    .selectCursor(5, "not-a-valid-cursor!", usersBy("id", false), UserDao.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("Invalid cursor", expected.getMessage());
        }
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

    // -------------------------------------------------------------------------
    // CURSOR WITH UUID KEYS
    // -------------------------------------------------------------------------

    private Selector uuidItems(boolean tiesFirst) {
        Selector s = new Selector()
                .select("uuid_items", "id", "name", "createdAt as \"createdAt\"")
                .setDialect(dialect());
        // either createdAt then uuid as tiebreaker, or the uuid alone
        return tiesFirst ? s.orderBy("createdAt", Order.ASC).orderBy("id", Order.ASC) : s.orderBy("id", Order.ASC);
    }

    private <E> List<Object> walkUuid(Class<E> clazz, boolean tiesFirst, int pageSize)
            throws SQLException, InvalidSqlGenerationException {
        List<Object> seen = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 50; guard++) {
            CursorPage<E> page = new Runner<E>(getStrictConnection())
                    .selectCursor(pageSize, cursor, uuidItems(tiesFirst), clazz);
            for (E e : page.getData())
                seen.add(e instanceof UuidItem ? ((UuidItem) e).getId() : ((UuidItemText) e).getId());
            if (!page.hasNext()) return seen;
            cursor = page.getNextCursor();
        }
        fail("cursor never ended");
        return seen;
    }

    private List<Object> expectedUuidOrder(boolean tiesFirst) throws SQLException, InvalidSqlGenerationException {
        List<Object> ids = new ArrayList<>();
        for (UuidItem i : new Runner<UuidItem>(getStrictConnection()).select(uuidItems(tiesFirst), UuidItem.class))
            ids.add(i.getId());
        return ids;
    }

    @Test
    public void v1_cursorWithUuidTypedIdAsTiebreaker() throws SQLException, InvalidSqlGenerationException {
        List<Object> expected = expectedUuidOrder(true);
        assertEquals(12, expected.size());
        assertEquals(expected, walkUuid(UuidItem.class, true, 5));
        assertEquals(expected, walkUuid(UuidItem.class, true, 1));
    }

    @Test
    public void v2_cursorOrderedByUuidAlone() throws SQLException, InvalidSqlGenerationException {
        List<Object> expected = expectedUuidOrder(false);
        assertEquals(12, expected.size());
        assertEquals(expected, walkUuid(UuidItem.class, false, 4));
    }

    @Test
    public void v3_cursorWithUuidMappedToStringField() throws SQLException, InvalidSqlGenerationException {
        List<Object> expected = new ArrayList<>();
        for (Object id : expectedUuidOrder(true)) expected.add(id.toString());
        assertEquals(expected, walkUuid(UuidItemText.class, true, 5));
    }

    @Test
    public void v4_cursorWithUuidAndManualMapping() throws SQLException, InvalidSqlGenerationException {
        List<Object> expected = new ArrayList<>();
        for (Object id : expectedUuidOrder(true)) expected.add(id.toString());

        List<Object> seen = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 50; guard++) {
            CursorPage<String> page = new Runner<String>(getStrictConnection())
                    .selectCursor(5, cursor, uuidItems(true), rs -> {
                        try {
                            return rs.getString("id");
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    });
            seen.addAll(page.getData());
            if (!page.hasNext()) break;
            cursor = page.getNextCursor();
        }
        assertEquals(expected, seen);
    }
}
