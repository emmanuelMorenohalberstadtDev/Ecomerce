package com.ecommerce.checkout.application.usecase;

import com.ecommerce.checkout.domain.exception.CheckoutSessionNotFoundException;
import com.ecommerce.checkout.domain.model.CheckoutSession;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.checkout.domain.port.out.CheckoutSessionRepository;
import com.ecommerce.shared.id.CartId;
import com.ecommerce.shared.id.CheckoutSessionId;
import com.ecommerce.shared.id.CustomerId;
import com.ecommerce.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetCheckoutSessionUseCase} — ownership-scoped read, mirroring
 * {@code cart.application.usecase.ViewCartUseCaseTest}'s "not found" style for the equivalent
 * single-resource read use case.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class GetCheckoutSessionUseCaseTest {

    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    private GetCheckoutSessionUseCase useCase;

    private final CustomerId customerId = CustomerId.generate();
    private final CheckoutSessionId sessionId = CheckoutSessionId.generate();

    @BeforeEach
    void setUp() {
        useCase = new GetCheckoutSessionUseCase(checkoutSessionRepository);
    }

    private CheckoutSession session() {
        Money money = new Money(new BigDecimal("50.00"), "USD");
        RecalculatedTotal total = new RecalculatedTotal(List.of(), money, Money.zero("USD"), Money.zero("USD"), money);
        return CheckoutSession.start(sessionId, customerId, CartId.generate(), total, money, "idem-key-1",
                Instant.parse("2026-07-31T10:00:00Z"));
    }

    @Test
    void shouldReturnSession_whenOwnedByCaller() {
        when(checkoutSessionRepository.findByIdAndCustomerId(sessionId, customerId))
                .thenReturn(Optional.of(session()));

        CheckoutSession result = useCase.execute(sessionId, customerId);

        assertThat(result.getId()).isEqualTo(sessionId);
    }

    @Test
    void shouldThrowCheckoutSessionNotFoundException_whenSessionDoesNotExist() {
        when(checkoutSessionRepository.findByIdAndCustomerId(sessionId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(sessionId, customerId))
                .isInstanceOf(CheckoutSessionNotFoundException.class);
    }

    @Test
    void shouldThrowCheckoutSessionNotFoundException_whenSessionBelongsToAnotherCustomer() {
        CustomerId anotherCustomer = CustomerId.generate();
        // The ownership-scoped query itself returns empty for a mismatched owner (security §3.2) —
        // simulated here by stubbing exactly that lookup to return empty.
        when(checkoutSessionRepository.findByIdAndCustomerId(sessionId, anotherCustomer)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(sessionId, anotherCustomer))
                .isInstanceOf(CheckoutSessionNotFoundException.class);
    }
}
