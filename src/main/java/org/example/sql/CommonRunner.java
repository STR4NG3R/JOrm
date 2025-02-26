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
    Connection connection;
    boolean withDeleted;
    boolean hardDelete;


    protected Selector commonSelect(Selector s) {
        ScannerEntity.findEntities(false);
        s.setWithDeleted(withDeleted);
        if (!withDeleted) {
            List<Table> tables = s.getTables();
            for (Table e : tables) {
                String[] tableNameAlias = getAliasTable(e.name);
                String k = ScannerEntity.createKey(tableNameAlias[0], e.database, e.schema);
                if (ScannerEntity.entities.containsKey(k)) {
                    EntityMetaData finded = ScannerEntity.entities.get(k);
                    if (finded != null) e.deletedAtColumn = finded.columnDeletedAt;
                }
            }
        }
        return s;
    }

    public String[] getAliasTable(String t) {
        String[] words = t.split("\\s+");
        if (words.length > 0)
            return new String[]{words[0], words[words.length - 1]};
        return new String[]{"", ""};
    }

    protected int commonInsert(String tableName, Class<T> clazz, EntityMetaData processedEntity) throws InvalidSqlGenerationException, IllegalAccessException, SQLException {
        if (processedEntity.getColumnCreatedAt() != null) {
            processedEntity.getColumns().add(processedEntity.getColumnCreatedAt());
            processedEntity.getValues().add(new Date(new java.util.Date().getTime()));
        }

        String sql = new Insert(clazz.getSimpleName())
                .setColumns(String.join(", ", processedEntity.getColumns()))
                .setTable(tableName)
                .setValues(processedEntity.getValues().toArray(new Object[0]))
                .write();
        PreparedStatement ps = connection.prepareStatement(sql);
        JDBCUtils.addParameters(ps, processedEntity.getValues());
        return ps.executeUpdate();
    }
}
