package com.ecommerce.common.web;

import java.net.URI;
import java.util.List;

import com.ecommerce.common.exception.BusinessRuleException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.DomainException;
import com.ecommerce.common.exception.NotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single global mapping of failures to RFC 9457 problem details
 * (api-guidelines.md §3). Internals never reach clients; unexpected errors
 * carry the correlation id so logs can be found from the response.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TYPE_BASE = "https://api.ecommerce.dev/problems/";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return toProblem(ex, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        log.warn("domain.conflict type={} correlationId={}", ex.type(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return toProblem(ex, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException ex) {
        log.warn("domain.rule-violation type={} correlationId={}", ex.type(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return toProblem(ex, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(URI.create(TYPE_BASE + "validation-error"));
        problem.setTitle("validation error");
        List<FieldErrorEntry> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorEntry(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        log.error("unexpected.error correlationId={}", correlationId, ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setType(URI.create(TYPE_BASE + "internal-error"));
        problem.setTitle("internal error");
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    private ProblemDetail toProblem(DomainException ex, HttpStatus status) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(URI.create(TYPE_BASE + ex.type()));
        problem.setTitle(ex.type().replace('-', ' '));
        ex.properties().forEach(problem::setProperty);
        return problem;
    }

    record FieldErrorEntry(String field, String code, String message) {
    }
}
