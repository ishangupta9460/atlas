package com.atlas.backend.task;

/** Raised when a requested walking-skeleton transition is not valid. */
public class InvalidTaskStateException extends RuntimeException {

    public InvalidTaskStateException(String message) {
        super(message);
    }
}
