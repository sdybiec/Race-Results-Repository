package org.rowtown.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rowtown.backup.BackupManifest;
import org.rowtown.domain.entity.Document;
import org.rowtown.domain.entity.Version;
import org.rowtown.repository.DocumentRepository;
import org.rowtown.repository.VersionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Service for restoring repository from backups.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestoreService {

    private final DocumentRepository documentRepository;
    private final VersionRepository versionRepository;

    @Value("${backup.directory}")
    private String backupDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    /**
     * Restore repository from a full backup.
     * WARNING: This will replace all existing data.
     */
    @Transactional
    public void restoreFull(String backupId) {
        log.warn("Starting full restore from backup: {}", backupId);

        try {
            // Validate backup exists and is complete
            BackupManifest manifest = validateBackup(backupId);

            // Delete all existing data
            log.warn("Deleting all existing data...");
            versionRepository.deleteAll();
            documentRepository.deleteAll();

            // Restore documents
            List<Document> documents = readDocumentsFromBackup(backupId);
            log.info("Restoring {} documents", documents.size());
            documentRepository.saveAll(documents);

            // Restore versions
            List<Version> versions = readVersionsFromBackup(backupId);
            log.info("Restoring {} versions", versions.size());
            versionRepository.saveAll(versions);

            log.info("Full restore completed successfully from backup: {}", backupId);

        } catch (Exception e) {
            log.error("Full restore failed from backup: {}", backupId, e);
            throw new RuntimeException("Restore failed", e);
        }
    }

    /**
     * Restore specific documents from a backup.
     */
    @Transactional
    public void restoreSelective(String backupId, List<Long> documentIds) {
        log.info("Starting selective restore from backup: {}", backupId);

        try {
            // Validate backup
            BackupManifest manifest = validateBackup(backupId);

            // Read all documents from backup
            List<Document> allDocuments = readDocumentsFromBackup(backupId);
            List<Version> allVersions = readVersionsFromBackup(backupId);

            // Filter to requested documents
            List<Document> documentsToRestore = allDocuments.stream()
                .filter(doc -> documentIds.contains(doc.getDocumentId()))
                .toList();

            List<Version> versionsToRestore = allVersions.stream()
                .filter(v -> documentIds.contains(v.getDocument().getDocumentId()))
                .toList();

            // Delete existing versions of these documents
            for (Long docId : documentIds) {
                if (documentRepository.existsById(docId)) {
                    versionRepository.deleteAll(
                        versionRepository.findByDocument_DocumentIdOrderByVersionNumberDesc(docId));
                    documentRepository.deleteById(docId);
                }
            }

            // Restore selected documents and versions
            documentRepository.saveAll(documentsToRestore);
            versionRepository.saveAll(versionsToRestore);

            log.info("Selective restore completed: {} documents restored", documentsToRestore.size());

        } catch (Exception e) {
            log.error("Selective restore failed from backup: {}", backupId, e);
            throw new RuntimeException("Selective restore failed", e);
        }
    }

    /**
     * Restore repository to a specific point in time using incremental backups.
     */
    @Transactional
    public void restorePointInTime(LocalDateTime targetTime) {
        log.info("Starting point-in-time restore to: {}", targetTime);

        try {
            // Find the last full backup before target time
            BackupManifest fullBackup = findFullBackupBefore(targetTime);
            if (fullBackup == null) {
                throw new IllegalStateException("No full backup found before target time");
            }

            // Restore from full backup
            restoreFull(fullBackup.getBackupId());

            // Apply incremental backups in order
            List<BackupManifest> incrementalBackups = findIncrementalBackupsBetween(
                fullBackup.getTimestamp(), targetTime);

            for (BackupManifest incremental : incrementalBackups) {
                applyIncrementalBackup(incremental.getBackupId());
            }

            log.info("Point-in-time restore completed to: {}", targetTime);

        } catch (Exception e) {
            log.error("Point-in-time restore failed", e);
            throw new RuntimeException("Point-in-time restore failed", e);
        }
    }

    /**
     * Validate that a backup exists and is complete.
     */
    private BackupManifest validateBackup(String backupId) throws IOException {
        Path backupPath = findBackupPath(backupId);
        if (backupPath == null || !Files.exists(backupPath)) {
            throw new IllegalArgumentException("Backup not found: " + backupId);
        }

        Path manifestFile = backupPath.resolve("manifest.json");
        if (!Files.exists(manifestFile)) {
            throw new IllegalStateException("Backup manifest not found: " + backupId);
        }

        BackupManifest manifest = objectMapper.readValue(manifestFile.toFile(), BackupManifest.class);

        if (manifest.getStatus() != BackupManifest.BackupStatus.COMPLETED) {
            throw new IllegalStateException("Backup is not in completed state: " + backupId);
        }

        // Verify files exist
        Path documentsFile = backupPath.resolve("documents.json.gz");
        Path versionsFile = backupPath.resolve("versions.json.gz");

        if (!Files.exists(documentsFile) || !Files.exists(versionsFile)) {
            throw new IllegalStateException("Backup files are missing: " + backupId);
        }

        return manifest;
    }

    /**
     * Read documents from backup file.
     */
    private List<Document> readDocumentsFromBackup(String backupId) throws IOException {
        Path backupPath = findBackupPath(backupId);
        Path documentsFile = backupPath.resolve("documents.json.gz");

        try (GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(documentsFile.toFile()))) {
            Document[] documents = objectMapper.readValue(gzip, Document[].class);
            return Arrays.asList(documents);
        }
    }

    /**
     * Read versions from backup file.
     */
    private List<Version> readVersionsFromBackup(String backupId) throws IOException {
        Path backupPath = findBackupPath(backupId);
        Path versionsFile = backupPath.resolve("versions.json.gz");

        try (GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(versionsFile.toFile()))) {
            Version[] versions = objectMapper.readValue(gzip, Version[].class);
            return Arrays.asList(versions);
        }
    }

    /**
     * Apply an incremental backup on top of existing data.
     */
    private void applyIncrementalBackup(String backupId) throws IOException {
        List<Document> documents = readDocumentsFromBackup(backupId);
        List<Version> versions = readVersionsFromBackup(backupId);

        // Merge documents (update or insert)
        for (Document doc : documents) {
            documentRepository.save(doc);
        }

        // Merge versions
        for (Version version : versions) {
            versionRepository.save(version);
        }

        log.info("Applied incremental backup: {}", backupId);
    }

    /**
     * Find backup path by ID.
     */
    private Path findBackupPath(String backupId) {
        try {
            Path fullPath = Paths.get(backupDirectory, "full", backupId);
            if (Files.exists(fullPath)) {
                return fullPath;
            }

            Path incrementalPath = Paths.get(backupDirectory, "incremental", backupId);
            if (Files.exists(incrementalPath)) {
                return incrementalPath;
            }
        } catch (Exception e) {
            log.error("Error finding backup path", e);
        }
        return null;
    }

    /**
     * Find the last full backup before a specific time.
     */
    private BackupManifest findFullBackupBefore(LocalDateTime targetTime) {
        try {
            Path fullBackupDir = Paths.get(backupDirectory, "full");
            if (!Files.exists(fullBackupDir)) {
                return null;
            }

            return Files.list(fullBackupDir)
                .filter(Files::isDirectory)
                .map(dir -> {
                    try {
                        Path manifestFile = dir.resolve("manifest.json");
                        if (Files.exists(manifestFile)) {
                            return objectMapper.readValue(manifestFile.toFile(), BackupManifest.class);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to read manifest", e);
                    }
                    return null;
                })
                .filter(manifest -> manifest != null &&
                    manifest.getTimestamp().isBefore(targetTime))
                .max((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                .orElse(null);

        } catch (Exception e) {
            log.error("Error finding full backup", e);
            return null;
        }
    }

    /**
     * Find incremental backups between two times.
     */
    private List<BackupManifest> findIncrementalBackupsBetween(LocalDateTime start, LocalDateTime end) {
        try {
            Path incrementalDir = Paths.get(backupDirectory, "incremental");
            if (!Files.exists(incrementalDir)) {
                return List.of();
            }

            return Files.list(incrementalDir)
                .filter(Files::isDirectory)
                .map(dir -> {
                    try {
                        Path manifestFile = dir.resolve("manifest.json");
                        if (Files.exists(manifestFile)) {
                            return objectMapper.readValue(manifestFile.toFile(), BackupManifest.class);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to read manifest", e);
                    }
                    return null;
                })
                .filter(manifest -> manifest != null &&
                    manifest.getTimestamp().isAfter(start) &&
                    manifest.getTimestamp().isBefore(end))
                .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                .toList();

        } catch (Exception e) {
            log.error("Error finding incremental backups", e);
            return List.of();
        }
    }
}
