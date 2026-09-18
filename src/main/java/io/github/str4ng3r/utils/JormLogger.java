package io.github.str4ng3r.utils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JormLogger {
    volatile boolean enable = false;
    volatile boolean enableMetrics = false;

    /**
     * Mapa global de métricas por alias de query.
     * Estático para ser compartido entre todas las instancias de JormLogger/Runner.
     */
    private static final ConcurrentHashMap<String, Metrics> metricsByQuery = new ConcurrentHashMap<>();

    public void setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public void debug(String message) {
        if (!enable) return;
        System.out.println("[JORM DEBUG] " + message);
    }

    public void info(String message) {
        if (!enable) return;
        System.out.println("[JORM INFO] " + message);
    }

    public void warn(String message) {
        if (!enable) return;
        System.out.println("[JORM WARN] " + message);
    }

    public void error(String message, Throwable error) {
        if (!enable) return;
        System.err.println("[JORM ERROR] " + message);
        error.printStackTrace();
    }

    public void startRecord(String alias, String sql) {
        if (!enableMetrics) return;
        metricsByQuery.computeIfAbsent(alias, k -> new Metrics()).startRecord(sql);
    }

    public void startRecord(String alias) {
        startRecord(alias, null);
    }

    public void endRecord(String alias) {
        if (!enableMetrics) return;
        Metrics metrics = metricsByQuery.get(alias);
        if (metrics == null) return;
        metrics.endRecord();
        Metrics.TimeRecord last = metrics.getLastRecord();
        if (last != null) {
            System.out.println("Alias: " + alias
                    + " | duration: " + last.getDuration() + "ms"
                    + (last.isSlow() ? " [SLOW]" : "")
                    + " | avg: " + metrics.getAverage() + "ms");
        }
    }

    // -------------------------------------------------------------------------
    // API estática para acceso global a las métricas
    // -------------------------------------------------------------------------

    /**
     * Elimina todas las métricas acumuladas.
     */
    public static void flush() {
        metricsByQuery.clear();
    }

    /**
     * Retorna una vista inmutable del mapa de métricas por alias.
     */
    public static Map<String, Metrics> getMetrics() {
        return Collections.unmodifiableMap(metricsByQuery);
    }

    /**
     * Retorna un JSON string con el resumen de todas las métricas.
     * Útil para exponer en un endpoint de debug.
     *
     * Formato:
     * {
     *   "slowThresholdMs": 200,
     *   "queries": {
     *     "get_users": {
     *       "avgMs": 12,
     *       "slowCount": 1,
     *       "slowQueries": [
     *         { "sql": "...", "durationMs": 340 }
     *       ]
     *     }
     *   }
     * }
     */
    public static String getMetricsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"slowThresholdMs\": ").append(Metrics.getSlowThreshold()).append(",\n");
        sb.append("  \"queries\": {\n");

        String[] aliases = metricsByQuery.keySet().toArray(new String[0]);
        for (int i = 0; i < aliases.length; i++) {
            String alias = aliases[i];
            Metrics m = metricsByQuery.get(alias);
            List<Metrics.TimeRecord> slowQueries = m.getSlowQueries();

            sb.append("    \"").append(alias).append("\": {\n");
            sb.append("      \"avgMs\": ").append(m.getAverage()).append(",\n");
            sb.append("      \"slowCount\": ").append(m.getSlowQueriesCount()).append(",\n");
            sb.append("      \"slowQueries\": [");

            if (!slowQueries.isEmpty()) {
                sb.append("\n");
                for (int j = 0; j < slowQueries.size(); j++) {
                    Metrics.TimeRecord r = slowQueries.get(j);
                    sb.append("        { \"sql\": \"").append(escapeSql(r.getSql())).append("\"")
                      .append(", \"durationMs\": ").append(r.getDuration()).append(" }");
                    if (j < slowQueries.size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("      ");
            }
            sb.append("]\n");
            sb.append("    }");
            if (i < aliases.length - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Retorna las métricas de un alias específico, o null si no existe.
     */
    public static Metrics getMetrics(String alias) {
        return metricsByQuery.get(alias);
    }

    /**
     * Retorna todos los TimeRecord marcados como slow en todos los aliases.
     */
    public static List<Metrics.TimeRecord> getAllSlowQueries() {
        return metricsByQuery.values().stream()
                .flatMap(m -> m.getSlowQueries().stream())
                .collect(Collectors.toList());
    }

    private static String escapeSql(String sql) {
        if (sql == null) return "";
        return sql.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
