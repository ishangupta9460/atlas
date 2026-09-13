package com.atlas.backend.category;

public class CategoryInUseException extends RuntimeException {
    public CategoryInUseException() { super("Category cannot be deleted while recurring intentions reference it"); }
}
