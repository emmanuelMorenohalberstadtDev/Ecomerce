package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.usecase.DeleteCategoryUseCase.DeleteCategoryCommand;
import com.ecommerce.catalog.domain.exception.CategoryNotEmptyException;
import com.ecommerce.catalog.domain.exception.CategoryNotFoundException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeleteCategoryUseCase}.
 *
 * <p>Mocks: {@link CategoryRepository}, {@link ProductRepository}, {@link AuditLogPort}.
 * No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DeleteCategoryUseCaseTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuditLogPort auditLogPort;

    private DeleteCategoryUseCase useCase;
    private Category category;

    @BeforeEach
    void setUp() {
        useCase = new DeleteCategoryUseCase(categoryRepository, productRepository, auditLogPort);
        category = Category.create("Electronics", null);
    }

    @Test
    void shouldDeleteCategory_whenEmpty() {
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.hasChildren(category.getId())).thenReturn(false);
        when(productRepository.existsByCategoryId(category.getId())).thenReturn(false);

        useCase.execute(new DeleteCategoryCommand(category.getId().toString()));

        verify(categoryRepository).delete(category);
    }

    @Test
    void shouldWriteAuditLog_onSuccess() {
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.hasChildren(category.getId())).thenReturn(false);
        when(productRepository.existsByCategoryId(category.getId())).thenReturn(false);

        useCase.execute(new DeleteCategoryCommand(category.getId().toString()));

        verify(auditLogPort).record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldThrowCategoryNotFoundException_whenCategoryDoesNotExist() {
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new DeleteCategoryCommand(category.getId().toString())))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void shouldThrowCategoryNotEmptyException_whenCategoryHasChildren() {
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.hasChildren(category.getId())).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(new DeleteCategoryCommand(category.getId().toString())))
                .isInstanceOf(CategoryNotEmptyException.class);

        verify(categoryRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldThrowCategoryNotEmptyException_whenCategoryHasProducts() {
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(categoryRepository.hasChildren(category.getId())).thenReturn(false);
        when(productRepository.existsByCategoryId(category.getId())).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(new DeleteCategoryCommand(category.getId().toString())))
                .isInstanceOf(CategoryNotEmptyException.class);

        verify(categoryRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
