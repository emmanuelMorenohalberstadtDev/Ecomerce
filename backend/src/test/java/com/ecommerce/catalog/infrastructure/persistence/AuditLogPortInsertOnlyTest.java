package com.ecommerce.catalog.infrastructure.persistence;

import com.ecommerce.catalog.application.port.AuditLogPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-based assertion that the admin audit log write path is insert-only, per
 * security-architecture.md §6c requirement 1 ("the audit repository exposes insert and read
 * only; no update/delete methods exist").
 *
 * <p>Placed in this package (rather than as an ArchUnit rule in the shared
 * {@code ArchitectureTest}) because {@link SpringDataAdminAuditLogDao} is package-private by
 * design (mirrors {@code SpringDataUserDao}'s encapsulation) — this test lives alongside it so
 * it can inspect it directly without widening its visibility.
 *
 * <p>No Spring context — pure reflection over the port interface and the Spring Data DAO.
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
        // "record" is the only write, and there is no read method either — catalog's use cases
        // never read the audit log back.
        assertThat(AuditLogPort.class.getDeclaredMethods()).hasSize(1);
        assertThat(AuditLogPort.class.getDeclaredMethods()[0].getName()).isEqualTo("record");
    }

    @Test
    void springDataAdminAuditLogDao_exposesNoUpdateOrDeleteMethod() {
        assertNoForbiddenMethods(SpringDataAdminAuditLogDao.class);
    }

    @Test
    void springDataAdminAuditLogDao_exposesExactlyOneDeclaredMethod() {
        // Declaring only "save" — no findById/deleteById/delete/deleteAll from JpaRepository,
        // because this interface deliberately extends the bare Spring Data Repository<T, ID>
        // marker instead of JpaRepository.
        Method[] declared = SpringDataAdminAuditLogDao.class.getDeclaredMethods();
        assertThat(declared).hasSize(1);
        assertThat(declared[0].getName()).isEqualTo("save");
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
