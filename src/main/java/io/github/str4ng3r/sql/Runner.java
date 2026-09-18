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
     * Extracts the deletedAt column for the first table in the selector's FROM clause.
     * Returns null when the entity is not registered or has no @DeletedAt column.
     * The table is parsed from the generated SQL since the builder no longer exposes it.
     */
    private String firstTableDeletedAtColumn(Selector selector) throws InvalidSqlGenerationException {
        String sql = selector.getSqlAndParameters().getSql();
        String table = parseFirstTable(sql);
        return table == null ? null : resolveDeletedAtColumn(table);
    }

    /**
     * Parses the first table name after FROM (before any JOIN, WHERE, comma, etc.).
     */
    static String parseFirstTable(String sql) {
        if (sql == null) return null;
        String upper = sql.toUpperCase();
        int from = upper.indexOf(" FROM ");
        if (from < 0) return null;
        String rest = sql.substring(from + 6).trim();
        // stop at the first delimiter: whitespace, comma, or parenthesis
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == ' ' || c == ',' || c == '\n' || c == '\t' || c == '(' || c == ')') {
                end = i;
                break;
            }
        }
        String table = rest.substring(0, end).trim();
        return table.isEmpty() ? null : table;
    }

    public List<T> select(Selector selector, Function<ResultSet, T> consumer)
            throws InvalidSqlGenerationException, SQLException {
        String deletedAt = withDeleted ? null : firstTableDeletedAtColumn(selector);
        return jdbcUtils.query(selector, getConnection(), alias, deletedAt, rs -> {
            ArrayList<T> list = new ArrayList<>();
            while (rs.next())
                list.add(consumer.apply(rs));
            return list;
        });
    }

    public List<T> select(Selector selector, Class<T> clazz) throws SQLException, InvalidSqlGenerationException {
        String deletedAt = withDeleted ? null : firstTableDeletedAtColumn(selector);
        Mapper<T> mapper = new Mapper<>(jormLogger, alias);
        return jdbcUtils.query(selector, getConnection(), alias, deletedAt,
                rs -> mapper.mapFromResultSet(rs, clazz));
    }

    public Template<List<T>> selectPaginated(int currentPage, int pageSize, Selector selector, Class<T> clazz)
            throws InvalidSqlGenerationException, SQLException, InvalidCurrentPageException {
        String deletedAt = withDeleted ? null : firstTableDeletedAtColumn(selector);
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        int count = jdbcUtils.getCount(getConnection(), selector, sqlParameter, alias);
        selector.setPagination(sqlParameter, new Pagination(pageSize, count, currentPage));
        Mapper<T> mapper = new Mapper<>(jormLogger, alias);
        List<T> data = jdbcUtils.query(selector, getConnection(), alias, deletedAt,
                rs -> mapper.mapFromResultSet(rs, clazz));
        return new Template<>(sqlParameter, data);
    }

    public Template<List<T>> selectPaginated(int currentPage, int pageSize, Selector selector,
            Function<ResultSet, T> consumer)
            throws SQLException, InvalidSqlGenerationException, InvalidCurrentPageException {
        String deletedAt = withDeleted ? null : firstTableDeletedAtColumn(selector);
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        int count = jdbcUtils.getCount(getConnection(), selector, sqlParameter, alias);
        selector.setPagination(sqlParameter, new Pagination(pageSize, count, currentPage));
        List<T> data = jdbcUtils.query(selector, getConnection(), alias, deletedAt, rs -> {
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
     * an UPDATE setting deletedAt = NOW() is issued instead of a physical DELETE.
     */
    public int delete(Delete delete, boolean hardDelete) throws InvalidSqlGenerationException, SQLException {
        SqlParameter deleteParam = delete.getSqlAndParameters();

        if (!hardDelete) {
            String table = parseFirstTableFromDelete(deleteParam.getSql());
            String deletedAtColumn = table == null ? null : resolveDeletedAtColumn(table);
            if (deletedAtColumn != null) {
                return softDelete(deleteParam, table, deletedAtColumn);
            }
        }

        try (PreparedStatement stmt = getConnection().prepareStatement(deleteParam.getSql())) {
            jdbcUtils.addParameters(stmt, deleteParam.getListParameters());
            return stmt.executeUpdate();
        }
    }

    /**
     * Converts a DELETE ... WHERE ... into UPDATE table SET deletedAt = NOW() WHERE ...
     * reusing the WHERE clause and parameters of the original delete statement.
     */
    private int softDelete(SqlParameter deleteParam, String table, String deletedAtColumn)
            throws SQLException {
        String deleteSql = deleteParam.getSql();
        int whereIdx = deleteSql.toUpperCase().indexOf(" WHERE ");
        String whereClause = whereIdx >= 0 ? deleteSql.substring(whereIdx) : "";
        String updateSql = "UPDATE " + table + " SET " + deletedAtColumn + " = ?" + whereClause;

        List<Object> params = new ArrayList<>();
        params.add(new Date(System.currentTimeMillis()));
        params.addAll(deleteParam.getListParameters());

        jormLogger.info(updateSql);
        jormLogger.startRecord(alias);
        try (PreparedStatement ps = getConnection().prepareStatement(updateSql)) {
            jdbcUtils.addParameters(ps, params);
            int res = ps.executeUpdate();
            jormLogger.endRecord(alias);
            return res;
        }
    }

    static String parseFirstTableFromDelete(String sql) {
        if (sql == null) return null;
        String upper = sql.toUpperCase();
        int from = upper.indexOf(" FROM ");
        if (from < 0) return null;
        String rest = sql.substring(from + 6).trim();
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == ' ' || c == ',' || c == '\n' || c == '\t') {
                end = i;
                break;
            }
        }
        String table = rest.substring(0, end).trim();
        return table.isEmpty() ? null : table;
    }

    public int delete(T data, boolean hardDelete) throws InvalidSqlGenerationException, SQLException {
        Mapper<T> mapper = new Mapper<>(jormLogger, alias);
        EntityMetaData processedEntity = mapper.mapFromEntity(data);
        String table = ScannerEntity.createKey(processedEntity.tableName, processedEntity.db, processedEntity.schema);

        if (!hardDelete && processedEntity.columnDeletedAt != null) {
            // soft delete: UPDATE table SET deletedAt = NOW() WHERE id = ?
            String updateSql = "UPDATE " + table + " SET " + processedEntity.columnDeletedAt
                    + " = ? WHERE " + processedEntity.columnId + " = ?";
            List<Object> params = new ArrayList<>();
            params.add(new Date(System.currentTimeMillis()));
            params.add(processedEntity.columnIdValue);

            jormLogger.info(updateSql);
            jormLogger.startRecord(alias);
            try (PreparedStatement ps = getConnection().prepareStatement(updateSql)) {
                jdbcUtils.addParameters(ps, params);
                int res = ps.executeUpdate();
                jormLogger.endRecord(alias);
                return res;
            }
        }

        Delete delete = new Delete();
        delete.from(table);
        delete.where(processedEntity.columnId + " = :id",
                (p) -> p.put(processedEntity.columnId, processedEntity.columnIdValue));

        SqlParameter sqlParameter = delete.getSqlAndParameters();
        jormLogger.info(sqlParameter.toString());
        try (PreparedStatement ps = getConnection().prepareStatement(sqlParameter.getSql())) {
            jdbcUtils.addParameters(ps, sqlParameter.getListParameters());
            return ps.executeUpdate();
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
