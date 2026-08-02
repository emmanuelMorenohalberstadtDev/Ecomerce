package com.ecommerce.checkout.presentation;

import com.ecommerce.checkout.application.usecase.CheckoutOutcome;
import com.ecommerce.checkout.application.usecase.ConfirmRecalculatedTotalUseCase;
import com.ecommerce.checkout.application.usecase.ConfirmRecalculatedTotalUseCase.ConfirmRecalculatedTotalCommand;
import com.ecommerce.checkout.application.usecase.GetCheckoutSessionUseCase;
import com.ecommerce.checkout.application.usecase.StartCheckoutUseCase;
import com.ecommerce.checkout.application.usecase.StartCheckoutUseCase.StartCheckoutCommand;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.presentation.dto.CheckoutSessionResponse;
import com.ecommerce.checkout.presentation.dto.ConfirmTotalRequest;
import com.ecommerce.checkout.presentation.dto.MoneyDto;
import com.ecommerce.checkout.presentation.dto.StartCheckoutRequest;
import com.ecommerce.checkout.presentation.mapper.CheckoutSessionMapper;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.UserId;
import com.ecommerce.shared.money.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

/**
 * REST controller for the checkout process: {@code /api/v1/checkout-sessions}.
 *
 * <p>Route set:
 * <ul>
 *   <li>{@code POST   /api/v1/checkout-sessions}                   — start checkout</li>
 *   <li>{@code POST   /api/v1/checkout-sessions/{id}/confirmation} — confirm a re-shown total</li>
 *   <li>{@code GET    /api/v1/checkout-sessions/{id}}               — view a session</li>
 * </ul>
 *
 * <p><strong>Resource naming (api-guidelines §1.2/§1.3):</strong> {@code checkout-sessions} (a
 * plural noun — "checkout" alone is not one) as the top-level resource; the re-confirmation step
 * is a sub-resource, {@code POST .../{id}/confirmation} — "the transition is the thing being
 * created" (§1.3), not a {@code PATCH status=X} tunnel.
 *
 * <p><strong>Auth (security-architecture §3.4 "Checkout & payment ops" row):</strong> GUEST — 401
 * (no {@code permitAll()} entry for this path in {@code AuthSecurityConfiguration}; it falls
 * through to the default {@code .anyRequest().authenticated()} rule — checkout has no guest path,
 * unlike cart's deliberate {@code permitAll()}); CUSTOMER — own session only, else 404; ADMIN —
 * 403 ({@code @PreAuthorize("hasRole('CUSTOMER')")} on every use case, security §3.2 fine layer).
 * Because there is no guest path, this controller never resolves a null customer id the way
 * {@code CartController} does for anonymous callers — see {@link #resolveCustomerId}.
 * <table>
 *   <caption>Auth decision table for this controller</caption>
 *   <tr><th>Endpoint</th><th>GUEST</th><th>CUSTOMER</th><th>ADMIN</th><th>Notes</th></tr>
 *   <tr><td>POST /checkout-sessions</td><td>401</td><td>200 or 201</td><td>403</td>
 *       <td>200 PENDING_CONFIRMATION (price changed), 201 AWAITING_PAYMENT (reserved)</td></tr>
 *   <tr><td>POST /checkout-sessions/{id}/confirmation</td><td>401</td><td>200, 201, or 404</td><td>403</td>
 *       <td>404 if not the caller's session</td></tr>
 *   <tr><td>GET /checkout-sessions/{id}</td><td>401</td><td>200 or 404</td><td>403</td>
 *       <td>404 if not the caller's session</td></tr>
 * </table>
 *
 * <p><strong>Idempotency-Key (api-guidelines §5.1, security-architecture §1.4 case 6):</strong>
 * mandatory on {@code POST /checkout-sessions}. Bound with {@code required = false,
 * defaultValue = ""} rather than the {@code @RequestHeader} default ({@code required = true}) —
 * a genuinely missing header would otherwise throw {@code MissingRequestHeaderException}, which
 * this codebase's {@code ApiExceptionHandler} has no dedicated handler for (it would fall through
 * to the generic 500 handler, which this change is not permitted to edit). Defaulting to
 * {@code ""} and validating with {@code @NotBlank} routes both "missing" and "blank" through the
 * already-handled {@code HandlerMethodValidationException} path (400), with no need to touch
 * {@code ApiExceptionHandler}.
 *
 * <p>No try/catch — error translation is handled entirely by {@code ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/checkout-sessions")
@Validated
public class CheckoutController {

    private final StartCheckoutUseCase startCheckoutUseCase;
    private final ConfirmRecalculatedTotalUseCase confirmRecalculatedTotalUseCase;
    private final GetCheckoutSessionUseCase getCheckoutSessionUseCase;

    public CheckoutController(StartCheckoutUseCase startCheckoutUseCase,
                              ConfirmRecalculatedTotalUseCase confirmRecalculatedTotalUseCase,
                              GetCheckoutSessionUseCase getCheckoutSessionUseCase) {
        this.startCheckoutUseCase = startCheckoutUseCase;
        this.confirmRecalculatedTotalUseCase = confirmRecalculatedTotalUseCase;
        this.getCheckoutSessionUseCase = getCheckoutSessionUseCase;
    }

    @PostMapping
    public ResponseEntity<CheckoutSessionResponse> startCheckout(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false, defaultValue = "")
            @NotBlank(message = "Idempotency-Key header is required")
            @Size(max = 255, message = "Idempotency-Key must be at most 255 characters")
            String idempotencyKey,
            @RequestBody @Valid StartCheckoutRequest request) {
        CustomerId customerId = resolveCustomerId(authentication);

        CheckoutOutcome outcome = startCheckoutUseCase.execute(new StartCheckoutCommand(
                customerId, idempotencyKey, toMoney(request.expectedTotal())));

        return toResponseEntity(outcome);
    }

    @PostMapping("/{id}/confirmation")
    public ResponseEntity<CheckoutSessionResponse> confirmRecalculatedTotal(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody @Valid ConfirmTotalRequest request) {
        CustomerId customerId = resolveCustomerId(authentication);

        CheckoutOutcome outcome = confirmRecalculatedTotalUseCase.execute(new ConfirmRecalculatedTotalCommand(
                CheckoutSessionId.of(id), customerId, toMoney(request.confirmedTotal())));

        return toResponseEntity(outcome);
    }

    @GetMapping("/{id}")
    public CheckoutSessionResponse getCheckoutSession(Authentication authentication, @PathVariable UUID id) {
        CustomerId customerId = resolveCustomerId(authentication);
        CheckoutSession session = getCheckoutSessionUseCase.execute(CheckoutSessionId.of(id), customerId);
        return CheckoutSessionMapper.toResponse(session);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Maps a {@link CheckoutOutcome} to its HTTP representation: {@link CheckoutOutcome.Confirmed}
     * (stock reserved, session now {@code AWAITING_PAYMENT}) is a 201 with a {@code Location}
     * header pointing at the session (api-guidelines §2: "resource created");
     * {@link CheckoutOutcome.NeedsReconfirmation} (still {@code PENDING_CONFIRMATION}, new total
     * to show) is a 200 (api-guidelines §2: "mutation succeeded, useful body — return what the
     * client needs next").
     */
    private static ResponseEntity<CheckoutSessionResponse> toResponseEntity(CheckoutOutcome outcome) {
        CheckoutSessionResponse body = CheckoutSessionMapper.toResponse(outcome.session());
        if (outcome instanceof CheckoutOutcome.Confirmed) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .location(URI.create("/api/v1/checkout-sessions/" + outcome.session().getId()))
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    private static Money toMoney(MoneyDto dto) {
        return new Money(new BigDecimal(dto.amount()), dto.currency());
    }

    /**
     * Extracts the caller's {@link CustomerId} from the JWT-derived principal. Checkout has no
     * guest path (security-architecture §3.4: checkout GUEST access is 401) — unlike
     * {@code CartController.resolveCustomerId}, this never returns {@code null}: the coarse route
     * rule (no {@code permitAll()} entry for this path) already rejects an unauthenticated caller
     * with 401 before this method ever runs, so {@code authentication} is always a genuine,
     * non-anonymous principal here. The check below is defensive only.
     */
    private static CustomerId resolveCustomerId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserId userId)) {
            throw new IllegalStateException(
                    "Unauthenticated request reached CheckoutController — the security filter chain "
                            + "should have rejected this with 401 before the controller ran");
        }
        return CustomerId.of(userId.value());
    }
}
