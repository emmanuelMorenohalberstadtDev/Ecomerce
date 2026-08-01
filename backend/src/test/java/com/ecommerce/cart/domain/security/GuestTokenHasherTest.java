package com.ecommerce.cart.domain.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GuestTokenHasher} — a small, framework-free domain-layer class, so it
 * belongs in domain unit tests rather than integration.
 */
@Tag("unit")
class GuestTokenHasherTest {

    @Test
    void sha256Hex_isDeterministic_forSameInput() {
        String first = GuestTokenHasher.sha256Hex("raw-token-value");
        String second = GuestTokenHasher.sha256Hex("raw-token-value");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void sha256Hex_producesDifferentHashes_forDifferentInputs() {
        String a = GuestTokenHasher.sha256Hex("raw-token-a");
        String b = GuestTokenHasher.sha256Hex("raw-token-b");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void sha256Hex_returnsSixtyFourCharacterLowercaseHex() {
        String hash = GuestTokenHasher.sha256Hex("raw-token-value");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[0-9a-f]{64}$");
    }

    @Test
    void sha256Hex_throwsIllegalArgumentException_whenInputIsNull() {
        assertThatThrownBy(() -> GuestTokenHasher.sha256Hex(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sha256Hex_producesKnownDigest_forEmptyString() {
        // Well-known SHA-256 digest of the empty string — pins the algorithm/encoding choice.
        String hash = GuestTokenHasher.sha256Hex("");

        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
