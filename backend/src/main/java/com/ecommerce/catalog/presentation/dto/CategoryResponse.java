package com.ecommerce.catalog.presentation.dto;

/** Wire shape for a category, returned by both public and admin category endpoints. */
public record CategoryResponse(String id, String parentId, String name) {}
