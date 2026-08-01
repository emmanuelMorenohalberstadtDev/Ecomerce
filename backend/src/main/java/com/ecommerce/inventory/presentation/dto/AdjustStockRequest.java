package com.ecommerce.inventory.presentation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ecommerce.inventory.domain.model.AdjustmentReason;

/**
 * Request body for {@code POST /api/v1/admin/stock-items/{productId}/adjustments}.
 *
 * <p>{@code delta} is deliberately unconstrained on sign (positive = increase, negative =
 * decrease) but bounded in magnitude — api-guidelines §8.2 requires bounds on every field, and an
 * unbounded delta is a footgun (a typo'd extra zero should not be able to wipe out or explode a
 * stock level). {@code 0} is rejected via {@link #isDeltaNonZero()} (a Bean Validation
 * {@code @AssertTrue} method, not a compact-constructor exception — keeps the failure on the
 * standard {@code MethodArgumentNotValidException -> 400 validation-error} path instead of
 * risking an uncaught-constructor 500 during Jackson deserialization): a zero-delta "adjustment"
 * records nothing and audits nothing meaningful.
 */
public record AdjustStockRequest(

        @NotNull(message = "delta must be provided")
        @Min(value = -1_000_000, message = "delta must be >= -1000000")
        @Max(value = 1_000_000, message = "delta must be <= 1000000")
        Integer delta,

        @NotBlank(message = "reason must not be blank")
        @Size(max = AdjustmentReason.MAX_LENGTH, message = "reason must be at most " + AdjustmentReason.MAX_LENGTH + " characters")
        String reason

) {

    public AdjustStockRequest {
        if (reason != null) {
            reason = reason.strip();
        }
    }

    @AssertTrue(message = "delta must not be zero")
    public boolean isDeltaNonZero() {
        return delta == null || delta != 0;
    }
}
