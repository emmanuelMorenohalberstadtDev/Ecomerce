package com.ecommerce.auth.presentation.dto;

/**
 * Marker record for {@code POST /api/v1/auth/refresh}.
 *
 * <p>The refresh token is read from the {@code refreshToken} httpOnly cookie
 * (security §2.5), not from the request body. This record is kept to preserve
 * a consistent controller signature pattern; it carries no fields.
 */
public record RefreshRequest() {}
