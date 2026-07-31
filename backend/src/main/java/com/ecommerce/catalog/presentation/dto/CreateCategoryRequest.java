package com.ecommerce.catalog.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/admin/categories}. */
public record CreateCategoryRequest(

        @NotBlank(message = "name must not be blank")
        @Size(max = 150, message = "name must not exceed 150 characters")
        String name,

        @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "parentId must be a valid UUID")
        String parentId

) {}
