package com.ecommerce.order.domain.port.out;

import java.util.List;

/**
 * Framework-free paginated result, returned by {@link com.ecommerce.order.domain.OrderRepository}.
 *
 * <p>Deliberately NOT Spring Data's {@code Page}/{@code Pageable} — those live in
 * {@code org.springframework.data..}, which the domain layer must never import (ArchUnit rule
 * {@code domain_is_framework_free}). Own copy, not a shared import of catalog's identically-shaped
 * {@code catalog.domain.port.out.PageResult} — importing a catalog domain type from order would
 * violate {@code contexts_communicate_only_through_ports_or_events} (catalog's {@code domain}
 * package is not a sanctioned crossing point).
 *
 * <p>Mirrors the API-level pagination envelope (api-guidelines.md §4.3:
 * {@code content}/{@code page}/{@code size}/{@code totalElements}/{@code totalPages}) one-to-one
 * so the presentation mapper is a direct field copy.
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResult {
        if (content == null) throw new IllegalArgumentException("content must not be null");
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1) throw new IllegalArgumentException("size must be >= 1");
        if (totalElements < 0) throw new IllegalArgumentException("totalElements must be >= 0");
        if (totalPages < 0) throw new IllegalArgumentException("totalPages must be >= 0");
        content = List.copyOf(content);
    }
}
