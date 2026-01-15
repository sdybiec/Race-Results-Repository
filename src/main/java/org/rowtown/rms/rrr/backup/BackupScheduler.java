package org.rowtown.rms.rrr.backup;

import org.rowtown.rms.rrr.service.BackupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler for automated backups.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackupScheduler {

    private final BackupService backupService;

    /**
     * Full backup - Daily at 2:00 AM
     * Cron: 0 0 2 * * * (second minute hour day month weekday)
     */
    @Scheduled(cron = "${backup.schedule.full}")
    public void scheduledFullBackup() {
        log.info("Starting scheduled full backup");
        try {
            BackupManifest manifest = backupService.createFullBackup();
            log.info("Scheduled full backup completed: {}", manifest.getBackupId());
        } catch (Exception e) {
            log.error("Scheduled full backup failed", e);
        }
    }

    /**
     * Incremental backup - Every 4 hours
     */
    @Scheduled(cron = "${backup.schedule.incremental}")
    public void scheduledIncrementalBackup() {
        log.info("Starting scheduled incremental backup");
        try {
            BackupManifest manifest = backupService.createIncrementalBackup();
            log.info("Scheduled incremental backup completed: {}", manifest.getBackupId());
        } catch (Exception e) {
            log.error("Scheduled incremental backup failed", e);
        }
    }

    /**
     * Cleanup old backups - Daily at 3:00 AM
     * Cron: 0 0 3 * * * (second minute hour day month weekday)
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledCleanup() {
        log.info("Starting scheduled backup cleanup");
        try {
            int deletedCount = backupService.cleanupOldBackups();
            log.info("Cleanup completed: {} old backups deleted", deletedCount);
        } catch (Exception e) {
            log.error("Scheduled cleanup failed", e);
        }
    }
}
