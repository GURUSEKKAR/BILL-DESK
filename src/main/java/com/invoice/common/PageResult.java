package com.invoice.common;

import java.util.List;

/**
 * Cursor-paginated response wrapper.
 * <p>
 * {@code nextCursor} is opaque to the client. When null, there are no more pages.
 */
public record PageResult<T>(
    List<T> items,
    String nextCursor,
    boolean hasMore
) {
    public static <T> PageResult<T> of(List<T> items, String nextCursor) {
        return new PageResult<>(items, nextCursor, nextCursor != null);
    }
}
