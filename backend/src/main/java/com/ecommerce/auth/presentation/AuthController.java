package com.ecommerce.auth.presentation;

import com.ecommerce.auth.application.usecase.ConsumePasswordResetUseCase;
import com.ecommerce.auth.application.usecase.ConsumePasswordResetUseCase.ConsumePasswordResetCommand;
import com.ecommerce.auth.application.usecase.LoginUseCase;
import com.ecommerce.auth.application.usecase.LoginUseCase.LoginCommand;
import com.ecommerce.auth.application.usecase.LoginUseCase.LoginResult;
import com.ecommerce.auth.application.usecase.LogoutUseCase;
import com.ecommerce.auth.application.usecase.RefreshTokenUseCase;
import com.ecommerce.auth.application.usecase.RegisterUserUseCase;
import com.ecommerce.auth.application.usecase.RegisterUserUseCase.RegisterCommand;
import com.ecommerce.auth.application.usecase.RequestPasswordResetUseCase;
import com.ecommerce.auth.application.usecase.RequestPasswordResetUseCase.RequestPasswordResetCommand;
import com.ecommerce.auth.presentation.dto.PasswordResetConsumeDto;
import com.ecommerce.auth.presentation.dto.PasswordResetRequestDto;
import com.ecommerce.auth.presentation.dto.RegisterRequest;
import com.ecommerce.auth.presentation.dto.LoginRequest;
import com.ecommerce.auth.presentation.dto.TokenResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * REST controller for authentication endpoints.
 *
 * <p>Responsibilities of this class:
 * <ul>
 *   <li>Translate HTTP request records to use-case commands.</li>
 *   <li>Delegate all logic to use cases — no business logic here.</li>
 *   <li>Translate use-case results to HTTP response records.</li>
 * </ul>
 *
 * <p>No try/catch blocks — error translation is handled entirely by
 * {@link com.ecommerce.common.web.ApiExceptionHandler} (backend-architecture §5).
 *
 * <p>All endpoints under {@code /api/v1/auth/**} are publicly accessible per the
 * security filter chain (rate-limited at nginx per security §5.2).
 *
 * <p>Refresh token delivery (security §2.5): the raw refresh token is NEVER included
 * in the JSON response body. It is set as an {@code HttpOnly; Secure; SameSite=Strict}
 * cookie scoped to {@code /api/v1/auth}. The {@code /refresh} and {@code /logout}
 * endpoints read the token from that cookie, not from the request body.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    /** Cookie name for the refresh token (security §2.5). */
    static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    /** Lifetime of the refresh token cookie — must match the token's DB TTL. */
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(14);

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ConsumePasswordResetUseCase consumePasswordResetUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                          LoginUseCase loginUseCase,
                          RefreshTokenUseCase refreshTokenUseCase,
                          LogoutUseCase logoutUseCase,
                          RequestPasswordResetUseCase requestPasswordResetUseCase,
                          ConsumePasswordResetUseCase consumePasswordResetUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.consumePasswordResetUseCase = consumePasswordResetUseCase;
    }

    /**
     * Register a new user account.
     *
     * <p>Anti-enumeration: always returns 201 Created, even if the email is already
     * registered. The controller cannot distinguish the two outcomes — that is by design.
     *
     * @return 201 Created with empty body
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody @Valid RegisterRequest request) {
        registerUserUseCase.execute(new RegisterCommand(request.email(), request.password()));
    }

    /**
     * Authenticate with email + password.
     *
     * <p>The refresh token is set as an httpOnly cookie. Only the access token is
     * returned in the JSON body.
     *
     * @return 200 OK with access token in body; refresh token in Set-Cookie header
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request,
                                               HttpServletResponse response) {
        LoginResult result = loginUseCase.execute(
                new LoginCommand(request.email(), request.password()));
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(new TokenResponse(result.accessToken()));
    }

    /**
     * Rotate a refresh token and issue a new token pair.
     *
     * <p>The refresh token is read from the {@code refreshToken} cookie.
     * The new refresh token is set as a new cookie. Only the access token is in the body.
     *
     * @return 200 OK with new access token in body; new refresh token in Set-Cookie header
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        LoginResult result = refreshTokenUseCase.execute(refreshToken);
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(new TokenResponse(result.accessToken()));
    }

    /**
     * Revoke the refresh token family (server-side logout).
     *
     * <p>The refresh token is read from the {@code refreshToken} cookie.
     * On success the cookie is immediately cleared (maxAge=0).
     *
     * @return 204 No Content
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        logoutUseCase.execute(refreshToken);
        clearRefreshTokenCookie(response);
    }

    /**
     * Request a password reset email.
     *
     * <p>Anti-enumeration: always returns 202 Accepted, regardless of whether the
     * email is registered. The exact same body and status on both branches (§2.6).
     *
     * @return 202 Accepted with empty body
     */
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@RequestBody @Valid PasswordResetRequestDto request) {
        requestPasswordResetUseCase.execute(new RequestPasswordResetCommand(request.email()));
    }

    /**
     * Consume a password reset token and set a new password.
     *
     * @return 204 No Content on success
     */
    @PostMapping("/password-reset/consume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consumePasswordReset(@RequestBody @Valid PasswordResetConsumeDto request) {
        consumePasswordResetUseCase.execute(
                new ConsumePasswordResetCommand(request.token(), request.newPassword()));
    }

    // -------------------------------------------------------------------------
    // Cookie helpers
    // -------------------------------------------------------------------------

    private void setRefreshTokenCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(REFRESH_COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
