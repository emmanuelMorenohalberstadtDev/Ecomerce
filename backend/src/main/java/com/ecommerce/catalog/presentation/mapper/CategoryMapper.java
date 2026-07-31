package com.ecommerce.catalog.presentation.mapper;

import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.presentation.dto.CategoryResponse;

/** Pure domain-to-DTO conversion for {@link Category}. No behavior, no framework imports. */
public final class CategoryMapper {

    private CategoryMapper() {}

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId().toString(),
                category.getParentId() == null ? null : category.getParentId().toString(),
                category.getName()
        );
    }
}
