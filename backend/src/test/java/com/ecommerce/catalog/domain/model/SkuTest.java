package com.ecommerce.catalog.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Sku} value object.
 *
 * <p>No Spring context — pure domain logic exercised through the public constructor.
 */
@Tag("unit")
class SkuTest {

    // ── valid construction / normalization ─────────────────────────────────────

    @Test
    void shouldNormalizeToUppercase_whenLowercaseSkuProvided() {
        Sku sku = new Sku("abc-123");

        assertThat(sku.value()).isEqualTo("ABC-123");
    }

    @Test
    void shouldTrimWhitespace_whenSkuHasLeadingOrTrailingSpaces() {
        Sku sku = new Sku("  ABC-123  ");

        assertThat(sku.value()).isEqualTo("ABC-123");
    }

    @Test
    void shouldAllowUnderscoresAndHyphens() {
        Sku sku = new Sku("SKU_ABC-123");

        assertThat(sku.value()).isEqualTo("SKU_ABC-123");
    }

    @Test
    void toString_returnsSkuValue() {
        Sku sku = new Sku("ABC-123");

        assertThat(sku.toString()).isEqualTo("ABC-123");
    }

    @Test
    void shouldAllowExactly64Characters() {
        String sixtyFourChars = "A".repeat(64);

        Sku sku = new Sku(sixtyFourChars);

        assertThat(sku.value()).hasSize(64);
    }

    // ── null and blank rejection ───────────────────────────────────────────────

    @Test
    void shouldThrowIllegalArgumentException_whenSkuIsNull() {
        assertThatThrownBy(() -> new Sku(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenSkuIsEmpty() {
        assertThatThrownBy(() -> new Sku(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenSkuIsBlank() {
        assertThatThrownBy(() -> new Sku("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── length and charset validation ──────────────────────────────────────────

    @Test
    void shouldThrowIllegalArgumentException_whenSkuExceedsMaxLength() {
        String tooLong = "A".repeat(65);

        assertThatThrownBy(() -> new Sku(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "invalid charset: \"{0}\"")
    @ValueSource(strings = {
            "ABC 123",   // whitespace inside
            "ABC/123",   // slash
            "ABC.123",   // dot
            "ABC#123",   // hash
            "ABC@123"    // at sign
    })
    void shouldThrowIllegalArgumentException_whenSkuHasDisallowedCharacters(String invalid) {
        assertThatThrownBy(() -> new Sku(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── value equality ──────────────────────────────────────────────────────────

    @Test
    void shouldBeEqual_whenNormalizedValueMatches() {
        Sku a = new Sku("abc-123");
        Sku b = new Sku("ABC-123");

        assertThat(a).isEqualTo(b);
    }
}
