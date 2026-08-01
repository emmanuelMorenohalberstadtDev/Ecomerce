package com.ecommerce.pricing.application.usecase;

import com.ecommerce.pricing.application.usecase.CalculateEffectivePriceUseCase.CalculatePriceCommand;
import com.ecommerce.pricing.application.usecase.CalculateEffectivePriceUseCase.PriceLineRequest;
import com.ecommerce.pricing.domain.exception.ProductNotAvailableException;
import com.ecommerce.pricing.domain.model.PriceBreakdown;
import com.ecommerce.pricing.domain.model.PriceCalculator;
import com.ecommerce.pricing.domain.model.PricedLine;
import com.ecommerce.pricing.domain.port.out.ProductCatalogPort;
import com.ecommerce.pricing.domain.port.out.ProductCatalogPort.PricedProductView;
import com.ecommerce.pricing.domain.port.out.PromotionPolicyPort;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CalculateEffectivePriceUseCase}.
 *
 * <p>Mocks at the ports it owns: {@link ProductCatalogPort}, {@link PromotionPolicyPort}. The
 * pure {@link PriceCalculator} collaborator is real (not mocked) — it has no I/O, so exercising
 * it directly here also verifies end-to-end composition without duplicating
 * {@code PriceCalculatorTest}'s own branch coverage.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CalculateEffectivePriceUseCaseTest {

    private static final Money SHIPPING_FEE = new Money(new BigDecimal("5.00"), "USD");

    @Mock
    private ProductCatalogPort productCatalogPort;

    @Mock
    private PromotionPolicyPort promotionPolicyPort;

    private CalculateEffectivePriceUseCase useCase;

    private final ProductId productId = ProductId.generate();

    private static Money usd(String amount) {
        return new Money(new BigDecimal(amount), "USD");
    }

    @BeforeEach
    void setUp() {
        useCase = new CalculateEffectivePriceUseCase(
                productCatalogPort, promotionPolicyPort, new PriceCalculator(), SHIPPING_FEE);
    }

    // ── happy path ─────────────────────────────────────────────────────────

    @Test
    void execute_singleLine_computesBreakdown() {
        when(productCatalogPort.findActiveById(productId))
                .thenReturn(Optional.of(new PricedProductView(productId, usd("20.00"))));
        when(promotionPolicyPort.discountForUnit(productId, usd("20.00")))
                .thenReturn(Money.zero("USD"));

        PriceBreakdown result = useCase.execute(
                new CalculatePriceCommand(List.of(new PriceLineRequest(productId, Quantity.of(2)))));

        assertThat(result.subtotal()).isEqualTo(usd("40.00"));
        assertThat(result.shippingFee()).isEqualTo(SHIPPING_FEE);
        assertThat(result.grandTotal().amount()).isEqualTo(usd("45.00"));
    }

    @Test
    void execute_multiLine_computesBreakdown() {
        ProductId productA = ProductId.generate();
        ProductId productB = ProductId.generate();
        when(productCatalogPort.findActiveById(productA))
                .thenReturn(Optional.of(new PricedProductView(productA, usd("10.00"))));
        when(productCatalogPort.findActiveById(productB))
                .thenReturn(Optional.of(new PricedProductView(productB, usd("15.00"))));
        when(promotionPolicyPort.discountForUnit(productA, usd("10.00"))).thenReturn(Money.zero("USD"));
        when(promotionPolicyPort.discountForUnit(productB, usd("15.00"))).thenReturn(Money.zero("USD"));

        PriceBreakdown result = useCase.execute(new CalculatePriceCommand(List.of(
                new PriceLineRequest(productA, Quantity.of(1)),
                new PriceLineRequest(productB, Quantity.of(2)))));

        assertThat(result.lines()).hasSize(2);
        // 10.00*1 + 15.00*2 = 10.00 + 30.00 = 40.00
        assertThat(result.subtotal()).isEqualTo(usd("40.00"));
        assertThat(result.grandTotal().amount()).isEqualTo(usd("45.00"));
    }

    @Test
    void execute_passesProductIdAndResolvedBasePrice_toPromotionPolicyPort() {
        when(productCatalogPort.findActiveById(productId))
                .thenReturn(Optional.of(new PricedProductView(productId, usd("30.00"))));
        when(promotionPolicyPort.discountForUnit(productId, usd("30.00")))
                .thenReturn(usd("5.00"));

        PriceBreakdown result = useCase.execute(
                new CalculatePriceCommand(List.of(new PriceLineRequest(productId, Quantity.of(1)))));

        verify(promotionPolicyPort).discountForUnit(productId, usd("30.00"));
        PricedLine line = result.lines().get(0);
        assertThat(line.unitDiscount()).isEqualTo(usd("5.00"));
        assertThat(line.unitFinalPrice()).isEqualTo(usd("25.00"));
    }

    @Test
    void execute_doesNotDedupeDuplicateProductIds_pricesLinesAsGiven() {
        when(productCatalogPort.findActiveById(productId))
                .thenReturn(Optional.of(new PricedProductView(productId, usd("10.00"))));
        when(promotionPolicyPort.discountForUnit(productId, usd("10.00")))
                .thenReturn(Money.zero("USD"));

        PriceBreakdown result = useCase.execute(new CalculatePriceCommand(List.of(
                new PriceLineRequest(productId, Quantity.of(1)),
                new PriceLineRequest(productId, Quantity.of(1)))));

        assertThat(result.lines()).hasSize(2);
        assertThat(result.subtotal()).isEqualTo(usd("20.00"));
    }

    // ── product not available ────────────────────────────────────────────

    @Test
    void execute_throwsProductNotAvailableException_whenProductIsNonexistent() {
        when(productCatalogPort.findActiveById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
                new CalculatePriceCommand(List.of(new PriceLineRequest(productId, Quantity.of(1))))))
                .isInstanceOf(ProductNotAvailableException.class);
    }

    @Test
    void execute_throwsProductNotAvailableException_whenProductIsRetired() {
        // ProductCatalogPort#findActiveById returns empty() for both RETIRED and nonexistent
        // products, by contract — same convention as catalog's ProductLookupPort. This test
        // documents that the use case does not (and cannot) distinguish the two cases.
        when(productCatalogPort.findActiveById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
                new CalculatePriceCommand(List.of(new PriceLineRequest(productId, Quantity.of(1))))))
                .isInstanceOf(ProductNotAvailableException.class);
    }

    @Test
    void execute_neverCallsPromotionPolicyPort_whenProductIsNotAvailable() {
        when(productCatalogPort.findActiveById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
                new CalculatePriceCommand(List.of(new PriceLineRequest(productId, Quantity.of(1))))))
                .isInstanceOf(ProductNotAvailableException.class);

        verify(promotionPolicyPort, never()).discountForUnit(any(ProductId.class), any(Money.class));
    }

    // ── currency mismatch defensive check ────────────────────────────────

    @Test
    void execute_throwsIllegalStateException_whenResolvedLineCurrencyDiffersFromShippingFeeCurrency() {
        Money eurBasePrice = new Money(new BigDecimal("10.00"), "EUR");
        when(productCatalogPort.findActiveById(productId))
                .thenReturn(Optional.of(new PricedProductView(productId, eurBasePrice)));
        when(promotionPolicyPort.discountForUnit(productId, eurBasePrice))
                .thenReturn(Money.zero("EUR"));

        // useCase was built with a USD shipping fee in setUp()
        assertThatThrownBy(() -> useCase.execute(
                new CalculatePriceCommand(List.of(new PriceLineRequest(productId, Quantity.of(1))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Currency mismatch");
    }

    // ── construction guards ───────────────────────────────────────────────

    @Test
    void execute_throwsNullPointerException_whenCommandIsNull() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void calculatePriceCommand_throwsIllegalArgumentException_whenLinesIsEmpty() {
        assertThatThrownBy(() -> new CalculatePriceCommand(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
