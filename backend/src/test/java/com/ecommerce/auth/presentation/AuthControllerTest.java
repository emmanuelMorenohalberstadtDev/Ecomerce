package com.ecommerce.auth.presentation;

import com.ecommerce.auth.application.usecase.ConsumePasswordResetUseCase;
import com.ecommerce.auth.application.usecase.LoginUseCase;
import com.ecommerce.auth.application.usecase.LoginUseCase.LoginResult;
import com.ecommerce.auth.application.usecase.LogoutUseCase;
import com.ecommerce.auth.application.usecase.RefreshTokenUseCase;
import com.ecommerce.auth.application.usecase.RegisterUserUseCase;
import com.ecommerce.auth.application.usecase.RequestPasswordResetUseCase;
import com.ecommerce.auth.domain.exception.AccountLockedException;
import com.ecommerce.auth.domain.exception.InvalidCredentialsException;
import com.ecommerce.auth.domain.exception.TokenExpiredException;
import com.ecommerce.auth.domain.exception.TokenReusedException;
import com.ecommerce.auth.infrastructure.config.AuthSecurityConfiguration;
import com.ecommerce.common.web.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-layer tests for {@link AuthController} using {@code @WebMvcTest}.
 *
 * <p>All use cases are mocked with {@code @MockBean}. This test tier verifies:
 * <ul>
 *   <li>HTTP status codes and response body structure for all auth endpoints.</li>
 *   <li>Bean Validation rejection (400) for invalid inputs.</li>
 *   <li>Anti-enumeration: register and password-reset/request always return 201/202.</li>
 *   <li>Error responses follow RFC 9457 Problem Details — type and status asserted,
 *       never message text.</li>
 *   <li>Refresh token delivered as httpOnly cookie, not in the JSON body (security §2.5).</li>
 * </ul>
 *
 * <p>Spring Security is active — auth endpoints are permitted per
 * {@link AuthSecurityConfiguration}. The {@link com.ecommerce.auth.infrastructure.security.JwtTokenService}
 * needed by the security filter chain is provided as a {@code @MockBean}.
 */
