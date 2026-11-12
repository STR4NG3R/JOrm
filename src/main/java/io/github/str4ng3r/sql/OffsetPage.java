package io.github.str4ng3r.sql;

import io.github.str4ng3r.common.SqlParameter;
import io.github.str4ng3r.common.Template;

import java.util.List;

/**
 * Result of offset pagination. It still is a {@code Template<List<T>>}, so code
 * written against {@code selectPaginated} before {@link Page} existed keeps
 * compiling unchanged.
 */
public class OffsetPage<T> extends Template<List<T>> implements Page<T> {

    OffsetPage(SqlParameter sqlParameter, List<T> data) {
        super(sqlParameter, data);
    }

    @Override
    public boolean hasNext() {
        return getCurrentPage() != null && getTotalPages() != null && getCurrentPage() < getTotalPages();
    }

    /** Offset pages have no cursor. */
    @Override
    public String getNextCursor() {
        return null;
    }
}
