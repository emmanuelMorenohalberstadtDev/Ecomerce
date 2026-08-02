package com.ecommerce.order.presentation.dto;

import java.util.List;

/**
 * Standard pagination envelope for every collection endpoint (api-guidelines.md §4.3):
 * {@code { "content": [...], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 } }.
 * Own copy — mirrors {@code catalog.presentation.dto.PageResponse} (flagged there as a promotion
 * candidate to {@code common.web.dto} the next time a second context needs it; this is the second
 * context).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
