package com.ecommerce.shared.uuid;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

/**
 * Application-side UUIDv7 factory.
 *
 * <p>{@link TimeBasedEpochGenerator} is documented as thread-safe by the
 * java-uuid-generator library; the singleton instance is safe to share across
 * concurrent calls.
 *
 * <p>No Spring, JPA, or Jackson imports — safe for the shared-kernel layer.
 */
public final class UuidV7Generator {

    private static final TimeBasedEpochGenerator GENERATOR =
            Generators.timeBasedEpochGenerator();

    private UuidV7Generator() {}

    public static java.util.UUID generate() {
        return GENERATOR.generate();
    }
}
