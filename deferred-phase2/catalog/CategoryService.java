package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.CategoryRequest;
import com.cassierq.api.catalog.dto.CategoryResponse;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.Category;
import com.cassierq.api.domain.repository.CategoryRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID storeId) {
        return categoryRepository.findByStoreIdOrderByNameAsc(storeId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(UUID storeId, CategoryRequest request) {
        Category category = categoryRepository.save(Category.builder()
                .store(storeRepository.getReferenceById(storeId))
                .name(request.name())
                .build());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(UUID storeId, UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> c.getStore().getId().equals(storeId))
                .orElseThrow(() -> new ResourceNotFoundException("Kategori tidak ditemukan"));
        categoryRepository.delete(category);
    }
}
