package com.ecommerce.payment.infrastructure.persistence;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-based assertion that payment's append-only write paths are insert-only, per
 * {@code V007__create_payment_schema.sql} §4 grants ("payment_attempts: INSERT and SELECT ONLY";
 * "refunds: INSERT and SELECT ONLY, same reasoning as payment_attempts") and
 * security-architecture.md §6c's insert-only enforcement pattern.
 *
 * <p>Deliberate duplicate of {@code order.infrastructure.persistence.AuditLogPortInsertOnlyTest}'s
 * reasoning: {@link SpringDataPaymentAttemptDao} and {@link SpringDataRefundDao} are
 * package-private by design, so this test lives alongside them to inspect them directly without
 * widening their visibility.
 *
 * <p>No Spring context — pure reflection over the two Spring Data DAOs.
 */
@Tag("unit")
class PaymentAttemptAndRefundDaoInsertOnlyTest {

    private static final List<String> FORBIDDEN_METHOD_NAME_FRAGMENTS =
            List.of("delete", "update", "remove");

    @Test
    void springDataPaymentAttemptDao_exposesNoUpdateOrDeleteMethod() {
        assertNoForbiddenMethods(SpringDataPaymentAttemptDao.class);
    }

    @Test
    void springDataPaymentAttemptDao_exposesOnlyTheTwoMethodsActuallyUsed() {
        // Declaring only "save" and the ordered lookup — no findById/deleteById/delete/deleteAll
        // from JpaRepository, because this interface deliberately extends the bare Spring Data
        // Repository<T, ID> marker instead of JpaRepository (payment_attempts is insert-only,
        // V007 §4).
        List<String> declaredNames = Arrays.stream(SpringDataPaymentAttemptDao.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertThat(declaredNames)
                .containsExactlyInAnyOrder("save", "findByPaymentIdOrderByAttemptedAtAsc");
    }

    @Test
    void springDataRefundDao_exposesNoUpdateOrDeleteMethod() {
        assertNoForbiddenMethods(SpringDataRefundDao.class);
    }

    @Test
    void springDataRefundDao_exposesOnlyTheTwoMethodsActuallyUsed() {
        // Same reasoning as SpringDataPaymentAttemptDao above — refunds is insert-only (V007 §4).
        List<String> declaredNames = Arrays.stream(SpringDataRefundDao.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertThat(declaredNames)
                .containsExactlyInAnyOrder("save", "findByPaymentIdOrderByIssuedAtAsc");
    }

    private static void assertNoForbiddenMethods(Class<?> type) {
        List<String> offendingMethods = Arrays.stream(type.getMethods())
                .map(Method::getName)
                .filter(name -> FORBIDDEN_METHOD_NAME_FRAGMENTS.stream()
                        .anyMatch(fragment -> name.toLowerCase().contains(fragment)))
                .toList();

        assertThat(offendingMethods)
                .as("no update/delete-like method should be exposed on %s", type.getSimpleName())
                .isEmpty();
    }
}
