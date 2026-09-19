package io.github.str4ng3r.utils;

import io.github.str4ng3r.common.Selector;
import io.github.str4ng3r.common.SqlParameter;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class JDBCUtils {

    JormLogger jormLogger;

    public JDBCUtils(JormLogger jormLogger) {
        this.jormLogger = jormLogger;
    }

    /**
     * Maps an already-open ResultSet into a result. The ResultSet lifecycle is
     * owned by JDBCUtils, so implementations must not close it.
     */
    @FunctionalInterface
    public interface ResultSetMapper<R> {
        R map(ResultSet rs) throws SQLException;
    }

    public void addParameters(PreparedStatement ps, List<Object> parameters) throws SQLException {
        ps.clearParameters();
        for (int i = 0; i < parameters.size(); i++) ps.setObject(i + 1, parameters.get(i));
    }

    public int getCount(Connection connection, Selector s, SqlParameter sqlParameter, String alias)
            throws SQLException {
        this.jormLogger.info(sqlParameter.toString());
        this.jormLogger.startRecord("count-" + alias, sqlParameter.getSql());
        try (PreparedStatement ps = connection.prepareStatement(s.getCount(sqlParameter.getSql()))) {
            addParameters(ps, sqlParameter.getListParameters());
            try (ResultSet rs = ps.executeQuery()) {
                int count = rs.next() ? rs.getInt(1) : 0;
                this.jormLogger.endRecord(alias);
                return count;
            }
        }
    }

    /**
     * Runs the selector query and maps the ResultSet through the given mapper,
     * closing the PreparedStatement and ResultSet before returning. Soft-delete
     * filtering (if any) is already baked into the selector's generated SQL by
     * querybuilder4j, so no post-processing of the SQL string is needed here.
     */
    public <R> R query(Selector selector, Connection connection, String alias,
            ResultSetMapper<R> mapper) throws SQLException, InvalidSqlGenerationException {
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        String sql = sqlParameter.getSql();
        this.jormLogger.info(sql);
        this.jormLogger.startRecord(alias, sql);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            addParameters(ps, sqlParameter.getListParameters());
            try (ResultSet rs = ps.executeQuery()) {
                R result = mapper.map(rs);
                jormLogger.endRecord(alias);
                return result;
            }
        }
    }
}
