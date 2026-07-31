package com.ecommerce.auth.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Email} value object.
 *
 * <p>No Spring context — pure domain logic exercised through the public constructor.
 */
@Tag("unit")
class EmailTest {

    // ── valid construction ─────────────────────────────────────────────────────

    @Test
    void shouldNormalizeToLowercase_whenUppercaseEmailProvided() {
        Email email = new Email("User@Example.COM");

        assertThat(email.value()).isEqualTo("user@example.com");
    }

    @Test
    void shouldTrimWhitespace_whenEmailHasLeadingOrTrailingSpaces() {
        Email email = new Email("  user@example.com  ");

        assertThat(email.value()).isEqualTo("user@example.com");
    }

    @Test
    void shouldPreserveValidEmail_whenAlreadyNormalized() {
        Email email = new Email("alice@example.com");

        assertThat(email.value()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldNormalizeAndTrim_whenMixedCaseWithSpaces() {
        Email email = new Email("  ALICE@Example.ORG  ");

        assertThat(email.value()).isEqualTo("alice@example.org");
    }

    @Test
    void toString_returnsEmailValue() {
        Email email = new Email("alice@example.com");

        assertThat(email.toString()).isEqualTo("alice@example.com");
    }

    // ── null and blank rejection ───────────────────────────────────────────────

    @Test
    void shouldThrowIllegalArgumentException_whenEmailIsNull() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenEmailIsEmpty() {
        assertThatThrownBy(() -> new Email(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenEmailIsBlank() {
        assertThatThrownBy(() -> new Email("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── format validation ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "invalid format: \"{0}\"")
    @ValueSource(strings = {
            "notanemail",
            "@nodomain",
            "noatsign",
            "missing@",
            "a@b",          // no TLD dot
            "a @b.com"      // space inside
    })
    void shouldThrowIllegalArgumentException_whenEmailFormatIsInvalid(String invalid) {
        assertThatThrownBy(() -> new Email(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
