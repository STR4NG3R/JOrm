package io.github.str4ng3r.sql;

import java.sql.SQLException;
import java.util.List;

/**
 * One page of a cursor (keyset) paginated query.
 */
public class CursorPage<T> implements Page<T> {
    private final List<T> data;
    private final int pageSize;
    private final String nextCursor;
    private final Counter counter;
    private Integer count;
    private Integer totalPages;

    /** Runs the COUNT(*) of the base query (without the cursor predicate). */
    @FunctionalInterface
    interface Counter {
        int count() throws SQLException;
    }

    CursorPage(List<T> data, int pageSize, String nextCursor, Counter counter) {
        this.data = data;
        this.pageSize = pageSize;
        this.nextCursor = nextCursor;
        this.counter = counter;
    }

    /**
     * Opt-in: runs a {@code COUNT(*)} over the whole query (ignoring the cursor)
     * and fills {@link #getCount()} and {@link #getTotalPages()}. Cursor pages do
     * not run a COUNT by default, which is what keeps them cheap; call this only
     * when you need the total, typically on the first page only (the client can
     * keep the number for the following pages).
     * <p>
     * It uses the same {@code Connection} as the {@code Runner} that produced the
     * page, so call it before closing that connection. The total is a snapshot
     * and can drift if rows change while the user paginates.
     *
     * @return this page, for chaining
     */
    public CursorPage<T> withCount() throws SQLException {
        if (count == null) {
            int total = counter.count();
            this.totalPages = (int) Math.ceil((double) total / pageSize);
            this.count = total;
        }
        return this;
    }

    @Override
    public List<T> getData() {
        return data;
    }

    @Override
    public Integer getPageSize() {
        return pageSize;
    }

    /** Opaque token to request the next page, or null when this is the last one. */
    @Override
    public String getNextCursor() {
        return nextCursor;
    }

    @Override
    public boolean hasNext() {
        return nextCursor != null;
    }

    /** Not available: cursor pagination has no page numbers. */
    @Override
    public Integer getCurrentPage() {
        return null;
    }

    /** {@code null} unless {@link #withCount()} was called. */
    @Override
    public Integer getCount() {
        return count;
    }

    /** {@code null} unless {@link #withCount()} was called. */
    @Override
    public Integer getTotalPages() {
        return totalPages;
    }
}
