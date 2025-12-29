package org.rowtown.exception;

/**
 * Exception thrown when version control operations fail.
 */
public class VersionControlException extends RuntimeException {
    public VersionControlException(String message) {
        super(message);
    }

    public VersionControlException(String message, Throwable cause) {
        super(message, cause);
    }
}
