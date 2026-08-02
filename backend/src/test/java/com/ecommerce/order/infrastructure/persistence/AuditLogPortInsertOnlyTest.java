package com.ecommerce.order.infrastructure.persistence;

import com.ecommerce.order.application.port.AuditLogPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-based assertion that order's append-only write paths are insert-only, per
 * security-architecture.md §6c requirement 1 ("the audit repository exposes insert and read
 * only; no update/delete methods exist") and domain-model.md rule 12 (order status history is
 * append-only).
 *
 * <p>Deliberate duplicate of catalog's {@code AuditLogPortInsertOnlyTest} — same reasoning:
 * {@link SpringDataOrderAuditLogDao} and {@link SpringDataOrderStatusHistoryDao} are
 * package-private by design, so this test lives alongside them to inspect them directly without
 * widening their visibility. Extended beyond catalog's original to also cover
 * {@link SpringDataOrderStatusHistoryDao}, which historically extended {@code JpaRepository} by
 * mistake — this test guards against regressing back to that.
 *
 * <p>No Spring context — pure reflection over the port interface and the Spring Data DAOs.
 */
@Tag("unit")
class AuditLogPortInsertOnlyTest {

    private static final List<String> FORBIDDEN_METHOD_NAME_FRAGMENTS =
            List.of("delete", "update", "remove");

    @Test
    void auditLogPort_exposesNoUpdateOrDeleteMethod() {
        assertNoForbiddenMethods(AuditLogPort.class);
    }

    @Test
    void auditLogPort_exposesExactlyOneMethod() {
        // "record" is the only write, and there is no read method either — order's use cases
        // never read the audit log back.
        assertThat(AuditLogPort.class.getDeclaredMethods()).hasSize(1);
        assertThat(AuditLogPort.class.getDeclaredMethods()[0].getName()).isEqualTo("record");
    }

    @Test
    void springDataOrderAuditLogDao_exposesNoUpdateOrDeleteMethod() {
        assertNoForbiddenMethods(SpringDataOrderAuditLogDao.class);
    }

    @Test
    void springDataOrderAuditLogDao_exposesExactlyOneDeclaredMethod() {
        // Declaring only "save" — no findById/deleteById/delete/deleteAll from JpaRepository,
        // because this interface deliberately extends the bare Spring Data Repository<T, ID>
        // marker instead of JpaRepository.
        Method[] declared = SpringDataOrderAuditLogDao.class.getDeclaredMethods();
        assertThat(declared).hasSize(1);
        assertThat(declared[0].getName()).isEqualTo("save");
    }

    @Test
    void springDataOrderStatusHistoryDao_exposesNoUpdateOrDeleteMethod() {
        assertNoForbiddenMethods(SpringDataOrderStatusHistoryDao.class);
    }

    @Test
    void springDataOrderStatusHistoryDao_exposesOnlyTheTwoMethodsActuallyUsed() {
        // "save" and the ordered lookup used by JpaOrderRepository — no findById/deleteById/
        // delete/deleteAll from JpaRepository, because this interface deliberately extends the
        // bare Spring Data Repository<T, ID> marker instead of JpaRepository (order_status_history
        // is append-only per security-architecture.md §6c and domain-model.md rule 12).
        List<String> declaredNames = Arrays.stream(SpringDataOrderStatusHistoryDao.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertThat(declaredNames)
                .containsExactlyInAnyOrder("save", "findByOrderIdOrderByOccurredAtAsc");
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
