package com.ecommerce.shared;

import com.ecommerce.shared.id.ProductId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProductId} — representative of all typed ID records.
 *
 * <p>No Spring context — pure JUnit 5.
 */
class ProductIdTest {

    // ── generate ──────────────────────────────────────────────────────────────

    @Test
    void generate_producesDistinctIds() {
        ProductId first = ProductId.generate();
        ProductId second = ProductId.generate();

        assertNotEquals(first, second,
                "Two successive generate() calls must not return the same ID");
    }

    @Test
    void generate_returnsNonNullId() {
        ProductId id = ProductId.generate();

        assertNotNull(id);
        assertNotNull(id.value());
    }

    // ── of(String) roundtrip ──────────────────────────────────────────────────

    @Test
    void of_string_roundtripViaToString() {
        ProductId original = ProductId.generate();
        String serialised = original.toString();

        ProductId restored = ProductId.of(serialised);

        assertEquals(original, restored);
    }

    @Test
    void of_string_equalToOfUuid() {
        UUID uuid = UUID.randomUUID();
        ProductId fromString = ProductId.of(uuid.toString());
        ProductId fromUuid = ProductId.of(uuid);

        assertEquals(fromString, fromUuid);
    }

    // ── null guard ────────────────────────────────────────────────────────────

    @Test
    void of_nullUuid_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ProductId.of((UUID) null));
    }

    @Test
    void of_nullString_throwsNullPointerOrIllegalArgumentException() {
        // UUID.fromString(null) throws NullPointerException from the JDK;
        // our constructor would then get a null value and throw IllegalArgumentException.
        // Either is acceptable — what matters is that null is rejected.
        assertThrows(RuntimeException.class, () -> ProductId.of((String) null));
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_returnsUuidString() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ProductId id = ProductId.of(uuid);

        assertEquals("00000000-0000-0000-0000-000000000001", id.toString());
    }
}
