package io.github.str4ng3r.sql;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // Reflection caches, populated once at registration time and shared read-only.
    Map<String, Field> fieldCache;
    Constructor<?> noArgConstructor;

    /**
     * Returns a new EntityMetaData with the structural metadata copied (table, columns, annotations)
     * and a fresh empty values list. Used to avoid mutating the shared registry object per operation.
     * The reflection caches are shared by reference since they are immutable after registration.
     */
    public EntityMetaData cloneStructure() {
        EntityMetaData clone = new EntityMetaData();
        clone.tableName = this.tableName;
        clone.schema = this.schema;
        clone.db = this.db;
        clone.columnId = this.columnId;
        clone.columnCreatedAt = this.columnCreatedAt;
        clone.columnUpdatedAt = this.columnUpdatedAt;
        clone.columnDeletedAt = this.columnDeletedAt;
        clone.columns = new ArrayList<>(this.columns); // structural copy
        clone.values = new ArrayList<>();               // fresh per operation
        clone.fieldCache = this.fieldCache;             // shared read-only
        clone.noArgConstructor = this.noArgConstructor; // shared read-only
        return clone;
    }

    public Map<String, Field> getFieldCache() {
        return fieldCache;
    }

    public Constructor<?> getNoArgConstructor() {
        return noArgConstructor;
    }

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
