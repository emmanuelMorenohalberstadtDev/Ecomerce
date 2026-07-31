package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.usecase.UpdateCategoryUseCase.UpdateCategoryCommand;
import com.ecommerce.catalog.domain.exception.CategoryCycleException;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UpdateCategoryUseCase}.
 *
 * <p>Mocks: {@link CategoryRepository}, {@link AuditLogPort}. No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UpdateCategoryUseCaseTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuditLogPort auditLogPort;

    private UpdateCategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateCategoryUseCase(categoryRepository, auditLogPort);
    }

    @Test
    void shouldUpdateNameAndParent_whenBothExistAndNoCycle() {
        Category category = Category.create("Electronics", null);
        CategoryId newParentId = CategoryId.generate();
        Category newParent = Category.reconstitute(newParentId, null, "Home");
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.existsById(newParentId)).thenReturn(true);
        when(categoryRepository.findById(newParentId)).thenReturn(Optional.of(newParent));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = useCase.execute(new UpdateCategoryCommand(
                category.getId().toString(), "Consumer Electronics", newParentId.toString()));

        assertThat(result.getName()).isEqualTo("Consumer Electronics");
        assertThat(result.getParentId()).isEqualTo(newParentId);
    }

    @Test
    void shouldWriteAuditLog_onSuccess() {
        Category category = Category.create("Electronics", null);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new UpdateCategoryCommand(category.getId().toString(), "New Name", null));

        verify(auditLogPort).record(any(), any(), any(), any());
    }

    @Test
    void shouldThrowCategoryNotFoundException_whenCategoryDoesNotExist() {
        CategoryId id = CategoryId.generate();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new UpdateCategoryCommand(id.toString(), "New Name", null)))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void shouldThrowCategoryNotFoundException_whenNewParentDoesNotExist() {
        Category category = Category.create("Electronics", null);
        CategoryId missingParent = CategoryId.generate();
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.existsById(missingParent)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new UpdateCategoryCommand(
                category.getId().toString(), "New Name", missingParent.toString())))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void shouldThrowCategoryCycleException_whenReParentingUnderOwnChild() {
        Category parent = Category.create("Electronics", null);
        Category child = Category.create("Laptops", parent.getId());
        when(categoryRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(categoryRepository.existsById(child.getId())).thenReturn(true);
        // Walking up from child.getId(): child's parent is parent.getId() == the category being updated
        when(categoryRepository.findById(child.getId())).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> useCase.execute(new UpdateCategoryCommand(
                parent.getId().toString(), "Electronics", child.getId().toString())))
                .isInstanceOf(CategoryCycleException.class);

        verify(categoryRepository, never()).save(any());
    }
}
