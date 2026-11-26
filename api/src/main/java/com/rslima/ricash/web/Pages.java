package com.rslima.ricash.web;

import org.springframework.data.domain.PageRequest;

/**
 * Builds the Pageable for every list endpoint from its
 * {@code page[number]}/{@code page[size]} request parameters, capping the page
 * size so a single request cannot ask for an unbounded result set. Values
 * below the valid range keep {@link PageRequest#of}'s 400 (via
 * IllegalArgumentException); only the upper bound is clamped. Links should use
 * {@code pageable.getPageSize()} so they carry the effective size.
 */
public final class Pages {

    /** Largest page any list endpoint serves; the frontend asks for at most 200. */
    public static final int MAX_PAGE_SIZE = 500;

    private Pages() {
    }

    public static PageRequest of(int page, int size) {
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
    }
}
