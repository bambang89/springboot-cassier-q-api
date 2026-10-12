package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.CategoryRequest;
import com.cassierq.api.catalog.dto.CategoryResponse;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.domain.entity.ProductCategory;
import com.cassierq.api.domain.repository.ProductCategoryRepository;
import com.cassierq.api.security.AppUserPrincipal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final CatalogScope catalogScope;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(AppUserPrincipal principal) {
        UUID owner = catalogScope.ownerOf(principal);
        return categoryRepository.findByOwnerStoreId(owner).stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(AppUserPrincipal principal, CategoryRequest request) {
        UUID owner = catalogScope.requireWriter(principal);
        if (categoryRepository.existsByOwnerStoreIdAndCategoryCodeIgnoreCase(owner, request.categoryCode())) {
            throw new ConflictException("Kode kategori sudah dipakai");
        }

        ProductCategory parent = null;
        if (request.parentCategoryId() != null) {
            parent = catalogScope.category(request.parentCategoryId(), owner);
        }

        ProductCategory saved = categoryRepository.save(ProductCategory.builder()
                .categoryCode(request.categoryCode())
                .categoryName(request.categoryName())
                .ownerStoreId(owner)
                .parentCategory(parent)
                .active(true)
                .build());

        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(AppUserPrincipal principal, UUID id, CategoryRequest request) {
        UUID owner = catalogScope.requireWriter(principal);
        ProductCategory category = catalogScope.category(id, owner);

        if (!category.getCategoryCode().equalsIgnoreCase(request.categoryCode())
                && categoryRepository.existsByOwnerStoreIdAndCategoryCodeIgnoreCase(owner, request.categoryCode())) {
            throw new ConflictException("Kode kategori sudah dipakai");
        }

        ProductCategory parent = null;
        if (request.parentCategoryId() != null) {
            if (request.parentCategoryId().equals(id)) {
                throw new BadRequestException("Kategori tidak boleh jadi induk dirinya sendiri");
            }
            parent = catalogScope.category(request.parentCategoryId(), owner);
        }

        category.setCategoryCode(request.categoryCode());
        category.setCategoryName(request.categoryName());
        category.setParentCategory(parent);
        categoryRepository.save(category);

        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(AppUserPrincipal principal, UUID id) {
        UUID owner = catalogScope.requireWriter(principal);
        // Products/child categories referencing this one will surface as a
        // 409 via GlobalExceptionHandler's DataIntegrityViolationException handler.
        categoryRepository.delete(catalogScope.category(id, owner));
    }
}
