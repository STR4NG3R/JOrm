package io.github.str4ng3r.sql;

import io.github.str4ng3r.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

class ScannerEntity {
    static Map<Class<?>, EntityMetaData> entitiesRegistry = new ConcurrentHashMap<>();
    static Map<String, EntityMetaData> entitiesRegistryByKey = new ConcurrentHashMap<>();


    static void registryEntity(Class<?>... entityClasses) {
        for (Class<?> clazz : entityClasses) {
            if (!clazz.isAnnotationPresent(Entity.class)) continue;
            Entity e = clazz.getAnnotation(Entity.class);

            EntityMetaData entityMetaData = entitiesRegistry.computeIfAbsent(clazz, (c) -> {
                EntityMetaData meta = new EntityMetaData();
                meta.tableName = e.name();
                meta.schema = e.schema();
                meta.db = e.database();
                getColumnsFromEntity(c, meta);
                buildReflectionCache(c, meta);
                return meta;
            });

            // Populate the secondary index outside the computeIfAbsent mapping function.
            // Modifying another ConcurrentHashMap inside the lambda is unsafe; doing it here
            // is idempotent (putIfAbsent) and avoids nested-update hazards.
            entitiesRegistryByKey.putIfAbsent(
                    createKey(entityMetaData.tableName, entityMetaData.db, entityMetaData.schema),
                    entityMetaData);
        }
    }

    /**
     * Precomputes the no-arg constructor and an accessible Field map keyed by field name,
     * so mapping a ResultSet no longer calls getDeclaredConstructor/getDeclaredField per row/cell.
     */
    static void buildReflectionCache(Class<?> clazz, EntityMetaData meta) {
        // Case-insensitive keys so ResultSet column labels match field names
        // regardless of the database's identifier case folding (e.g. Oracle
        // upper-cases unquoted identifiers: ID, NAME, DELETEDAT).
        Map<String, Field> fields = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // Walk the class hierarchy so inherited fields are also mappable.
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                field.setAccessible(true);
                fields.putIfAbsent(field.getName(), field);
            }
        }
        meta.fieldCache = fields;
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            meta.noArgConstructor = ctor;
        } catch (NoSuchMethodException ignored) {
            // No no-arg constructor: mapFromResultSet will fall back to reflection.
        }
    }

    public static String createKey(String name, String db, String schema) {
        StringBuilder key = new StringBuilder();
        if (db != null && !db.isEmpty()) key.append(db).append(".");
        if (schema != null && !schema.isEmpty()) key.append(schema).append(".");
        key.append(name);
        return key.toString();
    }

    public static <T> void getTableNameFromEntity(T entity, EntityMetaData processedEntity) {
        Entity e = entity.getClass().getAnnotation(Entity.class);
        processedEntity.tableName = e.name();
        processedEntity.db = e.database();
        processedEntity.schema = e.schema();
    }

    public static void iterateFields(Class<?> clazz, Consumer<Field> consumer) {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            consumer.accept(field);
        }
    }

    public static <T> void getValuesFromEntity(Class<?> clazz, T entity, EntityMetaData processedEntity) {
        List<Object> values = new ArrayList<>();
        iterateFields(clazz,
                field -> {
                    try {
                        if (field.isAnnotationPresent(Column.class)) values.add(field.get(entity));
                        else if (field.isAnnotationPresent(Id.class)) processedEntity.setColumnIdValue(field.get(entity));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
        processedEntity.setValues(values);
    }

    public static void getColumnsFromEntity(Class<?> clazz, EntityMetaData processedEntity) {
        List<String> columns = new ArrayList<>();
        iterateFields(clazz,
                field -> {
                    if (field.isAnnotationPresent(Column.class)) columns.add(field.getName());
                    else if (field.isAnnotationPresent(Id.class)) processedEntity.setColumnId(field.getName());
                    else if (field.isAnnotationPresent(CreatedAt.class)) processedEntity.setColumnCreatedAt(field.getName());
                    else if (field.isAnnotationPresent(UpdatedAt.class)) processedEntity.setColumnUpdatedAt(field.getName());
                    else if (field.isAnnotationPresent(DeletedAt.class)) processedEntity.setColumnDeletedAt(field.getName());
                });
        processedEntity.setColumns(columns);
    }

    public static EntityMetaData getEntityFromTableName(String tableName) {
        return entitiesRegistryByKey.getOrDefault(tableName, null);
    }
}
