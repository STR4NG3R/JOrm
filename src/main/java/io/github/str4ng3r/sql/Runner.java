package io.github.str4ng3r.sql;

import io.github.str4ng3r.common.*;
import io.github.str4ng3r.exceptions.InvalidCurrentPageException;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Runner<T> extends CommonRunner {

    public Runner(Connection connection) {
        super(connection);
        withDeleted = true;
    }

    public Runner<T> enableMetrics(String alias) {
        this.alias = alias;
        jormLogger.setEnableMetrics(true);
        return this;
    }

    public Runner<T> enableLogs() {
        jormLogger.setEnable(true);
        return this;
    }

    public Runner<T> hardDelete(boolean hardDelete) {
        this.hardDelete = hardDelete;
        return this;
    }

    public Runner<T> withDeleted(boolean withDeleted) {
        this.withDeleted = withDeleted;
        return this;
    }

    // -------------------------------------------------------------------------
    // Transactions
    // -------------------------------------------------------------------------

    public enum ISOLATION {
        NONE(Connection.TRANSACTION_NONE),
        READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
        READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
        REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
        SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

        final int isolationValue;

        ISOLATION(int value) {
            isolationValue = value;
        }
    }

    /**
     * Begins a transaction on the underlying connection.
     * Disables auto-commit and optionally sets the isolation level.
     */
    public Runner<T> beginTransaction() throws SQLException {
        autoCommitBeforeTx = getConnection().getAutoCommit();
        getConnection().setAutoCommit(false);
        return this;
    }

    /**
     * Begins a transaction with a specific isolation level.
     */
    public Runner<T> beginTransaction(ISOLATION isolation) throws SQLException {
        getConnection().setTransactionIsolation(isolation.isolationValue);
        autoCommitBeforeTx = getConnection().getAutoCommit();
        getConnection().setAutoCommit(false);
        return this;
    }

    /**
     * Commits the current transaction and restores the connection's original
     * autoCommit state (instead of forcing it to true).
     */
    public Runner<T> commit() throws SQLException {
        getConnection().commit();
        restoreAutoCommit();
        return this;
    }

    /**
     * Rolls back the current transaction and restores the connection's original
     * autoCommit state (instead of forcing it to true).
     */
    public Runner<T> rollback() throws SQLException {
        getConnection().rollback();
        restoreAutoCommit();
        return this;
    }

    private void restoreAutoCommit() throws SQLException {
        // Restore the state captured at beginTransaction; default to true if the
        // transaction was managed manually without going through beginTransaction.
        boolean restore = autoCommitBeforeTx == null ? true : autoCommitBeforeTx;
        getConnection().setAutoCommit(restore);
        autoCommitBeforeTx = null;
    }

    /**
     * Executes a block of operations inside a transaction.
     * Commits automatically on success, rolls back on any exception.
     *
     * Example:
     * <pre>
     * new Runner&lt;Void&gt;(connection).transaction(runner -> {
     *     runner.insert(UserDao.class, user);
     *     runner.insert(OrderDao.class, order);
     * });
     * </pre>
     */
    public void transaction(TransactionCallback<T> callback) throws SQLException {
        beginTransaction();
        try {
            callback.execute(this);
            commit();
        } catch (Exception e) {
            rollback();
            throw new SQLException("Transaction rolled back due to: " + e.getMessage(), e);
        }
    }

    /**
     * Same as {@link #transaction(TransactionCallback)} but with a specific isolation level.
     */
    public void transaction(ISOLATION isolation, TransactionCallback<T> callback) throws SQLException {
        beginTransaction(isolation);
        try {
            callback.execute(this);
            commit();
        } catch (Exception e) {
            rollback();
            throw new SQLException("Transaction rolled back due to: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        void execute(Runner<T> runner) throws Exception;
    }

    /**
     * Configures soft-delete filtering on the selector using querybuilder4j's
     * native per-table support. Builds the table-name -> deletedAt-column map from
     * ONLY the tables the selector actually references (resolved via
     * selector.getTableNames()), so jsqb filters just those that have a @DeletedAt
     * column. Avoids scanning the whole entity registry.
     */
    private void applySoftDelete(Selector selector) {
        if (withDeleted) return;
        java.util.Map<String, String> softDeleteColumns = new java.util.HashMap<>();
        for (String table : selector.getTableNames()) {
            String col = resolveDeletedAtColumn(table);
            if (col != null) softDeleteColumns.put(table, col);
        }
        if (!softDeleteColumns.isEmpty())
            selector.setWithDeleted(false).setSoftDeleteColumns(softDeleteColumns);
    }

    /**
     * Ensures the entity is registered so its soft-delete metadata is available,
     * and returns its @DeletedAt column (or null).
     */
    private String deletedAtColumnFor(Class<T> clazz) {
        if (clazz == null || !clazz.isAnnotationPresent(io.github.str4ng3r.Entity.class))
            return null;
        if (!ScannerEntity.entitiesRegistry.containsKey(clazz))
            ScannerEntity.registryEntity(clazz);
        EntityMetaData meta = ScannerEntity.entitiesRegistry.get(clazz);
        return meta != null ? meta.columnDeletedAt : null;
    }

    public List<T> select(Selector selector, Function<ResultSet, T> consumer)
            throws InvalidSqlGenerationException, SQLException {
        applySoftDelete(selector);
        return jdbcUtils.query(selector, getConnection(), alias, rs -> {
            ArrayList<T> list = new ArrayList<>();
            while (rs.next())
                list.add(consumer.apply(rs));
            return list;
        });
    }

    public List<T> select(Selector selector, Class<T> clazz) throws SQLException, InvalidSqlGenerationException {
        // Ensure the entity (and thus its soft-delete metadata) is registered.
        deletedAtColumnFor(clazz);
        applySoftDelete(selector);
        Mapper<T> mapper = mapper();
        return jdbcUtils.query(selector, getConnection(), alias,
                rs -> mapper.mapFromResultSet(rs, clazz));
    }

    public OffsetPage<T> selectPaginated(int currentPage, int pageSize, Selector selector, Class<T> clazz)
            throws InvalidSqlGenerationException, SQLException, InvalidCurrentPageException {
        deletedAtColumnFor(clazz);
        applySoftDelete(selector);
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        int count = jdbcUtils.getCount(getConnection(), selector, sqlParameter, alias);
        selector.setPagination(sqlParameter, new Pagination(pageSize, count, currentPage));
        Mapper<T> mapper = mapper();
        List<T> data = jdbcUtils.query(sqlParameter, getConnection(), alias,
                rs -> mapper.mapFromResultSet(rs, clazz));
        return new OffsetPage<>(sqlParameter, data);
    }

    public OffsetPage<T> selectPaginated(int currentPage, int pageSize, Selector selector,
            Function<ResultSet, T> consumer)
            throws SQLException, InvalidSqlGenerationException, InvalidCurrentPageException {
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        int count = jdbcUtils.getCount(getConnection(), selector, sqlParameter, alias);
        selector.setPagination(sqlParameter, new Pagination(pageSize, count, currentPage));
        List<T> data = jdbcUtils.query(sqlParameter, getConnection(), alias, rs -> {
            ArrayList<T> list = new ArrayList<>();
            while (rs.next())
                list.add(consumer.apply(rs));
            return list;
        });
        return new OffsetPage<>(sqlParameter, data);
    }

    /**
     * Cursor (keyset) pagination with automatic entity mapping. No COUNT and no
     * OFFSET: the cost per page is constant regardless of depth.
     * <p>
     * The selector must declare its ORDER BY, ending in a unique column (e.g. the
     * id), and those columns must be NOT NULL. Order columns are matched to entity
     * fields by name (a qualifier like {@code u.createdAt} is ignored).
     *
     * @param pageSize max rows in the returned page
     * @param cursor   token from the previous page's {@link CursorPage#getNextCursor()},
     *                 or null/empty for the first page
     */
    public CursorPage<T> selectCursor(int pageSize, String cursor, Selector selector, Class<T> clazz)
            throws InvalidSqlGenerationException, SQLException {
        deletedAtColumnFor(clazz);
        applySoftDelete(selector);
        List<String> labels = cursorLabels(selector);
        CursorPage.Counter counter = cursorCounter(selector);
        // pageSize + 1: the extra row only tells us whether a next page exists.
        SqlParameter sqlParameter = selector.keyset(checkedPageSize(pageSize) + 1, CursorCodec.decode(cursor))
                .getSqlAndParameters();
        Mapper<T> mapper = mapper();
        List<String> sqlTypes = new ArrayList<>();
        List<T> rows = jdbcUtils.query(sqlParameter, getConnection(), alias, rs -> {
            collectSqlTypes(rs, labels, sqlTypes);
            return mapper.mapFromResultSet(rs, clazz);
        });
        boolean hasNext = rows.size() > pageSize;
        List<T> data = hasNext ? new ArrayList<>(rows.subList(0, pageSize)) : rows;
        String next = null;
        if (hasNext) {
            T last = data.get(pageSize - 1);
            List<Object> values = new ArrayList<>();
            for (int i = 0; i < labels.size(); i++)
                values.add(cursorValue(fieldValue(clazz, last, labels.get(i)), sqlTypes.get(i)));
            next = CursorCodec.encode(values);
        }
        return new CursorPage<>(data, pageSize, next, counter);
    }

    /**
     * Cursor (keyset) pagination with manual mapping. The order columns are read
     * from the ResultSet by name, so they must be present in the SELECT list
     * (use the alias if you aliased them).
     *
     * @see #selectCursor(int, String, Selector, Class)
     */
    public CursorPage<T> selectCursor(int pageSize, String cursor, Selector selector,
            Function<ResultSet, T> consumer) throws InvalidSqlGenerationException, SQLException {
        applySoftDelete(selector);
        List<String> labels = cursorLabels(selector);
        CursorPage.Counter counter = cursorCounter(selector);
        SqlParameter sqlParameter = selector.keyset(checkedPageSize(pageSize) + 1, CursorCodec.decode(cursor))
                .getSqlAndParameters();
        return jdbcUtils.query(sqlParameter, getConnection(), alias, rs -> {
            List<String> sqlTypes = new ArrayList<>();
            collectSqlTypes(rs, labels, sqlTypes);
            List<T> data = new ArrayList<>();
            List<Object> lastValues = null;
            boolean hasNext = false;
            while (rs.next()) {
                if (data.size() == pageSize) {
                    hasNext = true;
                    break;
                }
                data.add(consumer.apply(rs));
                if (data.size() == pageSize) {
                    lastValues = new ArrayList<>();
                    for (int i = 0; i < labels.size(); i++)
                        lastValues.add(cursorValue(readCursorColumn(rs, labels.get(i)), sqlTypes.get(i)));
                }
            }
            return new CursorPage<>(data, pageSize, hasNext ? CursorCodec.encode(lastValues) : null, counter);
        });
    }

    /**
     * Captures the base query (before the keyset predicate is applied) so that
     * {@link CursorPage#withCount()} can count the whole result set later.
     */
    private CursorPage.Counter cursorCounter(Selector selector) throws InvalidSqlGenerationException {
        // A reused selector may still carry the previous page's cursor; count the whole set.
        SqlParameter base = selector.clearKeyset().getSqlAndParameters();
        return () -> jdbcUtils.getCount(getConnection(), selector, base, alias);
    }

    /**
     * Makes a cursor value bindable on every database. Only PostgreSQL can bind a
     * {@code java.util.UUID} (to a native {@code uuid} column); MySQL and Oracle
     * reject it, so a UUID over a text column travels as its String form. The
     * opposite case, a String over a native uuid column, is turned into a UUID
     * because PostgreSQL does not compare {@code uuid} with {@code varchar}.
     */
    private static Object cursorValue(Object value, String sqlType) {
        boolean nativeUuid = "uuid".equalsIgnoreCase(sqlType);
        if (value instanceof String && nativeUuid)
            return java.util.UUID.fromString((String) value);
        if (value instanceof java.util.UUID && !nativeUuid)
            return value.toString();
        return value;
    }

    /**
     * Reads an order column asking the driver for standard JDBC types, so vendor
     * classes (e.g. oracle.sql.TIMESTAMP) never end up in the cursor.
     */
    private static Object readCursorColumn(ResultSet rs, String label) throws SQLException {
        int index = rs.findColumn(label);
        int type = rs.getMetaData().getColumnType(index);
        if (type == Types.TIMESTAMP || type == Types.TIMESTAMP_WITH_TIMEZONE)
            return rs.getTimestamp(index);
        if (type == Types.DATE)
            return rs.getDate(index);
        return rs.getObject(index);
    }

    /** Database type name of each order column (null when it cannot be resolved). */
    private static void collectSqlTypes(ResultSet rs, List<String> labels, List<String> out) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        for (String label : labels) {
            String type = null;
            try {
                type = md.getColumnTypeName(rs.findColumn(label));
            } catch (SQLException ignored) {
                // column not in the select list: nothing to type
            }
            out.add(type);
        }
    }

    private static int checkedPageSize(int pageSize) {
        if (pageSize < 1)
            throw new IllegalArgumentException("The page size must be greater than 0");
        return pageSize;
    }

    /** Result-set labels of the ORDER BY columns: strips qualifier and quotes. */
    private static List<String> cursorLabels(Selector selector) {
        List<String> labels = new ArrayList<>();
        for (String column : selector.getOrderColumns()) {
            String label = column.substring(column.lastIndexOf('.') + 1).replace("\"", "").replace("`", "").trim();
            if (!label.matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw new IllegalArgumentException("Cursor pagination needs plain column names in ORDER BY, got: " + column);
            labels.add(label);
        }
        if (labels.isEmpty())
            throw new IllegalArgumentException("Cursor pagination requires an ORDER BY on the selector");
        return labels;
    }

    private Object fieldValue(Class<T> clazz, T entity, String label) {
        try {
            EntityMetaData meta = ScannerEntity.entitiesRegistry.get(clazz);
            java.lang.reflect.Field field = meta != null && meta.getFieldCache() != null
                    ? meta.getFieldCache().get(label) : null;
            if (field == null)
                field = clazz.getDeclaredField(label);
            field.setAccessible(true);
            return field.get(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Order column '" + label + "' has no matching field in "
                    + clazz.getSimpleName() + "; select it under that name or use the manual-mapping overload", e);
        }
    }

    public int update(Update update) throws InvalidSqlGenerationException, SQLException {
        SqlParameter sqlParameter = update.getSqlAndParameters();
        jormLogger.info(sqlParameter.toString());
        jormLogger.startRecord(alias);
        try (PreparedStatement ps = getConnection().prepareStatement(sqlParameter.getSql())) {
            jdbcUtils.addParameters(ps, sqlParameter.getListParameters());
            int res = ps.executeUpdate();
            jormLogger.endRecord(alias);
            return res;
        }
    }

    protected int commonUpdate(Update update, EntityMetaData processedEntity)
            throws InvalidSqlGenerationException, SQLException {
        for (int i = 0; i < processedEntity.getColumns().size(); i++) {
            Object value = processedEntity.getValues().get(i);
            if (value != null) {
                String column = processedEntity.getColumns().get(i);
                update.setColumnsValuesToUpdate((cv) -> cv.put(column, value));
            }
        }

        if (processedEntity.getColumnUpdatedAt() != null) {
            update.setColumnsValuesToUpdate(
                    p -> p.put(processedEntity.getColumnUpdatedAt(), new Date(new java.util.Date().getTime())));
        }

        SqlParameter sqlParameter = update.getSqlAndParameters();
        jormLogger.info(sqlParameter.toString());
        jormLogger.startRecord(alias);
        try (PreparedStatement ps = getConnection().prepareStatement(sqlParameter.getSql())) {
            jdbcUtils.addParameters(ps, sqlParameter.getListParameters());
            int res = ps.executeUpdate();
            jormLogger.endRecord(alias);
            return res;
        }
    }

    /**
     * Deletes rows matching the given Delete builder.
     * When hardDelete is false and the target table has a @DeletedAt column,
     * querybuilder4j rewrites the DELETE as an UPDATE that stamps deletedAt.
     */
    public int delete(Delete delete, boolean hardDelete) throws InvalidSqlGenerationException, SQLException {
        if (!hardDelete) {
            // Resolve the entity's @DeletedAt column from the registry using the
            // target table name provided by jsqb (no SQL string parsing), then let
            // jsqb generate the soft-delete UPDATE.
            String table = delete.getBaseTableName();
            String deletedAtColumn = table == null ? null : resolveDeletedAtColumn(table);
            if (deletedAtColumn != null) {
                delete.setDeletedAtColumn(deletedAtColumn).setHardDelete(false);
            }
        }

        SqlParameter deleteParam = delete.getSqlAndParameters();
        jormLogger.info(deleteParam.toString());
        jormLogger.startRecord(alias);
        try (PreparedStatement stmt = getConnection().prepareStatement(deleteParam.getSql())) {
            jdbcUtils.addParameters(stmt, deleteParam.getListParameters());
            int res = stmt.executeUpdate();
            jormLogger.endRecord(alias);
            return res;
        }
    }

    public int delete(T data, boolean hardDelete) throws InvalidSqlGenerationException, SQLException {
        Mapper<T> mapper = mapper();
        EntityMetaData processedEntity = mapper.mapFromEntity(data);
        String table = ScannerEntity.createKey(processedEntity.tableName, processedEntity.db, processedEntity.schema);

        Delete delete = new Delete()
                .from(table)
                .where(processedEntity.columnId + " = :id",
                        (p) -> p.put(processedEntity.columnId, processedEntity.columnIdValue));

        // Delegate soft delete to jsqb: it rewrites to UPDATE ... SET deletedAt = ?
        if (!hardDelete && processedEntity.columnDeletedAt != null) {
            delete.setDeletedAtColumn(processedEntity.columnDeletedAt).setHardDelete(false);
        }

        SqlParameter sqlParameter = delete.getSqlAndParameters();
        jormLogger.info(sqlParameter.toString());
        jormLogger.startRecord(alias);
        try (PreparedStatement ps = getConnection().prepareStatement(sqlParameter.getSql())) {
            jdbcUtils.addParameters(ps, sqlParameter.getListParameters());
            int res = ps.executeUpdate();
            jormLogger.endRecord(alias);
            return res;
        }
    }

    public int insert(Class<T> clazz, T data)
            throws InvalidSqlGenerationException, IllegalAccessException, SQLException {
        Mapper<T> mapper = mapper();
        EntityMetaData processedEntity = mapper.mapFromEntity(data);
        String columns = String.join(",", processedEntity.getColumns());

        if (processedEntity.getColumnIdValue() != null) {
            List<T> result = select(new Selector()
                    .select(
                            processedEntity.getTableName(),
                            columns)
                    .where(
                            processedEntity.getColumnId() + " = :id",
                            (p) -> p.put("id", processedEntity.getColumnIdValue())),
                    clazz);
            if (!result.isEmpty()) {
                Update u = new Update().from(processedEntity.getTableName())
                        .where(
                                processedEntity.getColumnId() + " = :id",
                                (p) -> p.put("id", processedEntity.getColumnIdValue()));
                return commonUpdate(u, processedEntity);
            }
        }

        return commonInsert(clazz, processedEntity);
    }

    public void insert(Class<T> clazz, List<T> data, int batchSize)
            throws InvalidSqlGenerationException, IllegalAccessException, SQLException {
        Mapper<T> mapper = mapper();
        EntityMetaData processedEntity = mapper.mapFromEntity(data.get(0));
        commonBatchInsert(processedEntity, clazz, data, batchSize);
    }
}
