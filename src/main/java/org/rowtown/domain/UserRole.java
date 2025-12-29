package org.rowtown.domain;

/**
 * Enumeration of user roles in the system.
 */
public enum UserRole {
    /**
     * Regatta administrator - full access to all documents in their regatta.
     */
    REGATTA_ADMIN,

    /**
     * Timer - can create and modify own race results, read start lists.
     */
    TIMER,

    /**
     * Viewer - read-only access to permitted documents.
     */
    VIEWER
}
