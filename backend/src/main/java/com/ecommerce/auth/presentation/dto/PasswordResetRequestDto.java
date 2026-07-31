package com.ecommerce.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/password-reset/request}.
 */
public record PasswordResetRequestDto(

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        String email

) {}
