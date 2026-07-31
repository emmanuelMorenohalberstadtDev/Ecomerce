package com.ecommerce.catalog.presentation;

import com.ecommerce.catalog.application.usecase.ChangeProductPriceUseCase;
import com.ecommerce.catalog.application.usecase.ChangeProductPriceUseCase.ChangeProductPriceCommand;
import com.ecommerce.catalog.application.usecase.CreateProductUseCase;
import com.ecommerce.catalog.application.usecase.CreateProductUseCase.CreateProductCommand;
import com.ecommerce.catalog.application.usecase.ProductImageInput;
import com.ecommerce.catalog.application.usecase.RetireProductUseCase;
import com.ecommerce.catalog.application.usecase.RetireProductUseCase.RetireProductCommand;
import com.ecommerce.catalog.application.usecase.UpdateProductUseCase;
import com.ecommerce.catalog.application.usecase.UpdateProductUseCase.UpdateProductCommand;
import com.ecommerce.catalog.domain.model.Product;
import com.ecommerce.catalog.presentation.dto.ChangeProductPriceRequest;
import com.ecommerce.catalog.presentation.dto.CreateProductRequest;
import com.ecommerce.catalog.presentation.dto.MoneyDto;
import com.ecommerce.catalog.presentation.dto.ProductImageDto;
import com.ecommerce.catalog.presentation.dto.ProductResponse;
import com.ecommerce.catalog.presentation.dto.UpdateProductRequest;
import com.ecommerce.catalog.presentation.mapper.ProductMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD/mutations for products: {@code /api/v1/admin/products/**}. The route matcher in
 * {@code AuthSecurityConfiguration} already requires {@code ADMIN} at the coarse layer for
 * everything under {@code /api/v1/admin/**}; each underlying use case additionally carries
 * {@code @PreAuthorize("hasRole('ADMIN')")} as the fine-grained second layer
 * (security-architecture §3.2). Every mutation writes one row to the admin audit log
 * (security §6c) via {@code AuditLogPort}, in the same transaction as the mutation.
 *
 * <p>Auth decision table — identical for every endpoint in this controller (no ownership
 * dimension; admin ops are role-gated only, security-architecture §3.4):
 * <table>
 *   <caption>Auth decision table for this controller</caption>
 *   <tr><th>Endpoint</th><th>GUEST</th><th>CUSTOMER</th><th>ADMIN</th></tr>
 *   <tr><td>POST /admin/products</td><td>401</td><td>403</td><td>201</td></tr>
 *   <tr><td>PUT /admin/products/{id}</td><td>401</td><td>403</td><td>200</td></tr>
 *   <tr><td>PUT /admin/products/{id}/price</td><td>401</td><td>403</td><td>200</td></tr>
 *   <tr><td>POST /admin/products/{id}/retirement</td><td>401</td><td>403</td><td>200</td></tr>
 * </table>
 *
 * <p>The price sub-resource ({@code PUT .../price}) and the retirement sub-resource
 * ({@code POST .../retirement}) follow api-guidelines §1.3/§1.8: retirement is modeled as a
 * created sub-resource (a state transition, like order cancellation) via POST; price is modeled
 * as a replaced sub-resource via PUT (idempotent full-replace of the current price value,
 * not a one-way transition) — both keep their own request record and problem-type surface
 * separate from the general {@link UpdateProductRequest} update.
 */
@RestController
@RequestMapping("/api/v1/admin/products")
@Validated
public class AdminProductController {

    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final ChangeProductPriceUseCase changeProductPriceUseCase;
    private final RetireProductUseCase retireProductUseCase;

    public AdminProductController(CreateProductUseCase createProductUseCase,
                                  UpdateProductUseCase updateProductUseCase,
                                  ChangeProductPriceUseCase changeProductPriceUseCase,
                                  RetireProductUseCase retireProductUseCase) {
        this.createProductUseCase = createProductUseCase;
        this.updateProductUseCase = updateProductUseCase;
        this.changeProductPriceUseCase = changeProductPriceUseCase;
        this.retireProductUseCase = retireProductUseCase;
    }

    /**
     * @return 201 Created, {@code Location} pointing at the public read endpoint
     *         ({@code GET /api/v1/products/{id}} — there is no admin-specific read endpoint in
     *         v1) since a newly created product is always {@code ACTIVE} and therefore visible
     *         there immediately.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProductResponse> create(@RequestBody @Valid CreateProductRequest request) {
        Product product = createProductUseCase.execute(new CreateProductCommand(
                request.categoryId(), request.sku(), toAmount(request.basePrice()),
                request.basePrice().currency(), request.name(), request.description(),
                toImageInputs(request.images())));

        return ResponseEntity.created(URI.create("/api/v1/products/" + product.getId()))
                .body(ProductMapper.toResponse(product));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @RequestBody @Valid UpdateProductRequest request) {
        Product product = updateProductUseCase.execute(new UpdateProductCommand(
                id.toString(), request.categoryId(), request.name(), request.description(),
                toImageInputs(request.images())));
        return ProductMapper.toResponse(product);
    }

    @PutMapping("/{id}/price")
    public ProductResponse changePrice(@PathVariable UUID id,
                                       @RequestBody @Valid ChangeProductPriceRequest request) {
        Product product = changeProductPriceUseCase.execute(new ChangeProductPriceCommand(
                id.toString(), toAmount(request.basePrice()), request.basePrice().currency()));
        return ProductMapper.toResponse(product);
    }

    /**
     * @return 200 with the updated (now {@code RETIRED}) product representation — chosen over a
     *         bare 204 because the admin UI benefits from an immediate confirmation of the new
     *         status without a follow-up GET (api-guidelines §2 decision table: "mutation
     *         succeeded, useful body ... 200").
     */
    @PostMapping("/{id}/retirement")
    public ProductResponse retire(@PathVariable UUID id) {
        Product product = retireProductUseCase.execute(new RetireProductCommand(id.toString()));
        return ProductMapper.toResponse(product);
    }

    private static BigDecimal toAmount(MoneyDto money) {
        return new BigDecimal(money.amount());
    }

    private static List<ProductImageInput> toImageInputs(List<ProductImageDto> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream().map(i -> new ProductImageInput(i.imageUrl(), i.displayOrder())).toList();
    }
}
