package com.atlas.backend.category;

import com.atlas.backend.user.User;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** API-surface-gap implementation for the flat Category entity. */
@RestController
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryService categoryService;
    public CategoryController(CategoryService categoryService) { this.categoryService = categoryService; }

    @PostMapping public ResponseEntity<CategoryResponse> create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(user.getId(), request));
    }
    @GetMapping public ResponseEntity<List<CategoryResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(categoryService.list(user.getId()));
    }
    @GetMapping("/{id}") public ResponseEntity<CategoryResponse> get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(categoryService.get(user.getId(), id));
    }
    @PatchMapping("/{id}") public ResponseEntity<CategoryResponse> update(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(user.getId(), id, request));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        categoryService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
