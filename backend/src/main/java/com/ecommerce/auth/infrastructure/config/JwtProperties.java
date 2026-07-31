package com.ecommerce.auth.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated configuration for JWT issuance.
 *
 * <p>Bound from the {@code jwt.*} namespace in {@code application.yml}.
 * All properties are mandatory — a missing or invalid value fails startup loudly
 * (backend-architecture §6, security §5.1: "no defaults for secrets").
 *
 * <p>The secret length is validated at the property level: HS256 requires a minimum
 * 256-bit (32 byte) key, which at ASCII encoding means at least 32 characters.
 * jjwt enforces this at runtime as well, but failing at startup is preferable.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(

        /**
         * HMAC-SHA256 signing secret. Minimum 32 ASCII characters (256 bits).
         * Injected from the {@code JWT_SECRET} environment variable via
         * {@code application.yml} placeholder — never a literal default.
         */
        @NotBlank
        @Size(min = 32, message = "jwt.secret must be at least 32 characters (256 bits) for HS256")
        String secret,

        /**
         * Access token expiry in minutes. Default 10 per security §6b.
         */
        @Min(1)
        long accessTokenExpiryMinutes

) {}
