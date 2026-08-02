package com.ecommerce.checkout.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed, validated configuration for the checkout context.
 *
 * <p>Bound from the {@code checkout.*} namespace in {@code application.yml}
 * (backend-architecture §6: "every config group is a {@code @ConfigurationProperties} record ...
 * no scattered {@code @Value}"). Mirrors {@code auth.infrastructure.config.JwtProperties}.
 */
@Validated
@ConfigurationProperties(prefix = "checkout")
public record CheckoutProperties(

        /**
         * The payment window (domain-model.md §1/§5 "Payment window", rules 5/8): how long a
         * customer has to pay after stock is reserved before the session expires. Default
         * {@code PT15M} (15 minutes) per domain-model.md; overridable per environment.
         */
        @NotNull
        Duration paymentWindow

) {}
