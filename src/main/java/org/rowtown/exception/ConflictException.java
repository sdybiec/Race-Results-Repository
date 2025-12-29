package org.rowtown.exception;

/**
 * Exception thrown when there is a conflict in document updates.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
