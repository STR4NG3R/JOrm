package org.example.sql;

import io.github.str4ng3r.common.*;
import io.github.str4ng3r.exceptions.InvalidCurrentPageException;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import org.example.utils.JDBCUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Runner<T> extends CommonRunner {
    public Runner() {
        withDeleted = true;
    }

    static Configuration configuration;

    public static void  setConfiguration(Configuration configuration) {
        Runner.configuration = configuration;
    }

    public Runner<T> hardDelete(boolean hardDelete) {
        this.hardDelete = hardDelete;
        return this;
    }

    public Runner<T> withDeleted(boolean withDeleted) {
        this.withDeleted = withDeleted;
        return this;
    }

    public enum ISOLATION {
        NONE(0),
        READ_UNCOMMITTED(1),
        READ_COMMITED(2),
        REPEATABLE_READ(4),
        SERIALIZABLE(8);

        final int isolationValue;

        ISOLATION(int value) {
            isolationValue = value;
        }
    }

    public void setTransaction(ISOLATION isolation) throws SQLException {
        connection.setTransactionIsolation(isolation.isolationValue);
    }


    public Runner<T> setConnection(Connection connection) {
        this.connection = connection;
        return this;
    }

    public List<T> select(Selector selector, Function<ResultSet, T> consumer) throws InvalidSqlGenerationException, SQLException {
        commonSelect(selector);
        ResultSet rs = JDBCUtils.createResultSet(selector, connection, withDeleted);
        ArrayList<T> list = new ArrayList<>();
        while (rs.next()) list.add(consumer.apply(rs));
        return list;
    }

    public List<T> select(Selector selector, Class<T> clazz) throws SQLException, InvalidSqlGenerationException {
        commonSelect(selector);
        ResultSet rs = JDBCUtils.createResultSet(selector, connection, withDeleted);
        Mapper<T> mapper = new Mapper<>();
        return mapper.mapFromResultSet(rs, clazz);
    }

    public Template<List<T>> selectPaginated(int currentPage, int pageSize, Selector selector, Class<T> clazz) throws InvalidSqlGenerationException, SQLException, InvalidCurrentPageException {
        commonSelect(selector);
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        int count = JDBCUtils.getCount(connection, selector, sqlParameter, withDeleted);
        selector.setPagination(sqlParameter, new Pagination(pageSize, count, currentPage));
        ResultSet rs = JDBCUtils.createResultSet(selector, connection, withDeleted);
        return new Template<List<T>>(sqlParameter, new Mapper<T>().mapFromResultSet(rs, clazz));
    }

    public Template<List<T>> selectPaginated(int currentPage, int pageSize, Selector selector, Function<ResultSet, T> consumer) throws SQLException, InvalidSqlGenerationException, InvalidCurrentPageException {
        commonSelect(selector);
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        int count = JDBCUtils.getCount(connection, selector, sqlParameter, withDeleted);
        selector.setPagination(sqlParameter, new Pagination(pageSize, count, currentPage));
        ResultSet rs = JDBCUtils.createResultSet(selector, connection, withDeleted);
        ArrayList<T> list = new ArrayList<>();
        while (rs.next()) list.add(consumer.apply(rs));
        return new Template<>(sqlParameter, list);
    }

    protected int commonUpdate(Update update, EntityMetaData processedEntity) throws InvalidSqlGenerationException, SQLException {
        for (int i = 0; i < processedEntity.getColumns().size(); i++) {
            Object value = processedEntity.getValues().get(i);
            if (value != null) {
                String column = processedEntity.getColumns().get(i);
                update.setColumnsValuesToUpdate((cv) ->
                        cv.put(column, value));
            }
        }

        if (processedEntity.getColumnUpdatedAt() != null) {
            update.setColumnsValuesToUpdate(
                    p -> p.put(processedEntity.getColumnUpdatedAt(), new Date(new java.util.Date().getTime()))
            );
        }

        SqlParameter sqlParameter = update.getSqlAndParameters();
        PreparedStatement ps = connection.prepareStatement(sqlParameter.sql);
        JDBCUtils.addParameters(ps, sqlParameter.getListParameters());
        return ps.executeUpdate();
    }

    public int delete(Delete delete, boolean hardDelete) throws InvalidSqlGenerationException, SQLException {
        if (hardDelete) {
            EntityMetaData found = ScannerEntity.getEntityFromTableName(delete.getTables().get(0));
            if (found != null && found.columnDeletedAt != null)
                delete.setDeletedAtColumn(found.columnDeletedAt);
        }

        delete.setHardDelete(hardDelete);
        SqlParameter sqlParameter = delete.getSqlAndParameters();
        PreparedStatement ps = connection.prepareStatement(sqlParameter.sql);
        JDBCUtils.addParameters(ps, sqlParameter.getListParameters());
        return ps.executeUpdate();
    }

    public int insert(Class<T> clazz, T data) throws InvalidSqlGenerationException, IllegalAccessException, SQLException {
        Mapper<T> mapper = new Mapper<>();
        EntityMetaData processedEntity = mapper.mapFromEntity(data);
        String columns = String.join(",", processedEntity.getColumns());

        if (processedEntity.getColumnId() != null) {
            List<T> result = select(new Selector()
                            .select(
                                    processedEntity.getTableName(),
                                    columns
                            ).where(
                                    processedEntity.getColumnId() + " = :id",
                                    (p) -> p.put("id", processedEntity.getColumnIdValue())
                            )
                    , clazz
            );
            if (!result.isEmpty()) {
                Update u = new Update().from(processedEntity.getTableName())
                        .where(
                                processedEntity.getColumnId() + " = :id",
                                (p) -> p.put("id", processedEntity.getColumnIdValue())
                        );
                return commonUpdate(u, processedEntity);
            }
        }

        return commonInsert(processedEntity.getTableName(), clazz, processedEntity);
    }


    public void insert(Class<T> clazz, List<T> data) throws InvalidSqlGenerationException, IllegalAccessException, SQLException {
        for (T d : data) insert(clazz, d);
    }
}
