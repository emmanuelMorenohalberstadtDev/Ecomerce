package com.ecommerce.catalog.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for {@link CategoryId} value object. */
@Tag("unit")
class CategoryIdTest {

    @Test
    void generate_producesNonNullId() {
        assertThat(CategoryId.generate()).isNotNull();
        assertThat(CategoryId.generate().value()).isNotNull();
    }

    @Test
    void generate_producesDistinctIds() {
        assertThat(CategoryId.generate()).isNotEqualTo(CategoryId.generate());
    }

    @Test
    void of_uuid_wrapsGivenValue() {
        UUID uuid = UUID.randomUUID();

        assertThat(CategoryId.of(uuid).value()).isEqualTo(uuid);
    }

    @Test
    void of_string_parsesValidUuidString() {
        UUID uuid = UUID.randomUUID();

        assertThat(CategoryId.of(uuid.toString()).value()).isEqualTo(uuid);
    }

    @Test
    void of_string_throwsIllegalArgumentException_whenNotAValidUuid() {
        assertThatThrownBy(() -> CategoryId.of("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsIllegalArgumentException_whenValueIsNull() {
        assertThatThrownBy(() -> new CategoryId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString_returnsUuidString() {
        UUID uuid = UUID.randomUUID();

        assertThat(CategoryId.of(uuid).toString()).isEqualTo(uuid.toString());
    }
}
