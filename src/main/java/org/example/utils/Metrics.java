package org.example.utils;

import java.sql.Time;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Metrics {
    private final LinkedBlockingDeque<TimeRecord> listRecord = new LinkedBlockingDeque<>(500);
    private final AtomicLong executions = new AtomicLong();
    private final AtomicLong slowQueriesCount = new AtomicLong();
    private final AtomicLong totalMs = new AtomicLong();

    public void startRecord(String sql) {
        TimeRecord record = new TimeRecord();
        long time = System.nanoTime();
        record.sql = sql;
        record.start = time;
        if (executions.incrementAndGet() >= 500){
            listRecord.clear();
            totalMs.set(0);
            executions.set(1);
        }
        listRecord.add(record);
    }

    public void endRecord() {
        TimeRecord record = listRecord.peekLast();
        record.end = System.nanoTime();
        record.record();
        totalMs.addAndGet(record.duration);
        if (record.slow) slowQueriesCount.incrementAndGet();
    }

    public long getAverage() {
        return totalMs.get() / executions.get();
    }

    public List<TimeRecord> getSlowQueries() {
        return this.listRecord.stream().filter(r -> r.slow).collect(Collectors.toList());
    }

    public TimeRecord getLastInsert() {
        return this.listRecord.peekLast();
    }

    class TimeRecord {
        private long start = 0;
        private long end = 0;
        private long duration;
        private boolean slow = false;
        private String sql;

        public void record() {
                duration = (end - start) / 1_000_000;
                if (duration > 100) slow = true;

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
    }
}
