package org.rowtown.rms.rrr.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.rowtown.rms.rrr.backup.BackupManifest;
import org.rowtown.rms.rrr.domain.UserRole;
import org.rowtown.rms.rrr.service.AuthorizationService;
import org.rowtown.rms.rrr.service.BackupService;
import org.rowtown.rms.rrr.service.RestoreService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for backup and restore operations.
 */
@RestController
@RequestMapping("/api/v1/backups")
@RequiredArgsConstructor
@Tag(name = "Backups", description = "Backup and restore API")
public class BackupController {

    private final BackupService backupService;
    private final RestoreService restoreService;
    private final AuthorizationService authorizationService;

    @PostMapping("/full")
    @Operation(summary = "Create full backup", description = "Creates a full backup of the repository")
    public ResponseEntity<BackupManifest> createFullBackup() {
        // Only regatta admin can create backups
        if (!authorizationService.hasRole(UserRole.REGATTA_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        BackupManifest manifest = backupService.createFullBackup();
        return new ResponseEntity<>(manifest, HttpStatus.CREATED);
    }

    @PostMapping("/incremental")
    @Operation(summary = "Create incremental backup", description = "Creates an incremental backup")
    public ResponseEntity<BackupManifest> createIncrementalBackup() {
        // Only regatta admin can create backups
        if (!authorizationService.hasRole(UserRole.REGATTA_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        BackupManifest manifest = backupService.createIncrementalBackup();
        return new ResponseEntity<>(manifest, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List backups", description = "Lists all available backups")
    public ResponseEntity<List<BackupManifest>> listBackups() {
        // Only regatta admin can view backups
        if (!authorizationService.hasRole(UserRole.REGATTA_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<BackupManifest> backups = backupService.listBackups();
        return ResponseEntity.ok(backups);
    }

    @PostMapping("/{backupId}/restore/full")
    @Operation(summary = "Restore full backup", description = "Restores repository from a backup (replaces all data)")
    public ResponseEntity<Void> restoreFull(@PathVariable String backupId) {
        // Only regatta admin can restore
        if (!authorizationService.hasRole(UserRole.REGATTA_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        restoreService.restoreFull(backupId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{backupId}/restore/selective")
    @Operation(summary = "Restore selective documents", description = "Restores specific documents from a backup")
    public ResponseEntity<Void> restoreSelective(
            @PathVariable String backupId,
            @RequestBody List<Long> documentIds) {
        // Only regatta admin can restore
        if (!authorizationService.hasRole(UserRole.REGATTA_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        restoreService.restoreSelective(backupId, documentIds);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/restore/point-in-time")
    @Operation(summary = "Point-in-time restore", description = "Restores repository to a specific point in time")
    public ResponseEntity<Void> restorePointInTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime targetTime) {
        // Only regatta admin can restore
        if (!authorizationService.hasRole(UserRole.REGATTA_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        restoreService.restorePointInTime(targetTime);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{backupId}")
    @Operation(summary = "Delete backup", description = "Deletes a specific backup")
    public ResponseEntity<Void> deleteBackup(@PathVariable String backupId) {
        // Only regatta admin can delete backups
        if (!authorizationService.hasRole(UserRole.REGATTA_ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            backupService.deleteBackup(backupId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
