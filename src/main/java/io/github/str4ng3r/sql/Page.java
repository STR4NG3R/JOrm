package io.github.str4ng3r.sql;

import java.util.List;

/**
 * Common result type of {@link Runner#selectPaginated} (offset) and
 * {@link Runner#selectCursor} (keyset), so calling code can treat both the same:
 *
 * <pre>{@code
 * Page<UserDao> page = runner.selectPaginated(1, 10, selector, UserDao.class);
 * Page<UserDao> page = runner.selectCursor(10, cursor, selector, UserDao.class);
 * page.getData();
 * page.hasNext();
 * }</pre>
 *
 * Fields that do not apply to the pagination style are {@code null}: the cursor
 * variant has no page number, count or total pages; the offset variant has no
 * cursor.
 */
public interface Page<T> {

    List<T> getData();

    Integer getPageSize();

    /** True when there is at least one more page after this one. */
    boolean hasNext();

    /** Cursor for the next page; {@code null} for offset pages or on the last page. */
    String getNextCursor();

    /** 1-based page number; {@code null} for cursor pages. */
    Integer getCurrentPage();

    /** Total rows matching the query; {@code null} for cursor pages (no COUNT is run). */
    Integer getCount();

    /** Total number of pages; {@code null} for cursor pages. */
    Integer getTotalPages();
}
