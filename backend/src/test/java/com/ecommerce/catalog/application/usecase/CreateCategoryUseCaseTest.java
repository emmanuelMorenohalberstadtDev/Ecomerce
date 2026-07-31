package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.usecase.CreateCategoryUseCase.CreateCategoryCommand;
import com.ecommerce.catalog.domain.exception.CategoryNotFoundException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CreateCategoryUseCase}.
 *
 * <p>Mocks: {@link CategoryRepository}, {@link AuditLogPort}. No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CreateCategoryUseCaseTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuditLogPort auditLogPort;

    private CreateCategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateCategoryUseCase(categoryRepository, auditLogPort);
    }

    @Test
    void shouldCreateRootCategory_whenParentIdIsNull() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = useCase.execute(new CreateCategoryCommand("Electronics", null));

        assertThat(result.getName()).isEqualTo("Electronics");
        assertThat(result.isRoot()).isTrue();
    }

    @Test
    void shouldCreateChildCategory_whenParentExists() {
        CategoryId parentId = CategoryId.generate();
        when(categoryRepository.existsById(parentId)).thenReturn(true);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = useCase.execute(new CreateCategoryCommand("Laptops", parentId.toString()));

        assertThat(result.getParentId()).isEqualTo(parentId);
    }

    @Test
    void shouldWriteAuditLog_onSuccess() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new CreateCategoryCommand("Electronics", null));

        verify(auditLogPort).record(any(), any(), any(), any());
    }

    @Test
    void shouldThrowCategoryNotFoundException_whenParentDoesNotExist() {
        CategoryId parentId = CategoryId.generate();
        when(categoryRepository.existsById(parentId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new CreateCategoryCommand("Laptops", parentId.toString())))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(categoryRepository, never()).save(any());
        verify(auditLogPort, never()).record(any(), any(), any(), any());
    }
}
