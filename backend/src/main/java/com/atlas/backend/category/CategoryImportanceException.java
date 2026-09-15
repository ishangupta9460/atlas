package com.atlas.backend.category;
public class CategoryImportanceException extends RuntimeException {
    public CategoryImportanceException() { super("defaultImportance must be low, medium, high, critical, or null"); }
}
