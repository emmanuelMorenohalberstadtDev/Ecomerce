package com.ecommerce.pricing.domain.model;

import com.ecommerce.pricing.domain.model.PriceCalculator.LineInput;
import com.ecommerce.shared.id.ProductId;
import com.ecommerce.shared.money.Money;
import com.ecommerce.shared.quantity.Quantity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PriceCalculator} — a pure domain service, zero mocks needed. Covers the
 * composition order (base → discount → line total → subtotal → shipping → grand total), the
 * penny-drift invariant (line totals must sum exactly to the subtotal), and rounding behaviour
 * per this codebase's actual {@link Money} (HALF_UP, scale-2, single rounding point).
 */
@Tag("unit")
class PriceCalculatorTest {

    private final PriceCalculator calculator = new PriceCalculator();

    private static Money usd(String amount) {
        return new Money(new BigDecimal(amount), "USD");
    }

    // ── argument validation ──────────────────────────────────────────────

    @Test
    void calculate_throwsNullPointerException_whenLinesIsNull() {
        assertThatThrownBy(() -> calculator.calculate(null, usd("5.00")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void calculate_throwsNullPointerException_whenShippingFeeIsNull() {
        LineInput line = new LineInput(ProductId.generate(), Quantity.of(1), usd("10.00"), Money.zero("USD"));

        assertThatThrownBy(() -> calculator.calculate(List.of(line), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void calculate_throwsIllegalArgumentException_whenLinesIsEmpty() {
        assertThatThrownBy(() -> calculator.calculate(List.of(), usd("5.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── single line, zero discount pass-through ──────────────────────────

    @Test
    void calculate_singleLine_zeroDiscount_passesBasePriceThrough() {
        ProductId productId = ProductId.generate();
        LineInput line = new LineInput(productId, Quantity.of(3), usd("10.00"), Money.zero("USD"));

        PriceBreakdown result = calculator.calculate(List.of(line), usd("5.00"));

        PricedLine pricedLine = result.lines().get(0);
        assertThat(pricedLine.unitBasePrice()).isEqualTo(usd("10.00"));
        assertThat(pricedLine.unitDiscount()).isEqualTo(usd("0.00"));
        assertThat(pricedLine.unitFinalPrice()).isEqualTo(usd("10.00"));
        assertThat(pricedLine.lineTotal()).isEqualTo(usd("30.00"));
    }

    // ── single line, with discount ────────────────────────────────────────

    @Test
    void calculate_singleLine_withDiscount_computesUnitFinalAndLineTotal() {
        LineInput line = new LineInput(ProductId.generate(), Quantity.of(2), usd("10.00"), usd("1.50"));

        PriceBreakdown result = calculator.calculate(List.of(line), usd("5.00"));

        PricedLine pricedLine = result.lines().get(0);
        assertThat(pricedLine.unitFinalPrice()).isEqualTo(usd("8.50"));
        assertThat(pricedLine.lineTotal()).isEqualTo(usd("17.00"));
        assertThat(result.discountTotal()).isEqualTo(usd("3.00"));
    }

    // ── shipping addition ─────────────────────────────────────────────────

    @Test
    void calculate_addsShippingFee_toGrandTotal_notToSubtotal() {
        LineInput line = new LineInput(ProductId.generate(), Quantity.of(1), usd("20.00"), Money.zero("USD"));

        PriceBreakdown result = calculator.calculate(List.of(line), usd("5.00"));

        assertThat(result.subtotal()).isEqualTo(usd("20.00"));
        assertThat(result.shippingFee()).isEqualTo(usd("5.00"));
        assertThat(result.grandTotal().amount()).isEqualTo(usd("25.00"));
    }

    // ── multi-line subtotal arithmetic / penny-drift invariant ───────────

    @Test
    void calculate_multiLine_subtotalEqualsExactSumOfLineTotals() {
        LineInput lineA = new LineInput(ProductId.generate(), Quantity.of(3), usd("33.33"), Money.zero("USD"));
        LineInput lineB = new LineInput(ProductId.generate(), Quantity.of(2), usd("14.99"), usd("1.00"));
        LineInput lineC = new LineInput(ProductId.generate(), Quantity.of(1), usd("0.01"), Money.zero("USD"));

        PriceBreakdown result = calculator.calculate(List.of(lineA, lineB, lineC), usd("5.00"));

        Money expectedSubtotal = result.lines().stream()
                .map(PricedLine::lineTotal)
                .reduce(Money.zero("USD"), Money::add);
        assertThat(result.subtotal()).isEqualTo(expectedSubtotal);
        // 33.33*3=99.99, (14.99-1.00)*2=27.98, 0.01*1=0.01 -> subtotal 127.98
        assertThat(result.subtotal()).isEqualTo(usd("127.98"));
        assertThat(result.grandTotal().amount()).isEqualTo(usd("132.98"));
    }

    @Test
    void calculate_multiLine_discountTotalEqualsExactSumOfLineDiscounts() {
        LineInput lineA = new LineInput(ProductId.generate(), Quantity.of(2), usd("10.00"), usd("1.00"));
        LineInput lineB = new LineInput(ProductId.generate(), Quantity.of(3), usd("5.00"), usd("0.50"));

        PriceBreakdown result = calculator.calculate(List.of(lineA, lineB), usd("5.00"));

        // (1.00*2) + (0.50*3) = 2.00 + 1.50 = 3.50
        assertThat(result.discountTotal()).isEqualTo(usd("3.50"));
    }

    @Test
    void calculate_preservesLineOrder() {
        ProductId first = ProductId.generate();
        ProductId second = ProductId.generate();
        LineInput lineA = new LineInput(first, Quantity.of(1), usd("10.00"), Money.zero("USD"));
        LineInput lineB = new LineInput(second, Quantity.of(1), usd("20.00"), Money.zero("USD"));

        PriceBreakdown result = calculator.calculate(List.of(lineA, lineB), usd("5.00"));

        assertThat(result.lines()).extracting(PricedLine::productId).containsExactly(first, second);
    }

    @Test
    void calculate_doesNotDedupeDuplicateProductIds_pricesEachLineIndependently() {
        ProductId productId = ProductId.generate();
        LineInput lineA = new LineInput(productId, Quantity.of(1), usd("10.00"), Money.zero("USD"));
        LineInput lineB = new LineInput(productId, Quantity.of(1), usd("10.00"), Money.zero("USD"));

        PriceBreakdown result = calculator.calculate(List.of(lineA, lineB), usd("5.00"));

        assertThat(result.lines()).hasSize(2);
        assertThat(result.subtotal()).isEqualTo(usd("20.00"));
    }

    // ── LineInput record invariants ───────────────────────────────────────

    @Test
    void lineInput_throwsNullPointerException_whenProductIdIsNull() {
        assertThatThrownBy(() -> new LineInput(null, Quantity.of(1), usd("10.00"), Money.zero("USD")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void lineInput_throwsNullPointerException_whenQuantityIsNull() {
        assertThatThrownBy(() -> new LineInput(ProductId.generate(), null, usd("10.00"), Money.zero("USD")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void lineInput_throwsNullPointerException_whenUnitBasePriceIsNull() {
        assertThatThrownBy(() -> new LineInput(ProductId.generate(), Quantity.of(1), null, Money.zero("USD")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void lineInput_throwsNullPointerException_whenUnitDiscountIsNull() {
        assertThatThrownBy(() -> new LineInput(ProductId.generate(), Quantity.of(1), usd("10.00"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
