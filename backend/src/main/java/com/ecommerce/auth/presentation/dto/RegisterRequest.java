package com.ecommerce.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/register}.
 *
 * <p>Password minimum length: 12 per security §2.1. Maximum 72 characters —
 * BCrypt silently truncates beyond 72 bytes; enforcing the max prevents
 * user surprise where "password longer than 72 chars" appears to work but
 * the stored hash only covers the first 72 characters.
 */
public record RegisterRequest(

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password must not be blank")
        @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
        String password

) {}
