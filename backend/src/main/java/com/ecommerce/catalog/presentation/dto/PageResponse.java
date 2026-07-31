package com.ecommerce.catalog.presentation.dto;

import java.util.List;

/**
 * Standard pagination envelope for every collection endpoint (api-guidelines.md §4.3):
 * {@code { "content": [...], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 } }.
 *
 * <p>Not promoted to a shared/common web package yet — catalog is the first context to ship a
 * paginated endpoint, so this is currently duplicated-in-spirit-only (no other context has one
 * to share with). Flagged as a candidate for promotion to {@code common.web.dto} the next time a
 * second context needs the same envelope.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
