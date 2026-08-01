package com.ecommerce.cart.domain.port.out;

/**
 * Outbound port for minting a brand-new anonymous guest cart identity.
 *
 * <p>Implemented by {@code cart.infrastructure.security.GuestCartTokenGenerator} using
 * {@code SecureRandom} (security-architecture §2.7: guest cart tokens are ≥128-bit
 * {@code SecureRandom}, never a UUIDv7 — bearer secrets require full entropy). Living behind a
 * port keeps the application layer ({@code GetOrCreateCartUseCase}) free of the concrete crypto
 * mechanism, per the hexagonal skill ("port signatures use domain types only").
 */
public interface GuestCartTokenIssuer {

    IssuedGuestToken issue();

    /**
     * A freshly-minted guest identity.
     *
     * @param rawToken  the raw, high-entropy token — delivered to the client as a cookie value
     *                  and NEVER persisted; travels only as far as the presentation layer that
     *                  sets the {@code Set-Cookie} header
     * @param tokenHash the SHA-256 hash of {@code rawToken} — the only form the domain/
     *                  application layers store or compare ({@link com.ecommerce.cart.domain.model.Cart#getGuestTokenHash()})
     */
    record IssuedGuestToken(String rawToken, String tokenHash) {

        public IssuedGuestToken {
            if (rawToken == null || rawToken.isBlank()) {
                throw new IllegalArgumentException("rawToken must not be null or blank");
            }
            if (tokenHash == null || tokenHash.isBlank()) {
                throw new IllegalArgumentException("tokenHash must not be null or blank");
            }
        }
    }
}
