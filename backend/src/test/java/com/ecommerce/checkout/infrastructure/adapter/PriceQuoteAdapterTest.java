package com.ecommerce.checkout.infrastructure.adapter;

import com.ecommerce.checkout.domain.exception.ProductUnavailableForCheckoutException;
import com.ecommerce.checkout.domain.model.RecalculatedTotal;
import com.ecommerce.checkout.domain.port.out.PriceQuotePort.PriceQuoteLine;
import com.ecommerce.pricing.application.port.PriceCalculationPort;
import com.ecommerce.pricing.application.port.PriceCalculationPort.PriceQuote;
import com.ecommerce.pricing.application.port.PriceCalculationPort.PricedLineView;
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
 * Unit test for {@link PriceQuoteAdapter} — translates pricing's {@link PriceQuote} into
 * checkout's own {@link RecalculatedTotal}, and pricing's façade-owned
 * {@link PriceCalculationPort.ProductUnavailableException} into checkout's own
 * {@link ProductUnavailableForCheckoutException}.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PriceQuoteAdapterTest {

    @Mock
    private PriceCalculationPort priceCalculationPort;

    private PriceQuoteAdapter adapter;

    private final ProductId productId = ProductId.generate();

    @BeforeEach
    void setUp() {
        adapter = new PriceQuoteAdapter(priceCalculationPort);
    }

    @Test
    void recalculate_translatesPriceQuoteIntoRecalculatedTotal() {
        Money unitPrice = new Money(new BigDecimal("10.00"), "USD");
        Money subtotal = new Money(new BigDecimal("20.00"), "USD");
        Money shipping = new Money(new BigDecimal("5.00"), "USD");
        Money grandTotal = new Money(new BigDecimal("25.00"), "USD");
        PriceQuote quote = new PriceQuote(
                List.of(new PricedLineView(productId, Quantity.of(2), unitPrice, subtotal)),
                subtotal, Money.zero("USD"), shipping, grandTotal);
        when(priceCalculationPort.calculate(any())).thenReturn(quote);

        RecalculatedTotal result = adapter.recalculate(List.of(new PriceQuoteLine(productId, Quantity.of(2))));

        assertThat(result.subtotal()).isEqualTo(subtotal);
        assertThat(result.shippingFee()).isEqualTo(shipping);
        assertThat(result.grandTotal()).isEqualTo(grandTotal);
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).productId()).isEqualTo(productId);
        assertThat(result.lines().get(0).quantity()).isEqualTo(Quantity.of(2));
        assertThat(result.lines().get(0).unitPrice()).isEqualTo(unitPrice);
        assertThat(result.lines().get(0).lineTotal()).isEqualTo(subtotal);
    }

    @Test
    void recalculate_throwsProductUnavailableForCheckoutException_whenPriceCalculationPortRejectsAProduct() {
        when(priceCalculationPort.calculate(any()))
                .thenThrow(new PriceCalculationPort.ProductUnavailableException("product retired"));

        assertThatThrownBy(() -> adapter.recalculate(List.of(new PriceQuoteLine(productId, Quantity.of(1)))))
                .isInstanceOf(ProductUnavailableForCheckoutException.class);
    }
}
