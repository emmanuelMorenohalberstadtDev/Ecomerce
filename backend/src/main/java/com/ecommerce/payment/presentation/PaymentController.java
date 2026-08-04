package com.ecommerce.payment.presentation;

import com.ecommerce.payment.application.usecase.SubmitPaymentUseCase;
import com.ecommerce.payment.application.usecase.SubmitPaymentUseCase.SubmitPaymentCommand;
import com.ecommerce.payment.application.usecase.SubmitPaymentUseCase.SubmitPaymentResult;
import com.ecommerce.payment.presentation.dto.PaymentResponse;
import com.ecommerce.payment.presentation.mapper.PaymentMapper;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.OrderId;
import com.ecommerce.shared.id.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for submitting a payment against a customer's own order:
 * {@code POST /api/v1/orders/{orderId}/payments} (ADR-0005).
 *
 * <p><strong>Resource naming (api-guidelines §1.2/§1.3):</strong> payment is a sub-resource of the
 * order it charges — mirrors {@code OrderController}'s {@code .../{id}/cancellation} and
 * {@code CheckoutController}'s {@code .../{id}/confirmation} shape.
 *
 * <p><strong>Auth (security-architecture §3.2/§3.3, mirroring {@code OrderController}/
 * {@code CheckoutController}):</strong> GUEST — 401 (no {@code permitAll()} entry for
 * {@code /api/v1/orders/**}; falls through to {@code .anyRequest().authenticated()}); CUSTOMER —
 * own order only, else 404 ({@code @PreAuthorize("hasRole('CUSTOMER')")} on
 * {@link SubmitPaymentUseCase}, security §3.2 fine layer); ADMIN — 403 (admins don't shop).
 * Ownership is enforced by {@code payment.domain.port.out.OrderPort.getForPayment}'s
 * ownership-scoped read (translated from order's own ownership-scoped repository lookup) — a
 * caller can never submit a payment against another customer's order.
 * <table>
 *   <caption>Auth decision table for this controller</caption>
 *   <tr><th>Endpoint</th><th>GUEST</th><th>CUSTOMER</th><th>ADMIN</th><th>Notes</th></tr>
 *   <tr><td>POST /orders/{orderId}/payments</td><td>401</td><td>200, 404, or 422</td><td>403</td>
 *       <td>404 if not the caller's order; 422 if the order is not currently payable</td></tr>
 * </table>
 *
 * <p><strong>Idempotency-Key (api-guidelines §5.1, ADR-0005 item 2):</strong> mandatory. Bound with
 * {@code required = false, defaultValue = ""} rather than the {@code @RequestHeader} default
 * ({@code required = true}) — a genuinely missing header would otherwise throw
 * {@code MissingRequestHeaderException}, unhandled by {@code ApiExceptionHandler}. Defaulting to
 * {@code ""} and validating with {@code @NotBlank} routes both "missing" and "blank" through the
 * already-handled {@code HandlerMethodValidationException} path (400), mirroring
 * {@code CheckoutController#startCheckout} exactly. {@code max = 128} matches
 * {@code payment_attempts.idempotency_key}'s {@code varchar(128)} column width
 * ({@code V007__create_payment_schema.sql}) — narrower than checkout's 255, deliberately, to avoid
 * a DB-level truncation surprise.
 *
 * <p><strong>No request body</strong>: deliberately no amount field anywhere on this endpoint
 * (ADR-0005 sanity check, security-critical) — the charge amount is read exclusively from the
 * order's own server-computed grand total.
 *
 * <p>No try/catch — error translation is handled entirely by {@code ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class PaymentController {

    private final SubmitPaymentUseCase submitPaymentUseCase;

    public PaymentController(SubmitPaymentUseCase submitPaymentUseCase) {
        this.submitPaymentUseCase = submitPaymentUseCase;
    }

    @PostMapping("/{orderId}/payments")
    public ResponseEntity<PaymentResponse> submitPayment(
            Authentication authentication,
            @PathVariable UUID orderId,
            @RequestHeader(value = "Idempotency-Key", required = false, defaultValue = "")
            @NotBlank(message = "Idempotency-Key header is required")
            @Size(max = 128, message = "Idempotency-Key must be at most 128 characters")
            String idempotencyKey) {
        CustomerId customerId = resolveCustomerId(authentication);

        SubmitPaymentResult result = submitPaymentUseCase.execute(
                new SubmitPaymentCommand(OrderId.of(orderId), customerId, idempotencyKey));

        return ResponseEntity.ok(PaymentMapper.toResponse(result));
    }

    /**
     * Extracts the caller's {@link CustomerId} from the JWT-derived principal. This route has no
     * guest path (falls under {@code /api/v1/orders/**}, which has no {@code permitAll()} entry) —
     * unlike {@code CartController.resolveCustomerId}, this never returns {@code null}: the coarse
     * route rule already rejects an unauthenticated caller with 401 before this method ever runs,
     * so {@code authentication} is always a genuine, non-anonymous principal here. The check below
     * is defensive only. Mirrors {@code OrderController.resolveCustomerId} exactly.
     */
    private static CustomerId resolveCustomerId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserId userId)) {
            throw new IllegalStateException(
                    "Unauthenticated request reached PaymentController — the security filter chain "
                            + "should have rejected this with 401 before the controller ran");
        }
        return CustomerId.of(userId.value());
    }
}
