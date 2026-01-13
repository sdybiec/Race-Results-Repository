package org.rowtown.rms.rrr.client.model;

/**
 * Synchronization status for local entities.
 */
public enum SyncStatus {
    /**
     * Entity is synchronized with server.
     */
    SYNCED,

    /**
     * Entity has local changes pending upload.
     */
    PENDING,

    /**
     * Entity is currently being synchronized.
     */
    SYNCING,

    /**
     * Synchronization failed, needs retry.
     */
    FAILED,

    /**
     * Conflict detected, needs resolution.
     */
    CONFLICT
}
