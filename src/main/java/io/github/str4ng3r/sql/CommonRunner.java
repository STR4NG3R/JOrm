package io.github.str4ng3r.sql;

import io.github.str4ng3r.common.Insert;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import io.github.str4ng3r.utils.JDBCUtils;
import io.github.str4ng3r.utils.JormLogger;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class CommonRunner<T> {
    boolean withDeleted;
    boolean hardDelete;
    Connection connection;
    JormLogger jormLogger = new JormLogger();
    JDBCUtils jdbcUtils = new JDBCUtils(jormLogger);
    String alias;

    public CommonRunner(Connection connection) {
        this.connection = connection;
        jormLogger.setEnable(false);
        jormLogger.setEnableMetrics(false);
    }

    Connection getConnection() {
        return connection;
    }

    /**
     * Resolves the soft-delete column for a table name, or null if the entity
     * is not registered or has no @DeletedAt column. Used to append the
     * "deletedAt IS NULL" filter when withDeleted is false.
     */
    protected String resolveDeletedAtColumn(String tableExpression) {
        String[] tableNameAlias = getAliasTable(tableExpression);
        EntityMetaData found = ScannerEntity.entitiesRegistryByKey.get(tableNameAlias[0]);
        return found != null ? found.columnDeletedAt : null;
    }

    public String[] getAliasTable(String t) {
        String[] words = t.split("\\s+");
        if (words.length > 0)
            return new String[] { words[0], words[words.length - 1] };
        return new String[] { "", "" };
    }

    protected int commonInsert(Class<T> clazz, EntityMetaData processedEntity)
            throws InvalidSqlGenerationException, SQLException {

        if (processedEntity.getColumnCreatedAt() != null) {
            jormLogger.debug(processedEntity.getColumnCreatedAt());
            processedEntity.getValues().add(new Date(new java.util.Date().getTime()));
        }

        String sql = insertStatement(clazz, processedEntity).getSql();
        jormLogger.info(sql);
        jormLogger.startRecord(alias);
        PreparedStatement ps = getConnection().prepareStatement(sql);
        jdbcUtils.addParameters(ps, processedEntity.getValues());
        int res =  ps.executeUpdate();
        jormLogger.endRecord(alias);
        return res;
    }

    Insert insertStatement(Class<T> clazz, EntityMetaData processedEntity) {
        List<String> columns = new java.util.ArrayList<>(processedEntity.getColumns());
        if (processedEntity.columnCreatedAt != null)
            columns.add(processedEntity.columnCreatedAt);

        return new Insert(clazz.getSimpleName())
                .setTable(processedEntity.tableName)
                .setColumns(columns.toArray(new String[0]))
                .setValues(processedEntity.getValues().toArray(new Object[0]));
    }

    protected void commonBatchInsert(
            EntityMetaData tableMeta,
            Class<T> clazz,
            List<T> data,
            int batchSize) throws SQLException, InvalidSqlGenerationException {
        // Ensure createdAt placeholder is included in the INSERT SQL
        if (tableMeta.getColumnCreatedAt() != null) {
            tableMeta.getValues().add(new Date(System.currentTimeMillis()));
        }
        String sql = insertStatement(clazz, tableMeta).getSql();

        long count = 0;
        PreparedStatement ps = getConnection().prepareStatement(sql);
        getConnection().setAutoCommit(false);

        for (T d : data) {
            EntityMetaData e = new EntityMetaData();
            ScannerEntity.getValuesFromEntity(d.getClass(), d, e);
            if (tableMeta.getColumnCreatedAt() != null)
                e.getValues().add(new Date(System.currentTimeMillis()));

            jdbcUtils.addParameters(ps, e.getValues());

            ps.addBatch();

            if (++count % batchSize == 0) {
                ps.executeBatch(); // flush
                ps.clearBatch();
                getConnection().commit(); // commit parcial
            }
        }
        ps.executeBatch();
        getConnection().commit();
    }

}
