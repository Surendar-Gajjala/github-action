package com.demo.itemintegration.common.dto;

import java.util.List;

/**
 * One page of any entity as returned by the local API ({@code /api/items},
 * {@code /api/parts}, ...). {@code objects} holds the page's rows, whatever the entity;
 * {@code page} is zero-based; {@code count} is the number of rows on this page.
 * {@code totalObjects} and {@code totalPages} are null when the hosted server does not
 * report a total.
 */
public record PageResponse<T>(
        int count,
        List<T> objects,
        int page,
        int size,
        Long totalObjects,
        Integer totalPages,
        boolean hasMore) {

    /** Largest page size the local API accepts. */
    public static final int MAX_SIZE = 100;

    public static <T> PageResponse<T> of(List<T> objects, int page, int size, Long totalObjects, Boolean hasMore) {
        Integer totalPages = totalObjects == null ? null : (int) Math.max(1, (totalObjects + size - 1) / size);
        boolean more = hasMore != null
                ? hasMore
                : totalObjects != null ? (long) (page + 1) * size < totalObjects : objects.size() == size;
        return new PageResponse<>(objects.size(), objects, page, size, totalObjects, totalPages, more);
    }
}