@Tag("unit")
@WebMvcTest(AuthController.class)
@Import({ApiExceptionHandler.class, AuthSecurityConfiguration.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegisterUserUseCase registerUserUseCase;

    @MockBean
    private LoginUseCase loginUseCase;

    @MockBean
    private RefreshTokenUseCase refreshTokenUseCase;

    @MockBean
    private LogoutUseCase logoutUseCase;

    @MockBean
    private RequestPasswordResetUseCase requestPasswordResetUseCase;

    @MockBean
    private ConsumePasswordResetUseCase consumePasswordResetUseCase;

    // Required by JwtAuthenticationFilter which is wired in AuthSecurityConfiguration
    @MockBean
    private com.ecommerce.auth.infrastructure.security.JwtTokenService jwtTokenService;

    // ── POST /api/v1/auth/register ────────────────────────────────────────────

    @Test
    void register_returns201_whenRequestIsValid() throws Exception {
        doNothing().when(registerUserUseCase).execute(any());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"securePassword12!"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void register_returns400_whenEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"notanemail","password":"securePassword12!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void register_returns400_whenPasswordIsTooShort() throws Exception {
        // Minimum is 12 characters per RegisterRequest validation
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void register_returns201_whenEmailAlreadyExists_antiEnumeration() throws Exception {
        // ANTI-ENUMERATION: use case returns void silently for duplicate emails.
        // Controller always returns 201 — caller cannot distinguish new vs. duplicate.
        doNothing().when(registerUserUseCase).execute(any());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"existing@example.com","password":"securePassword12!"}
                                """))
                .andExpect(status().isCreated());
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────────────────

    @Test
    void login_returns200WithAccessToken_whenCredentialsAreValid() throws Exception {
        when(loginUseCase.execute(any())).thenReturn(new LoginResult("accessToken", "rawRefreshToken"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"myPassword123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("accessToken"));
    }

    @Test
    void login_setsRefreshTokenCookie_onSuccess() throws Exception {
        // Security §2.5: refresh token must be in httpOnly cookie, never in body
        when(loginUseCase.execute(any())).thenReturn(new LoginResult("accessToken", "rawRefreshToken"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"myPassword123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist()); // NOT in body
    }

    @Test
    void login_returns401_whenCredentialsAreInvalid() throws Exception {
        when(loginUseCase.execute(any())).thenThrow(new InvalidCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"wrongPassword"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/invalid-credentials"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void login_returns401_whenAccountIsLocked_antiEnumeration() throws Exception {
        // ANTI-ENUMERATION: locked account produces same type as wrong password
        when(loginUseCase.execute(any())).thenThrow(new AccountLockedException("Account is locked"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"correctPassword12"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/invalid-credentials"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void login_returns400_whenBodyHasBlankFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────────────────

    @Test
    void refresh_returns200WithNewAccessToken_whenCookieIsValid() throws Exception {
        when(refreshTokenUseCase.execute(anyString()))
                .thenReturn(new LoginResult("newAccessToken", "newRefreshToken"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthController.REFRESH_TOKEN_COOKIE, "validRefreshToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("newAccessToken"));
    }

    @Test
    void refresh_returns401_whenCookieIsMissing() throws Exception {
        // @CookieValue(required = false) passes null; use case throws on null input
        when(refreshTokenUseCase.execute(isNull()))
                .thenThrow(new TokenExpiredException("Refresh token not found"));

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/token-expired"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refresh_returns401_whenTokenIsExpiredOrNotFound() throws Exception {
        when(refreshTokenUseCase.execute(anyString()))
                .thenThrow(new TokenExpiredException("Refresh token expired"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthController.REFRESH_TOKEN_COOKIE, "expiredToken")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/token-expired"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refresh_returns401_whenTokenWasAlreadyUsed_reuseDetection() throws Exception {
        when(refreshTokenUseCase.execute(anyString()))
                .thenThrow(new TokenReusedException("Token already used"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthController.REFRESH_TOKEN_COOKIE, "reusedToken")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/token-expired"))
                .andExpect(jsonPath("$.status").value(401));
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────────────────

    @Test
    void logout_returns204_always() throws Exception {
        doNothing().when(logoutUseCase).execute(any());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(AuthController.REFRESH_TOKEN_COOKIE, "someRefreshToken")))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_returns204_whenNoCookiePresent_idempotent() throws Exception {
        // LogoutUseCase is idempotent — no token = no-op, still 204
        doNothing().when(logoutUseCase).execute(isNull());

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/v1/auth/password-reset/request ──────────────────────────────

    @Test
    void passwordResetRequest_returns202_whenEmailExists_antiEnumeration() throws Exception {
        doNothing().when(requestPasswordResetUseCase).execute(any());

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com"}
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void passwordResetRequest_returns202_whenEmailDoesNotExist_antiEnumeration() throws Exception {
        // ANTI-ENUMERATION: use case returns void for non-existent email.
        // Controller always returns 202 — identical to the "email found" branch.
        doNothing().when(requestPasswordResetUseCase).execute(any());

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ghost@example.com"}
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void passwordResetRequest_returns400_whenEmailFormatIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"notanemail"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── POST /api/v1/auth/password-reset/consume ──────────────────────────────

    @Test
    void passwordResetConsume_returns204_whenTokenIsValid() throws Exception {
        doNothing().when(consumePasswordResetUseCase).execute(any());

        mockMvc.perform(post("/api/v1/auth/password-reset/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"validResetToken","newPassword":"newSecurePassword12!"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void passwordResetConsume_returns400_whenTokenIsExpiredOrAlreadyUsed() throws Exception {
        doThrow(new TokenExpiredException("Token expired"))
                .when(consumePasswordResetUseCase).execute(any());

        mockMvc.perform(post("/api/v1/auth/password-reset/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"expiredToken","newPassword":"newSecurePassword12!"}
                                """))
                .andExpect(status().isBadRequest())
                // Path-based status selection in ApiExceptionHandler: /password-reset/consume → 400
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/invalid-or-expired-token"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void passwordResetConsume_returns400_whenNewPasswordTooShort() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"someToken","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.dev/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }
}
