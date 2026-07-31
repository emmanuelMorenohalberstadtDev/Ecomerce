package com.ecommerce.common.web;

import com.ecommerce.common.web.exception.AuthenticationException;
import com.ecommerce.common.web.exception.BusinessRuleViolationException;
import com.ecommerce.common.web.exception.ConflictException;
import com.ecommerce.common.web.exception.NotFoundException;
import com.ecommerce.common.web.exception.TokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single {@code @RestControllerAdvice} for the entire API — no per-controller try/catch for
 * response shaping, ever (backend-architecture §5).
 *
 * <p>Every error response follows RFC 9457 Problem Details. The {@code type} URI uses the slug
 * registry under {@code https://api.ecommerce.dev/problems/}; frontend keys UX off {@code type},
 * tests assert {@code type} + {@code status}, never message text.
 *
 * <p>500s leak nothing: only a correlation id and a generic message reach the client. Class names,
 * SQL, and stack frames stay in logs only.
 *
 * <p>Handler ordering: Spring processes handlers top-to-bottom within this class. More specific
 * types ({@code AuthenticationException}, {@code TokenException}) are declared before the generic
 * {@code BusinessRuleViolationException} handler so they are matched first.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String PROBLEMS_BASE = "https://api.ecommerce.dev/problems/";

    // -------------------------------------------------------------------------
    // Authentication / token handlers (must precede generic BusinessRuleViolation)
    // -------------------------------------------------------------------------

    /**
     * Invalid credentials and locked accounts → HTTP 401 with a single generic body.
     *
     * <p>Anti-enumeration (security §2.2, §2.6): the detail is identical whether the
     * user exists, the password is wrong, or the account is locked. The caller cannot
     * distinguish these cases from the response.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationFailure(
            AuthenticationException ex, HttpServletRequest request) {
        // WARN with no stack trace — expected path, not an error
        log.warn("Authentication failure [{}] on {}", ex.getClass().getSimpleName(),
                request.getRequestURI());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setType(URI.create(PROBLEMS_BASE + "invalid-credentials"));
        pd.setTitle("Unauthorized");
        // Generic detail — same for all auth failure subtypes (anti-enumeration)
        pd.setDetail("Invalid credentials");
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    /**
     * Token expired or reused:
     * <ul>
     *   <li>On refresh/logout paths → HTTP 401 (client must re-authenticate).</li>
     *   <li>On password-reset/consume → HTTP 400 (generic "invalid or expired token").</li>
     * </ul>
     *
     * <p>The path-based status selection keeps the exception types simple (domain need not
     * know HTTP semantics) while delivering the correct status per context.
     */
    @ExceptionHandler(TokenException.class)
    public ResponseEntity<ProblemDetail> handleTokenFailure(
            TokenException ex, HttpServletRequest request) {
        log.info("Token error [{}] on {}", ex.getClass().getSimpleName(), request.getRequestURI());

        boolean isPasswordResetConsumePath = request.getRequestURI().contains("/password-reset/consume");
        if (isPasswordResetConsumePath) {
            ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            pd.setType(URI.create(PROBLEMS_BASE + "invalid-or-expired-token"));
            pd.setTitle("Bad Request");
            pd.setDetail("The password reset token is invalid or has expired");
            pd.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
        }

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setType(URI.create(PROBLEMS_BASE + "token-expired"));
        pd.setTitle("Unauthorized");
        pd.setDetail("Token is expired or has already been used — please log in again");
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    // -------------------------------------------------------------------------
    // Domain exception handlers
    // -------------------------------------------------------------------------

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            NotFoundException ex, HttpServletRequest request) {
        // WARN level — expected business outcome, no stack trace
        log.warn("Not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create(PROBLEMS_BASE + "not-found"));
        pd.setTitle("Not Found");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setType(URI.create(PROBLEMS_BASE + "conflict"));
        pd.setTitle("Conflict");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRuleViolation(
            BusinessRuleViolationException ex, HttpServletRequest request) {
        log.warn("Business rule violation: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create(PROBLEMS_BASE + "business-rule-violation"));
        pd.setTitle("Business Rule Violation");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(pd);
    }

    // -------------------------------------------------------------------------
    // Bean Validation — 400 with field errors extension
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        // Validation errors are metrics, not logs (backend-architecture §5)
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create(PROBLEMS_BASE + "validation-error"));
        pd.setTitle("Validation Error");
        pd.setDetail("Request contains invalid fields");
        pd.setInstance(URI.create(request.getRequestURI()));

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        pd.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    // -------------------------------------------------------------------------
    // Catch-all — 500, leaks nothing to the client
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        // ERROR level with full stack trace — stays in logs, never reaches the response body
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setType(URI.create(PROBLEMS_BASE + "internal-error"));
        pd.setTitle("Internal Server Error");
        pd.setDetail("An unexpected error occurred. Please try again later.");
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
    }
}
