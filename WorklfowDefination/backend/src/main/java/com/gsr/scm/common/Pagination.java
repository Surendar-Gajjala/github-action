package com.gsr.scm.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

/** Builds a {@link Pageable} from request parameters, accepting only whitelisted sort properties. */
public final class Pagination {

    public static final int MAX_SIZE = 200;

    private Pagination() {
    }

    public static Pageable of(int page, int size, String sort, Set<String> allowedSort, String defaultSort) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
        String spec = (sort == null || sort.isBlank()) ? defaultSort : sort;
        String[] parts = spec.split(",");
        String property = parts[0].trim();
        if (!allowedSort.contains(property)) {
            throw new IllegalArgumentException("Unsupported sort property '" + property + "'. Allowed: " + allowedSort);
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    /** Lower-cased LIKE pattern or null when the search text is empty. */
    public static String likePattern(String q) {
        return (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
