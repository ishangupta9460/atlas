package com.atlas.backend.category;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException() { super("Category not found"); }
}
