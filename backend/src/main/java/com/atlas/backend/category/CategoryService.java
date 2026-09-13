package com.atlas.backend.category;

import com.atlas.backend.recurringintention.RecurringIntentionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns user-scoped Category CRUD and protects references from deletion. */
@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final RecurringIntentionRepository recurringIntentionRepository;

    public CategoryService(CategoryRepository categoryRepository, RecurringIntentionRepository recurringIntentionRepository) {
        this.categoryRepository = categoryRepository;
        this.recurringIntentionRepository = recurringIntentionRepository;
    }

    @Transactional
    public CategoryResponse create(Long userId, CreateCategoryRequest request) {
        return CategoryResponse.from(categoryRepository.save(Category.create(userId, request.name(), request.defaultFlexibilityTier(), request.color())));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId) {
        return categoryRepository.findAllByUserIdOrderByIdAsc(userId).stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long userId, Long id) { return CategoryResponse.from(findOwned(userId, id)); }

    @Transactional
    public CategoryResponse update(Long userId, Long id, UpdateCategoryRequest request) {
        Category category = findOwned(userId, id);
        category.update(request.name(), request.defaultFlexibilityTier(), request.color());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Category category = findOwned(userId, id);
        if (recurringIntentionRepository.existsByCategoryId(id)) throw new CategoryInUseException();
        categoryRepository.delete(category);
    }

    private Category findOwned(Long userId, Long id) {
        return categoryRepository.findByIdAndUserId(id, userId).orElseThrow(CategoryNotFoundException::new);
    }
}
