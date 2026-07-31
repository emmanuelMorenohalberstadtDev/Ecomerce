package com.ecommerce.auth.domain.model;

/**
 * Value object wrapping a BCrypt password hash.
 *
 * <p>Construction accepts the raw hash string produced by the application's PasswordEncoder
 * (BCrypt format {@code $2a$NN$...}). Format is intentionally NOT validated here —
 * validation would tie domain code to BCrypt specifics. The caller is responsible for
 * always passing a properly encoded hash, never plaintext.
 *
 * <p>{@link #toString()} returns {@code "[PROTECTED]"} unconditionally so that this value
 * object is safe to include in log statements, exception messages, and toString output of
 * containing aggregates. The raw hash value is therefore never written to logs.
 *
 * <p>{@link #matches(String, PasswordEncoder)} delegates matching to the provided encoder,
 * keeping BCrypt out of the domain layer while still expressing the intent in domain terms.
 *
 * <p>No Spring/JPA/Jackson imports — satisfies domain_is_framework_free.
 */
public record PasswordHash(String value) {

    /**
     * Functional interface matching only what the domain needs — isolates
     * domain from {@code org.springframework.security.crypto.password.PasswordEncoder}.
     */
    @FunctionalInterface
    public interface PasswordEncoder {
        boolean matches(CharSequence rawPassword, String encodedPassword);
    }

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PasswordHash value must not be null or blank");
        }
    }

    /**
     * Tests whether {@code rawPassword} matches this hash using the provided encoder.
     *
     * @param rawPassword the plaintext password to check
     * @param encoder     the encoder that understands the hash format (BCrypt)
     * @return {@code true} if the raw password matches this hash
     */
    public boolean matches(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, value);
    }

    /**
     * Always returns {@code "[PROTECTED]"} — the hash value is never exposed in
     * string representations to prevent accidental logging of credentials.
     */
    @Override
    public String toString() {
        return "[PROTECTED]";
    }
}
