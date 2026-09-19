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
        Mapper<T> mapper = new Mapper<>(jormLogger, alias);
        return jdbcUtils.query(selector, getConnection(), alias,
                rs -> mapper.mapFromResultSet(rs, clazz));
    }

    public Template<List<T>> selectPaginated(int currentPage, int pageSize, Selector selector, Class<T> clazz)
            throws InvalidSqlGenerationException, SQLException, InvalidCurrentPageException {
        deletedAtColumnFor(clazz);
        applySoftDelete(selector);
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        int count = jdbcUtils.getCount(getConnection(), selector, sqlParameter, alias);
        selector.setPagination(sqlParameter, new Pagination(pageSize, count, currentPage));
        Mapper<T> mapper = new Mapper<>(jormLogger, alias);
        List<T> data = jdbcUtils.query(selector, getConnection(), alias,
                rs -> mapper.mapFromResultSet(rs, clazz));
        return new Template<>(sqlParameter, data);
    }

    public Template<List<T>> selectPaginated(int currentPage, int pageSize, Selector selector,
            Function<ResultSet, T> consumer)
            throws SQLException, InvalidSqlGenerationException, InvalidCurrentPageException {
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        int count = jdbcUtils.getCount(getConnection(), selector, sqlParameter, alias);
        selector.setPagination(sqlParameter, new Pagination(pageSize, count, currentPage));
        List<T> data = jdbcUtils.query(selector, getConnection(), alias, rs -> {
            ArrayList<T> list = new ArrayList<>();
            while (rs.next())
                list.add(consumer.apply(rs));
            return list;
        });
        return new Template<>(sqlParameter, data);
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
        Mapper<T> mapper = new Mapper<>(jormLogger, alias);
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
        Mapper<T> mapper = new Mapper<>(jormLogger, alias);
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
        Mapper<T> mapper = new Mapper<>(jormLogger, alias);
        EntityMetaData processedEntity = mapper.mapFromEntity(data.get(0));
        commonBatchInsert(processedEntity, clazz, data, batchSize);
    }
}
