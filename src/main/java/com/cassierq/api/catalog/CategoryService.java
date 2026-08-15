package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.CategoryRequest;
import com.cassierq.api.catalog.dto.CategoryResponse;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.ProductCategory;
import com.cassierq.api.domain.repository.ProductCategoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ProductCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAll().stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByCategoryCodeIgnoreCase(request.categoryCode())) {
            throw new ConflictException("Kode kategori sudah dipakai");
        }

        ProductCategory parent = null;
        if (request.parentCategoryId() != null) {
            parent = categoryRepository.findById(request.parentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Kategori induk tidak ditemukan"));
        }

        ProductCategory saved = categoryRepository.save(ProductCategory.builder()
                .categoryCode(request.categoryCode())
                .categoryName(request.categoryName())
                .parentCategory(parent)
                .active(true)
                .build());

        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kategori tidak ditemukan"));

        if (!category.getCategoryCode().equalsIgnoreCase(request.categoryCode())
                && categoryRepository.existsByCategoryCodeIgnoreCase(request.categoryCode())) {
            throw new ConflictException("Kode kategori sudah dipakai");
        }

        ProductCategory parent = null;
        if (request.parentCategoryId() != null) {
            if (request.parentCategoryId().equals(id)) {
                throw new com.cassierq.api.common.exception.BadRequestException("Kategori tidak boleh jadi induk dirinya sendiri");
            }
            parent = categoryRepository.findById(request.parentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Kategori induk tidak ditemukan"));
        }

        category.setCategoryCode(request.categoryCode());
        category.setCategoryName(request.categoryName());
        category.setParentCategory(parent);
        categoryRepository.save(category);

        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(UUID id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Kategori tidak ditemukan");
        }
        // Products/child categories referencing this one will surface as a
        // 409 via GlobalExceptionHandler's DataIntegrityViolationException handler.
        categoryRepository.deleteById(id);
    }
}
