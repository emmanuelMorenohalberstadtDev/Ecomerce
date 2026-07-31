package com.ecommerce.catalog.application.usecase;

import com.ecommerce.catalog.application.port.AuditLogPort;
import com.ecommerce.catalog.application.port.CatalogAuditAction;
import com.ecommerce.catalog.application.usecase.CreateProductUseCase.CreateProductCommand;
import com.ecommerce.catalog.domain.exception.CategoryNotFoundException;
import com.ecommerce.catalog.domain.exception.DuplicateSkuException;
import com.ecommerce.catalog.domain.model.Category;
import com.ecommerce.catalog.domain.model.CategoryId;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.domain.model.Sku;
import com.ecommerce.catalog.domain.port.out.CategoryRepository;
import com.ecommerce.catalog.domain.port.out.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CreateProductUseCase}.
 *
 * <p>Mocks: {@link ProductRepository}, {@link CategoryRepository}, {@link AuditLogPort}.
 * No Spring context.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CreateProductUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuditLogPort auditLogPort;

    private CreateProductUseCase useCase;

    private final CategoryId categoryId = CategoryId.generate();
    private final Category category = Category.reconstitute(categoryId, null, "Electronics");

    @BeforeEach
    void setUp() {
        useCase = new CreateProductUseCase(productRepository, categoryRepository, auditLogPort);
    }

    private CreateProductCommand command() {
        return new CreateProductCommand(categoryId.toString(), "abc-123",
                new BigDecimal("19.99"), "USD", "Widget", "A widget", null);
    }

    // ── successful creation ────────────────────────────────────────────────────

    @Test
    void shouldSaveProduct_whenCategoryExistsAndSkuIsNew() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.existsBySku(any(Sku.class))).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = useCase.execute(command());

        assertThat(result.getSku()).isEqualTo(new Sku("abc-123"));
        assertThat(result.getCategoryId()).isEqualTo(categoryId);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void shouldWriteAuditLog_onSuccess() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.existsBySku(any(Sku.class))).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = useCase.execute(command());

        ArgumentCaptor<String> entityIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogPort).record(eq(CatalogAuditAction.PRODUCT_CREATED),
                eq("Product"), entityIdCaptor.capture(), any());
        assertThat(entityIdCaptor.getValue()).isEqualTo(result.getId().toString());
    }

    // ── category not found ────────────────────────────────────────────────────

    @Test
    void shouldThrowCategoryNotFoundException_whenCategoryDoesNotExist() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void shouldNotSaveOrAudit_whenCategoryDoesNotExist() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command())).isInstanceOf(CategoryNotFoundException.class);

        verify(productRepository, never()).save(any());
        verify(auditLogPort, never()).record(any(), any(), any(), any());
    }

    // ── duplicate SKU ──────────────────────────────────────────────────────────

    @Test
    void shouldThrowDuplicateSkuException_whenSkuAlreadyExists() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.existsBySku(any(Sku.class))).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(DuplicateSkuException.class);

        verify(productRepository, never()).save(any());
        verify(auditLogPort, never()).record(any(), any(), any(), any());
    }
}
