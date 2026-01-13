package org.rowtown.rms.rrr.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Manifest describing the contents of a backup.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupManifest {
    private String backupId;
    private BackupType backupType;
    private LocalDateTime timestamp;
    private Long documentCount;
    private Long versionCount;
    private Long totalSize;
    private String lastBackupId; // For incremental backups
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BackupStatus status;

    public enum BackupStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
