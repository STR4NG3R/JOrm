package org.example.utils;

import java.util.concurrent.ConcurrentHashMap;

public class JormLogger {
    volatile boolean enable = false;
    volatile boolean enableMetrics = false;
    ConcurrentHashMap<String, Metrics> metricsByQuery = new ConcurrentHashMap<>();


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

    public void startRecord (String alias, String sql) {
        if(!enableMetrics) return;
        Metrics metrics = new Metrics();
        metrics.startRecord(sql);
        metricsByQuery.put(alias, metrics);
    }

    public void startRecord (String alias) {
        startRecord(alias, null);
    }

    public  void endRecord(String alias){
        if (!enableMetrics) return;
        Metrics metrics = metricsByQuery.get(alias);
        metrics.endRecord();
        System.out.print("Alias: " + alias + "\navg: " + metrics.getAverage() + " ms.\n");
    }

}
