package org.rowtown.rms.rrr.backup;

/**
 * Types of backups supported by the system.
 */
public enum BackupType {
    /**
     * Full backup - all documents and versions.
     */
    FULL,

    /**
     * Incremental backup - only changes since last backup.
     */
    INCREMENTAL
}
