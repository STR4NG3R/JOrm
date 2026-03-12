package org.example.sql;

import io.github.str4ng3r.common.Insert;
import io.github.str4ng3r.common.Selector;
import io.github.str4ng3r.common.Table;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import org.example.utils.JDBCUtils;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class CommonRunner<T> {
    boolean withDeleted;
    boolean hardDelete;
    Connection connection;

    public CommonRunner(Connection connection) {
        this.connection = connection;
    }

    Connection getConnection() {
        return connection;
    }

    protected Selector commonSelect(Selector s) {
        s.setWithDeleted(withDeleted);
        if (!withDeleted) {
            List<Table> tables = s.getTables();
            for (Table e : tables) {
                String[] tableNameAlias = getAliasTable(e.name);
                String k = ScannerEntity.createKey(tableNameAlias[0], e.database, e.schema);
                if (ScannerEntity.entities.containsKey(k)) {
                    EntityMetaData finded = ScannerEntity.entities.get(k);
                    if (finded != null)
                        e.deletedAtColumn = finded.columnDeletedAt;
                }
            }
        }
        return s;
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
            processedEntity.getColumns().add(processedEntity.getColumnCreatedAt());
            processedEntity.getValues().add(new Date(new java.util.Date().getTime()));
        }
        String sql = insertStatement(clazz, processedEntity).write();
        if (processedEntity.getColumnCreatedAt() != null)
            processedEntity.getValues().add(new Date(System.currentTimeMillis()));

        PreparedStatement ps = getConnection().prepareStatement(sql);
        JDBCUtils.addParameters(ps, processedEntity.getValues());
        return ps.executeUpdate();
    }

    Insert insertStatement(Class<T> clazz, EntityMetaData processedEntity) {
        Insert insert = new Insert(clazz.getSimpleName())
                .setColumns(String.join(", ", processedEntity.getColumns()))
                .setTable(processedEntity.tableName)
                .setValues(processedEntity.getValues().toArray(new Object[0]));
        if (processedEntity.columnCreatedAt != null)
            insert.setColumns(processedEntity.columnCreatedAt);
        return insert;
    }

    protected void commonBatchInsert(
            EntityMetaData tableMeta,
            Class<T> clazz,
            List<T> data,
            int batchSize) throws SQLException, InvalidSqlGenerationException {
        String sql = insertStatement(clazz, tableMeta).write();

        long count = 0;
        PreparedStatement ps = getConnection().prepareStatement(sql);
        getConnection().setAutoCommit(false);

        for (T d : data) {
            EntityMetaData e = new EntityMetaData();
            ScannerEntity.getValuesFromEntity(d.getClass(), d, e);
            if (tableMeta.getColumnCreatedAt() != null)
                e.getValues().add(new Date(System.currentTimeMillis()));

            JDBCUtils.addParameters(ps, e.getValues());

            ps.addBatch();

            if (++count % batchSize == 0) {
                ps.executeBatch(); // flush
                ps.clearBatch();
                getConnection().commit(); // commit parcial
                // TODO: Log para metricas
            }
        }
        ps.executeBatch();
        getConnection().commit();
    }

}
