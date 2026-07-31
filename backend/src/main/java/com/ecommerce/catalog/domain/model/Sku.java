package com.ecommerce.catalog.domain.model;

import java.util.regex.Pattern;

/**
 * Value object wrapping a validated, normalized stock-keeping unit code.
 *
 * <p>Compact constructor enforces: non-null/blank, trim + uppercase (SKUs are
 * merchant-assigned catalog codes, conventionally uppercase — normalizing here means
 * "abc-123" and "ABC-123" are the same SKU, matching the DB's plain {@code unique index}
 * on the raw column, which is case-sensitive at the Postgres level; normalizing in the
 * domain is what makes the uniqueness guarantee actually hold in practice), 1–64 characters,
 * and restricted to a conservative charset (letters, digits, hyphen, underscore) that avoids
 * whitespace/control characters ending up in URLs (SKU appears in admin lookups) or logs.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record Sku(String value) {

    private static final int MAX_LENGTH = 64;
    private static final Pattern ALLOWED_CHARS = Pattern.compile("^[A-Z0-9_-]+$");

    public Sku {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Sku must not be null or blank");
        }
        value = value.trim().toUpperCase();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Sku must not exceed " + MAX_LENGTH + " characters, got: " + value.length());
        }
        if (!ALLOWED_CHARS.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Sku may only contain letters, digits, hyphen, and underscore: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
