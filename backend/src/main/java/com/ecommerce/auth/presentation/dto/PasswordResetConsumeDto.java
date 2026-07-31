package com.ecommerce.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/password-reset/consume}.
 */
public record PasswordResetConsumeDto(

        @NotBlank(message = "Token must not be blank")
        String token,

        @NotBlank(message = "New password must not be blank")
        @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
        String newPassword

) {}
