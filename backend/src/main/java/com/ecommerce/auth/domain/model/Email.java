package com.ecommerce.auth.domain.model;

import java.util.regex.Pattern;

/**
 * Value object wrapping a validated, normalized email address.
 *
 * <p>Compact constructor enforces: non-null/blank, trim + lowercase, valid format.
 * The simple regex avoids RFC 5321's full complexity while catching obvious errors —
 * the authoritative guard is the DB citext unique index plus the email verification flow.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record Email(String value) {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email must not be null or blank");
        }
        value = value.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Email format is invalid: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
