package com.ecommerce.pricing.infrastructure.adapter;

import com.ecommerce.pricing.application.port.PriceCalculationPort.PriceLineRequest;
import com.ecommerce.pricing.application.port.PriceCalculationPort.PriceQuote;
import com.ecommerce.pricing.application.port.PriceCalculationPort.ProductUnavailableException;
import com.ecommerce.pricing.application.usecase.CalculateEffectivePriceUseCase;
import com.ecommerce.pricing.domain.exception.ProductNotAvailableException;
import com.ecommerce.pricing.domain.model.EffectivePrice;
import com.ecommerce.pricing.domain.model.PriceBreakdown;
import com.ecommerce.pricing.domain.model.PricedLine;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link PriceCalculationAdapter} — translates {@link CalculateEffectivePriceUseCase}'s
 * {@link PriceBreakdown} into this façade's {@link PriceQuote}, and pricing's own
 * {@link ProductNotAvailableException} into this façade's nested {@link ProductUnavailableException}.
 * Checkout is the first (and only) cross-context consumer of this façade, so its correctness is
 * covered directly here rather than only transitively through checkout's use-case tests.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PriceCalculationAdapterTest {

    @Mock
    private CalculateEffectivePriceUseCase calculateEffectivePriceUseCase;

    private PriceCalculationAdapter adapter;

    private final ProductId productId = ProductId.generate();

    @BeforeEach
    void setUp() {
        adapter = new PriceCalculationAdapter(calculateEffectivePriceUseCase);
    }

    @Test
    void calculate_translatesPriceBreakdownIntoPriceQuote() {
        Money unitBase = new Money(new BigDecimal("10.00"), "USD");
        Money zero = Money.zero("USD");
        Money lineTotal = new Money(new BigDecimal("20.00"), "USD");
        Money shipping = new Money(new BigDecimal("5.00"), "USD");
        Money grandTotal = new Money(new BigDecimal("25.00"), "USD");
        PricedLine line = new PricedLine(productId, Quantity.of(2), unitBase, zero, unitBase, lineTotal);
        PriceBreakdown breakdown = new PriceBreakdown(List.of(line), lineTotal, zero, shipping,
                new EffectivePrice(grandTotal));
        when(calculateEffectivePriceUseCase.execute(any())).thenReturn(breakdown);

        PriceQuote result = adapter.calculate(List.of(new PriceLineRequest(productId, Quantity.of(2))));

        assertThat(result.subtotal()).isEqualTo(lineTotal);
        assertThat(result.shippingFee()).isEqualTo(shipping);
        assertThat(result.grandTotal()).isEqualTo(grandTotal);
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).productId()).isEqualTo(productId);
        assertThat(result.lines().get(0).unitFinalPrice()).isEqualTo(unitBase);
        assertThat(result.lines().get(0).lineTotal()).isEqualTo(lineTotal);
    }

    @Test
    void calculate_throwsProductUnavailableException_whenUseCaseRejectsAProduct() {
        when(calculateEffectivePriceUseCase.execute(any()))
                .thenThrow(new ProductNotAvailableException("product retired"));

        assertThatThrownBy(() -> adapter.calculate(List.of(new PriceLineRequest(productId, Quantity.of(1)))))
                .isInstanceOf(ProductUnavailableException.class);
    }
}
