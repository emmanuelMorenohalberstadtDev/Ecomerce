package com.ecommerce.cart.domain.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hashing of the opaque guest cart token — same algorithm/encoding as
 * {@code auth.infrastructure.security.TokenHasher}, deliberately re-implemented here rather than
 * reused directly. See the cart handoff report for the full rationale; summary: a first attempt
 * delegated to {@code TokenHasher} via a one-line class in {@code com.ecommerce.common.security},
 * which satisfied {@code ArchitectureTest.contexts_communicate_only_through_ports_or_events}
 * (rule 6) but failed {@code no_cycles_between_bounded_contexts} (rule 7) — that rule slices
 * *every* top-level {@code com.ecommerce} package including {@code common}, and auth's own
 * exception hierarchy already depends on {@code common.web.exception}, so routing
 * {@code common -> auth} closed a cycle {@code auth -> common -> auth}. Both rules are hard
 * gates this change may not edit, and {@code TokenHasher} is a read-only auth file this change
 * may not move — so an independent, algorithm-identical re-implementation is the only design
 * that satisfies every constraint.
 *
 * <p><strong>Lives in {@code cart.domain.security}, not {@code cart.infrastructure}:</strong>
 * both {@code cart.application.usecase.CartLookup} (application layer, needs to hash an
 * incoming raw guest cookie value to look up by hash) and
 * {@code cart.infrastructure.security.GuestCartTokenGenerator} (infrastructure layer, needs to
 * hash a freshly-minted raw token) need this. Only the domain layer is reachable from both
 * without breaking the dependency rule (application/infrastructure may both depend inward on
 * domain; application may never depend on infrastructure). Pure {@code java.security}/{@code java.util}
 * — no Spring/JPA/Jackson imports — satisfies {@code domain_is_framework_free}.
 *
 * <p>Non-instantiable — static utility only.
 */
public final class GuestTokenHasher {

    private GuestTokenHasher() {}

    /**
     * Returns the lowercase hexadecimal SHA-256 digest of {@code input} encoded as UTF-8.
     *
     * @param input the raw token value; must not be null
     * @return 64-character hex string
     */
    public static String sha256Hex(String input) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java spec — this never happens
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
