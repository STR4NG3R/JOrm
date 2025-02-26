package org.example.sql;

import java.util.List;
import java.util.Objects;

public class EntityMetaData {
    List<String> columns;
    List<Object> values;
    String tableName;
    String schema;
    String db;
    String columnId;
    String columnCreatedAt;
    String columnUpdatedAt;
    String columnDeletedAt;
    Object columnIdValue;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityMetaData that = (EntityMetaData) o;
        return Objects.equals(tableName, that.tableName) && Objects.equals(schema, that.schema) && Objects.equals(db, that.db);
    }

    @Override
    public int hashCode() {
        return Objects.hash(db, schema, tableName);
    }

    public String getColumnCreatedAt() {
        return columnCreatedAt;
    }

    public void setColumnCreatedAt(String columnCreatedAt) {
        this.columnCreatedAt = columnCreatedAt;
    }

    public String getColumnUpdatedAt() {
        return columnUpdatedAt;
    }

    public void setColumnUpdatedAt(String columnUpdatedAt) {
        this.columnUpdatedAt = columnUpdatedAt;
    }

    public String getColumnDeletedAt() {
        return columnDeletedAt;
    }

    public void setColumnDeletedAt(String columnDeletedAt) {
        this.columnDeletedAt = columnDeletedAt;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setColumnIdValue(Object columnIdValue) {
        this.columnIdValue = columnIdValue;
    }

    public String getColumnId() {
        return columnId;
    }

    public Object getColumnIdValue() {
        return columnIdValue;
    }

    public void setColumnId(String columnId) {
        this.columnId = columnId;
    }
}
