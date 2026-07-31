package com.ecommerce.auth.presentation.dto;

/**
 * Response body for successful login and token refresh.
 *
 * <p>The refresh token is delivered exclusively as an httpOnly, Secure, SameSite=Strict
 * cookie scoped to {@code /api/v1/auth}. It is never included in the JSON body.
 */
public record TokenResponse(String accessToken) {}
