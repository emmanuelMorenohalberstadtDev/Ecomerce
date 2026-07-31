package com.ecommerce.catalog.domain.port.out;

import java.util.List;

/**
 * Framework-free paginated result, returned by domain repository ports.
 *
 * <p>Deliberately NOT Spring Data's {@code Page}/{@code Pageable} — those live in
 * {@code org.springframework.data..}, which the domain layer must never import
 * (ArchUnit rule {@code domain_is_framework_free}). Infrastructure adapters convert between
 * this type and Spring Data's {@code Page} at the boundary.
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
