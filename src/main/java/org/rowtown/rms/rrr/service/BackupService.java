package org.rowtown.rms.rrr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.backup.BackupManifest;
import org.rowtown.rms.rrr.backup.BackupType;
import org.rowtown.rms.rrr.domain.entity.Document;
import org.rowtown.rms.rrr.domain.entity.Version;
import org.rowtown.rms.rrr.repository.DocumentRepository;
import org.rowtown.rms.rrr.repository.VersionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * Service for creating and managing backups of the repository.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final DocumentRepository documentRepository;
    private final VersionRepository versionRepository;

    @Value("${backup.directory}")
    private String backupDirectory;

    @Value("${backup.retention.days:30}")
    private int retentionDays;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    /**
     * Create a full backup of the repository.
     */
    @Transactional(readOnly = true)
    public BackupManifest createFullBackup() {
        String backupId = generateBackupId(BackupType.FULL);
        LocalDateTime startTime = LocalDateTime.now();

        log.info("Starting full backup: {}", backupId);

        try {
            // Create backup directory
            Path backupPath = createBackupDirectory(backupId, BackupType.FULL);

            // Get all documents and versions
            List<Document> documents = documentRepository.findAll();
            List<Version> versions = new ArrayList<>();
            for (Document doc : documents) {
                versions.addAll(versionRepository.findByDocument_DocumentIdOrderByVersionNumberDesc(
                    doc.getDocumentId()));
            }

            // Write documents
            writeDocumentsToFile(backupPath, documents);

            // Write versions
            writeVersionsToFile(backupPath, versions);

            // Calculate total size
            long totalSize = calculateBackupSize(backupPath);

            // Create manifest
            BackupManifest manifest = BackupManifest.builder()
                .backupId(backupId)
                .backupType(BackupType.FULL)
                .timestamp(startTime)
                .documentCount((long) documents.size())
                .versionCount((long) versions.size())
                .totalSize(totalSize)
                .startTime(startTime)
                .endTime(LocalDateTime.now())
                .status(BackupManifest.BackupStatus.COMPLETED)
                .build();

            // Write manifest
            writeManifest(backupPath, manifest);

            log.info("Full backup completed: {} ({} documents, {} versions, {} bytes)",
                backupId, documents.size(), versions.size(), totalSize);

            return manifest;

        } catch (Exception e) {
            log.error("Full backup failed: {}", backupId, e);
            throw new RuntimeException("Backup failed", e);
        }
    }

    /**
     * Create an incremental backup (changes since last backup).
     */
    @Transactional(readOnly = true)
    public BackupManifest createIncrementalBackup() {
        String backupId = generateBackupId(BackupType.INCREMENTAL);
        LocalDateTime startTime = LocalDateTime.now();

        log.info("Starting incremental backup: {}", backupId);

        try {
            // Find last backup timestamp
            LocalDateTime lastBackupTime = getLastBackupTime();
            if (lastBackupTime == null) {
                log.warn("No previous backup found, creating full backup instead");
                return createFullBackup();
            }

            // Create backup directory
            Path backupPath = createBackupDirectory(backupId, BackupType.INCREMENTAL);

            // Get documents modified since last backup
            List<Document> documents = documentRepository.findAll().stream()
                .filter(doc -> doc.getCreatedAt().isAfter(lastBackupTime))
                .collect(Collectors.toList());

            // Get versions created since last backup
            List<Version> versions = versionRepository.findAll().stream()
                .filter(v -> v.getTimestamp().isAfter(lastBackupTime))
                .collect(Collectors.toList());

            // Write documents and versions
            writeDocumentsToFile(backupPath, documents);
            writeVersionsToFile(backupPath, versions);

            // Calculate total size
            long totalSize = calculateBackupSize(backupPath);

            // Create manifest
            BackupManifest manifest = BackupManifest.builder()
                .backupId(backupId)
                .backupType(BackupType.INCREMENTAL)
                .timestamp(startTime)
                .documentCount((long) documents.size())
                .versionCount((long) versions.size())
                .totalSize(totalSize)
                .lastBackupId(getLastBackupId())
                .startTime(startTime)
                .endTime(LocalDateTime.now())
                .status(BackupManifest.BackupStatus.COMPLETED)
                .build();

            // Write manifest
            writeManifest(backupPath, manifest);

            log.info("Incremental backup completed: {} ({} documents, {} versions)",
                backupId, documents.size(), versions.size());

            return manifest;

        } catch (Exception e) {
            log.error("Incremental backup failed: {}", backupId, e);
            throw new RuntimeException("Incremental backup failed", e);
        }
    }

    /**
     * List all available backups.
     */
    public List<BackupManifest> listBackups() {
        List<BackupManifest> backups = new ArrayList<>();

        try {
            Path backupDir = Paths.get(backupDirectory);
            if (!Files.exists(backupDir)) {
                return backups;
            }

            Files.list(backupDir)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        Path manifestFile = dir.resolve("manifest.json");
                        if (Files.exists(manifestFile)) {
                            BackupManifest manifest = objectMapper.readValue(
                                manifestFile.toFile(), BackupManifest.class);
                            backups.add(manifest);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to read manifest from: {}", dir, e);
                    }
                });

        } catch (Exception e) {
            log.error("Failed to list backups", e);
        }

        return backups.stream()
            .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
            .collect(Collectors.toList());
    }

    /**
     * Delete old backups based on retention policy.
     */
    public int cleanupOldBackups() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        List<BackupManifest> backups = listBackups();
        int deletedCount = 0;

        for (BackupManifest backup : backups) {
            if (backup.getTimestamp().isBefore(cutoffDate)) {
                try {
                    deleteBackup(backup.getBackupId());
                    deletedCount++;
                    log.info("Deleted old backup: {}", backup.getBackupId());
                } catch (Exception e) {
                    log.error("Failed to delete backup: {}", backup.getBackupId(), e);
                }
            }
        }

        return deletedCount;
    }

    /**
     * Delete a specific backup.
     */
    public void deleteBackup(String backupId) throws IOException {
        Path backupPath = Paths.get(backupDirectory, backupId);
        if (Files.exists(backupPath)) {
            Files.walk(backupPath)
                .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.error("Failed to delete: {}", path, e);
                    }
                });
        }
    }

    /**
     * Generate backup ID based on timestamp.
     */
    private String generateBackupId(BackupType type) {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return String.format("%s-%s", type.name().toLowerCase(), timestamp);
    }

    /**
     * Create backup directory structure.
     */
    private Path createBackupDirectory(String backupId, BackupType type) throws IOException {
        Path baseDir = Paths.get(backupDirectory, type.name().toLowerCase());
        Path backupPath = baseDir.resolve(backupId);
        Files.createDirectories(backupPath);
        return backupPath;
    }

    /**
     * Write documents to backup file.
     */
    private void writeDocumentsToFile(Path backupPath, List<Document> documents) throws IOException {
        Path documentsFile = backupPath.resolve("documents.json.gz");
        try (GZIPOutputStream gzip = new GZIPOutputStream(
                new FileOutputStream(documentsFile.toFile()))) {
            objectMapper.writeValue(gzip, documents);
        }
    }

    /**
     * Write versions to backup file.
     */
    private void writeVersionsToFile(Path backupPath, List<Version> versions) throws IOException {
        Path versionsFile = backupPath.resolve("versions.json.gz");
        try (GZIPOutputStream gzip = new GZIPOutputStream(
                new FileOutputStream(versionsFile.toFile()))) {
            objectMapper.writeValue(gzip, versions);
        }
    }

    /**
     * Write manifest to backup directory.
     */
    private void writeManifest(Path backupPath, BackupManifest manifest) throws IOException {
        Path manifestFile = backupPath.resolve("manifest.json");
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(manifestFile.toFile(), manifest);
    }

    /**
     * Calculate total size of backup.
     */
    private long calculateBackupSize(Path backupPath) throws IOException {
        return Files.walk(backupPath)
            .filter(Files::isRegularFile)
            .mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    return 0;
                }
            })
            .sum();
    }

    /**
     * Get timestamp of last backup.
     */
    private LocalDateTime getLastBackupTime() {
        List<BackupManifest> backups = listBackups();
        if (backups.isEmpty()) {
            return null;
        }
        return backups.get(0).getTimestamp();
    }

    /**
     * Get ID of last backup.
     */
    private String getLastBackupId() {
        List<BackupManifest> backups = listBackups();
        if (backups.isEmpty()) {
            return null;
        }
        return backups.get(0).getBackupId();
    }
}
