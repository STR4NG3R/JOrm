package io.github.str4ng3r.utils;

import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Metrics {
    private static final int MAX_RECORDS = 200;

    /**
     * Threshold en milisegundos para marcar un query como slow.
     * Compartido globalmente como singleton entre todas las instancias.
     * Default: 200ms.
     */
    static volatile long slowThreshold = 200;

    private final LinkedBlockingDeque<TimeRecord> listRecord = new LinkedBlockingDeque<>(MAX_RECORDS);
    private final AtomicLong executions = new AtomicLong();
    private final AtomicLong slowQueriesCount = new AtomicLong();
    private final AtomicLong totalMs = new AtomicLong();

    /**
     * Starts a timed record and returns it so the caller can close it later with
     * {@link #endRecord(TimeRecord)}. Returning the record (instead of relying on
     * peekLast) makes timing thread-safe: each caller owns its own record and never
     * reads one that belongs to another thread or that was evicted by the ring-buffer
     * reset.
     */
    public TimeRecord startRecord(String sql) {
        TimeRecord record = new TimeRecord();
        record.sql = sql;
        record.start = System.nanoTime();

        if (executions.incrementAndGet() >= MAX_RECORDS) {
            listRecord.clear();
            totalMs.set(0);
            executions.set(1);
        }
        // offer() (unlike add()) never throws IllegalStateException when the bounded
        // deque is momentarily full under concurrency; the record is simply dropped
        // from the retained sample, but its timing is still accounted below.
        listRecord.offer(record);
        return record;
    }

    public void endRecord(TimeRecord record) {
        if (record == null) return;
        record.end = System.nanoTime();
        record.record();
        totalMs.addAndGet(record.duration);
        if (record.slow) slowQueriesCount.incrementAndGet();
    }

    public long getAverage() {
        long count = executions.get();
        return count == 0 ? 0 : totalMs.get() / count;
    }

    public List<TimeRecord> getSlowQueries() {
        return this.listRecord.stream().filter(r -> r.slow).collect(Collectors.toList());
    }

    public TimeRecord getLastRecord() {
        return this.listRecord.peekLast();
    }

    /** @deprecated usar getLastRecord() */
    @Deprecated
    public TimeRecord getLastInsert() {
        return getLastRecord();
    }

    public long getSlowQueriesCount() {
        return slowQueriesCount.get();
    }

    /**
     * Configura el threshold global de slow queries (en milisegundos).
     * Aplica a todas las instancias de Metrics.
     */
    public static void setSlowThreshold(long thresholdMs) {
        slowThreshold = thresholdMs;
    }

    public static long getSlowThreshold() {
        return slowThreshold;
    }

    public class TimeRecord {
        private long start = 0;
        private long end = 0;
        private long duration;
        private boolean slow = false;
        private String sql;

        public void record() {
            duration = (end - start) / 1_000_000;
            slow = duration > slowThreshold;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public long getDuration() {
            return duration;
        }

        public String getSql() {
            return sql;
        }

        public boolean isSlow() {
            return slow;
        }

        @Override
        public String toString() {
            return "TimeRecord{sql='" + sql + "', duration=" + duration + "ms, slow=" + slow + "}";
        }
    }
}
