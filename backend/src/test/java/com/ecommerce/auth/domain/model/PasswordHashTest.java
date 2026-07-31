package com.ecommerce.auth.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PasswordHash} value object.
 *
 * <p>Critical security property: {@code toString()} NEVER reveals the hash value —
 * this prevents accidental credential leakage via logs, exception messages, or
 * aggregate {@code toString()} output.
 */
@Tag("unit")
class PasswordHashTest {

    private static final String SAMPLE_BCRYPT_HASH =
            "$2a$12$KIXmSfbLHoUBHNZMKYIkO.2jHrTVqAh9sNijmF1JOOiQDPZ3e7XgS";

    // ── toString() security contract ───────────────────────────────────────────

    @Test
    void toString_returnsProtectedLiteral_neverTheActualHash() {
        PasswordHash hash = new PasswordHash(SAMPLE_BCRYPT_HASH);

        assertThat(hash.toString()).isEqualTo("[PROTECTED]");
    }

    @Test
    void toString_doesNotContainHashValue_underAnyCircumstances() {
        PasswordHash hash = new PasswordHash(SAMPLE_BCRYPT_HASH);

        assertThat(hash.toString()).doesNotContain(SAMPLE_BCRYPT_HASH);
        assertThat(hash.toString()).doesNotContain("$2a$");
    }

    // ── matches() delegation ──────────────────────────────────────────────────

    @Test
    void matches_returnsTrue_whenEncoderConfirmsMatch() {
        PasswordHash hash = new PasswordHash(SAMPLE_BCRYPT_HASH);
        // Stub encoder that always says "match"
        PasswordHash.PasswordEncoder encoder = (raw, encoded) -> true;

        assertThat(hash.matches("anyPassword", encoder)).isTrue();
    }

    @Test
    void matches_returnsFalse_whenEncoderDeniesMatch() {
        PasswordHash hash = new PasswordHash(SAMPLE_BCRYPT_HASH);
        // Stub encoder that always says "no match"
        PasswordHash.PasswordEncoder encoder = (raw, encoded) -> false;

        assertThat(hash.matches("wrongPassword", encoder)).isFalse();
    }

    @Test
    void matches_passesCorrectArgumentsToEncoder() {
        PasswordHash hash = new PasswordHash(SAMPLE_BCRYPT_HASH);

        String[] capturedRaw = new String[1];
        String[] capturedEncoded = new String[1];

        PasswordHash.PasswordEncoder capturingEncoder = (raw, encoded) -> {
            capturedRaw[0] = raw.toString();
            capturedEncoded[0] = encoded;
            return false;
        };

        hash.matches("myRawPassword", capturingEncoder);

        assertThat(capturedRaw[0]).isEqualTo("myRawPassword");
        assertThat(capturedEncoded[0]).isEqualTo(SAMPLE_BCRYPT_HASH);
    }

    // ── construction validation ────────────────────────────────────────────────

    @Test
    void shouldThrowIllegalArgumentException_whenValueIsNull() {
        assertThatThrownBy(() -> new PasswordHash(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenValueIsBlank() {
        assertThatThrownBy(() -> new PasswordHash("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── value() accessor ──────────────────────────────────────────────────────

    @Test
    void value_returnsRawHash_forAdapterUseOnly() {
        // The value() method is accessed by the persistence adapter only.
        // This test verifies correctness, not that callers should log it.
        PasswordHash hash = new PasswordHash(SAMPLE_BCRYPT_HASH);

        assertThat(hash.value()).isEqualTo(SAMPLE_BCRYPT_HASH);
    }
}
