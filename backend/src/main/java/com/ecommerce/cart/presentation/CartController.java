package com.ecommerce.cart.presentation;

import com.ecommerce.cart.application.usecase.AddItemUseCase;
import com.ecommerce.cart.application.usecase.AddItemUseCase.AddItemCommand;
import com.ecommerce.cart.application.usecase.AddItemUseCase.AddItemResult;
import com.ecommerce.cart.application.usecase.GetOrCreateCartUseCase;
import com.ecommerce.cart.application.usecase.GetOrCreateCartUseCase.GetOrCreateCartResult;
import com.ecommerce.cart.application.usecase.MergeCartsUseCase;
import com.ecommerce.cart.application.usecase.RemoveLineUseCase;
import com.ecommerce.cart.application.usecase.RemoveLineUseCase.RemoveLineCommand;
import com.ecommerce.cart.application.usecase.UpdateLineQuantityUseCase;
import com.ecommerce.cart.application.usecase.UpdateLineQuantityUseCase.UpdateLineQuantityCommand;
import com.ecommerce.cart.application.usecase.ViewCartUseCase;
import com.ecommerce.cart.domain.model.Cart;
import com.ecommerce.cart.presentation.dto.AddItemRequest;
import com.ecommerce.cart.presentation.dto.CartResponse;
import com.ecommerce.cart.presentation.dto.UpdateLineQuantityRequest;
import com.ecommerce.cart.presentation.mapper.CartMapper;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.id.UserId;
import com.ecommerce.shared.quantity.Quantity;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * REST controller for cart endpoints — a "my cart" resource that transparently resolves the
 * caller's identity (guest cart token cookie or authenticated customer JWT). No cart id ever
 * appears in a URL: every endpoint resolves "my cart" purely from the presenting identity, which
 * both matches api-guidelines.md §1.8 nesting rules (one sub-resource level: {@code items}) and
 * sidesteps IDOR entirely for the cart resource itself (there is no path-addressed cart id to
 * guess or leak).
 *
 * <p>Route set (all under {@code /api/v1/carts}):
 * <ul>
 *   <li>{@code GET    /me}              — view my cart; 404 if none exists (no side effect)</li>
 *   <li>{@code POST   /me}              — get-or-create my cart; 200 if it existed, 201 if just created</li>
 *   <li>{@code POST   /me/items}        — add an item; auto-creates the cart if needed; 200 + cart summary</li>
 *   <li>{@code PUT    /me/items/{productId}} — replace a line's quantity; 200 + cart summary</li>
 *   <li>{@code DELETE /me/items/{productId}} — remove a line; 200 + cart summary (see note below)</li>
 * </ul>
 *
 * <p><strong>DELETE returns 200 with the cart summary, not 204:</strong> a deliberate deviation
 * from api-guidelines.md §2's generic DELETE→204 default (that table also explicitly says
 * "the endpoint design fixes which" for the return-useful-body cases). The shopping-cart skill's
 * best practice — "return the full cart summary from every mutation endpoint — the UI's single
 * source after each action" — applies identically to remove-line as to add/update, so all three
 * mutation endpoints share one response contract. Flagged for reviewer visibility.
 *
 * <p><strong>Guest cart token cookie</strong> (security-architecture §2.7, quoted): "Delivered as
 * {@code HttpOnly; Secure; SameSite=Lax; Path=/api/v1} cookie; TTL matches cart expiration
 * policy." {@link #GUEST_CART_COOKIE_MAX_AGE} (30 days) is an <em>assumption</em> — no TTL number
 * is documented anywhere in the repository; flagged pending product-owner confirmation, same
 * treatment as {@link Cart#MAX_QUANTITY_PER_LINE}.
 *
 * <p><strong>Opportunistic cart merge:</strong> every endpoint here calls {@code mergeIfNeeded}
 * first — if the caller is authenticated (JWT resolves a {@code CustomerId}) AND a guest cart
 * cookie is still present, {@link MergeCartsUseCase} runs before the main operation and the
 * cookie is then cleared. See {@code MergeCartsUseCase} javadoc for the full design-fork
 * rationale (why the merge is not triggered directly from the login flow).
 *
 * <p>Auth decision table (security-architecture §3.4 seed row "Cart operations", expanded per
 * endpoint as api-guidelines.md §7.3 requires):
 * <table>
 *   <caption>Auth decision table for this controller</caption>
 *   <tr><th>Endpoint</th><th>GUEST</th><th>CUSTOMER</th><th>ADMIN</th><th>Notes</th></tr>
 *   <tr><td>GET /me</td><td>200/404</td><td>200/404</td><td>403</td><td>own cart only, by identity</td></tr>
 *   <tr><td>POST /me</td><td>200/201</td><td>200/201</td><td>403</td><td>mints guest token cookie on creation</td></tr>
 *   <tr><td>POST /me/items</td><td>200</td><td>200</td><td>403</td><td>auto-creates cart</td></tr>
 *   <tr><td>PUT /me/items/{productId}</td><td>200/404</td><td>200/404</td><td>403</td><td>404 if no cart or no such line</td></tr>
 *   <tr><td>DELETE /me/items/{productId}</td><td>200/404</td><td>200/404</td><td>403</td><td>404 if no cart or no such line</td></tr>
 * </table>
 * ADMIN 403 is enforced by {@code @PreAuthorize("!hasRole('ADMIN')")} on every use case
 * (security-architecture §3.2 fine layer). The coarse route layer permits this whole path to
 * anonymous + authenticated callers alike via an explicit {@code .requestMatchers("/api/v1/carts/**").permitAll()}
 * in {@code AuthSecurityConfiguration} — {@code .anyRequest().authenticated()} would otherwise
 * reject truly anonymous requests, since Spring Security's {@code AuthenticatedAuthorizationManager}
 * requires both {@code isAuthenticated()} and {@code !isAnonymous(...)} (confirmed against Spring
 * Security 6.4.5 bytecode); {@code AnonymousAuthenticationToken.isAuthenticated()} being
 * {@code true} does not, by itself, satisfy Spring Security's {@code .authenticated()} check.
 *
 * <p>No try/catch — error translation is handled entirely by {@code ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/carts")
@Validated
public class CartController {

    static final String GUEST_CART_TOKEN_COOKIE = "guestCartToken";

    /** Assumption pending PO confirmation — see class javadoc. */
    private static final Duration GUEST_CART_COOKIE_MAX_AGE = Duration.ofDays(30);

    private final GetOrCreateCartUseCase getOrCreateCartUseCase;
    private final ViewCartUseCase viewCartUseCase;
    private final AddItemUseCase addItemUseCase;
    private final UpdateLineQuantityUseCase updateLineQuantityUseCase;
    private final RemoveLineUseCase removeLineUseCase;
    private final MergeCartsUseCase mergeCartsUseCase;

    public CartController(GetOrCreateCartUseCase getOrCreateCartUseCase,
                          ViewCartUseCase viewCartUseCase,
                          AddItemUseCase addItemUseCase,
                          UpdateLineQuantityUseCase updateLineQuantityUseCase,
                          RemoveLineUseCase removeLineUseCase,
                          MergeCartsUseCase mergeCartsUseCase) {
        this.getOrCreateCartUseCase = getOrCreateCartUseCase;
        this.viewCartUseCase = viewCartUseCase;
        this.addItemUseCase = addItemUseCase;
        this.updateLineQuantityUseCase = updateLineQuantityUseCase;
        this.removeLineUseCase = removeLineUseCase;
        this.mergeCartsUseCase = mergeCartsUseCase;
    }

    @GetMapping("/me")
    public CartResponse getMyCart(
            Authentication authentication,
            @CookieValue(name = GUEST_CART_TOKEN_COOKIE, required = false) String guestToken,
            HttpServletResponse response) {
        CustomerId customerId = resolveCustomerId(authentication);
        mergeIfNeeded(customerId, guestToken, response);
        Cart cart = viewCartUseCase.execute(customerId, guestToken);
        return CartMapper.toResponse(cart);
    }

    @PostMapping("/me")
    public ResponseEntity<CartResponse> getOrCreateMyCart(
            Authentication authentication,
            @CookieValue(name = GUEST_CART_TOKEN_COOKIE, required = false) String guestToken,
            HttpServletResponse response) {
        CustomerId customerId = resolveCustomerId(authentication);
        mergeIfNeeded(customerId, guestToken, response);

        GetOrCreateCartResult result = getOrCreateCartUseCase.execute(customerId, guestToken);
        if (result.newRawGuestToken() != null) {
            setGuestCartTokenCookie(response, result.newRawGuestToken());
        }

        CartResponse body = CartMapper.toResponse(result.cart());
        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .location(URI.create("/api/v1/carts/me"))
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/me/items")
    public ResponseEntity<CartResponse> addItem(
            Authentication authentication,
            @CookieValue(name = GUEST_CART_TOKEN_COOKIE, required = false) String guestToken,
            @RequestBody @Valid AddItemRequest request,
            HttpServletResponse response) {
        CustomerId customerId = resolveCustomerId(authentication);
        mergeIfNeeded(customerId, guestToken, response);

        AddItemResult result = addItemUseCase.execute(new AddItemCommand(
                customerId, guestToken, ProductId.of(request.productId()), Quantity.of(request.quantity())));
        if (result.newRawGuestToken() != null) {
            setGuestCartTokenCookie(response, result.newRawGuestToken());
        }
        return ResponseEntity.ok(CartMapper.toResponse(result.cart()));
    }

    @PutMapping("/me/items/{productId}")
    public CartResponse updateLineQuantity(
            Authentication authentication,
            @CookieValue(name = GUEST_CART_TOKEN_COOKIE, required = false) String guestToken,
            @PathVariable UUID productId,
            @RequestBody @Valid UpdateLineQuantityRequest request,
            HttpServletResponse response) {
        CustomerId customerId = resolveCustomerId(authentication);
        mergeIfNeeded(customerId, guestToken, response);

        Cart cart = updateLineQuantityUseCase.execute(new UpdateLineQuantityCommand(
                customerId, guestToken, ProductId.of(productId), Quantity.of(request.quantity())));
        return CartMapper.toResponse(cart);
    }

    @DeleteMapping("/me/items/{productId}")
    public CartResponse removeLine(
            Authentication authentication,
            @CookieValue(name = GUEST_CART_TOKEN_COOKIE, required = false) String guestToken,
            @PathVariable UUID productId,
            HttpServletResponse response) {
        CustomerId customerId = resolveCustomerId(authentication);
        mergeIfNeeded(customerId, guestToken, response);

        Cart cart = removeLineUseCase.execute(new RemoveLineCommand(customerId, guestToken, ProductId.of(productId)));
        return CartMapper.toResponse(cart);
    }

    // -------------------------------------------------------------------------
    // Identity resolution and cookie helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the caller's {@link CustomerId} from the JWT-derived principal, or {@code null}
     * for an anonymous (guest) caller.
     *
     * <p><strong>Assumption:</strong> {@code CustomerId} and {@link UserId} share the same
     * underlying UUID value — a customer's commerce-context identity is the same UUID as their
     * auth-context user id. Neither domain-model.md nor security-architecture.md defines this
     * mapping explicitly (auth owns {@code UserId}, cart owns {@code CustomerId}, kept distinct
     * "so the compiler catches accidental cross-context identity misuse" per {@code UserId}'s own
     * javadoc, which also says "the application layer is responsible for the mapping between
     * them"). Cart is the first consumer of {@code CustomerId} in the codebase, so there is no
     * existing precedent to follow or contradict. Flagged in the handoff report.
     *
     * <p>The instanceof check below also doubles as the anonymous/guest filter: Spring's default
     * anonymous principal is the literal string {@code "anonymousUser"}, which fails the
     * {@code UserId} check regardless of whether {@code authentication} itself is {@code null},
     * an {@code AnonymousAuthenticationToken}, or absent — no extra anonymous-detection logic
     * needed.
     */
    private static CustomerId resolveCustomerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (!(authentication.getPrincipal() instanceof UserId userId)) {
            return null;
        }
        return CustomerId.of(userId.value());
    }

    /**
     * If the caller is authenticated AND a guest cart cookie is still present, folds the guest
     * cart into the customer's cart (or claims it directly) and clears the now-invalid cookie.
     * See {@link MergeCartsUseCase} javadoc for why this runs here rather than at login.
     */
    private void mergeIfNeeded(CustomerId customerId, String guestToken, HttpServletResponse response) {
        if (customerId != null && guestToken != null && !guestToken.isBlank()) {
            mergeCartsUseCase.execute(customerId, guestToken);
            clearGuestCartTokenCookie(response);
        }
    }

    private void setGuestCartTokenCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from(GUEST_CART_TOKEN_COOKIE, rawToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1")
                .maxAge(GUEST_CART_COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearGuestCartTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(GUEST_CART_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
