package org.rowtown.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rowtown.domain.SerializationFormat;
import org.rowtown.domain.entity.Document;
import org.rowtown.domain.entity.Version;
import org.rowtown.dto.VersionInfo;
import org.rowtown.exception.ResourceNotFoundException;
import org.rowtown.exception.VersionControlException;
import org.rowtown.repository.DocumentRepository;
import org.rowtown.repository.VersionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing document versions.
 * Implements version control operations including creation, retrieval, comparison, and rollback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VersionControlService {

    private final VersionRepository versionRepository;
    private final DocumentRepository documentRepository;
    private final ModelSerializationService serializationService;

    /**
     * Create a new version of a document.
     */
    @Transactional
    public Version createVersion(Long documentId, byte[] modelData, String author, String changeDescription,
                                 SerializationFormat format) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // Get next version number
        Long nextVersionNumber = versionRepository.findLatestVersionNumber(documentId)
            .map(v -> v + 1)
            .orElse(1L);

        // Calculate checksum
        String checksum = serializationService.calculateChecksum(modelData);

        // Create version
        Version version = Version.builder()
            .document(document)
            .versionNumber(nextVersionNumber)
            .timestamp(LocalDateTime.now())
            .author(author)
            .changeDescription(changeDescription)
            .modelSnapshot(modelData)
            .snapshotFormat(format)
            .checksum(checksum)
            .build();

        version = versionRepository.save(version);

        // Update document's latest version
        document.setLatestVersion(nextVersionNumber);
        documentRepository.save(document);

        log.info("Created version {} for document {}", nextVersionNumber, documentId);
        return version;
    }

    /**
     * Retrieve a specific version of a document.
     */
    @Transactional(readOnly = true)
    public Version getVersion(Long documentId, Long versionNumber) {
        return versionRepository.findByDocument_DocumentIdAndVersionNumber(documentId, versionNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format("Version %d not found for document %d", versionNumber, documentId)));
    }

    /**
     * Retrieve the latest version of a document.
     */
    @Transactional(readOnly = true)
    public Version getLatestVersion(Long documentId) {
        return versionRepository.findFirstByDocument_DocumentIdOrderByVersionNumberDesc(documentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No versions found for document: " + documentId));
    }

    /**
     * List all versions for a document with pagination.
     */
    @Transactional(readOnly = true)
    public Page<VersionInfo> listVersions(Long documentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Version> versions = versionRepository.findByDocument_DocumentIdOrderByVersionNumberDesc(
            documentId, pageable);

        return versions.map(this::toVersionInfo);
    }

    /**
     * List all versions for a document.
     */
    @Transactional(readOnly = true)
    public List<VersionInfo> listAllVersions(Long documentId) {
        List<Version> versions = versionRepository.findByDocument_DocumentIdOrderByVersionNumberDesc(documentId);
        return versions.stream()
            .map(this::toVersionInfo)
            .collect(Collectors.toList());
    }

    /**
     * Compare two versions of a document.
     * Returns a diff model showing the changes between versions.
     */
    @Transactional(readOnly = true)
    public byte[] compareVersions(Long documentId, Long sourceVersion, Long targetVersion) {
        Version source = getVersion(documentId, sourceVersion);
        Version target = getVersion(documentId, targetVersion);

        try {
            // Deserialize both models
            Object sourceModel = serializationService.deserialize(source.getModelSnapshot(), source.getSnapshotFormat());
            Object targetModel = serializationService.deserialize(target.getModelSnapshot(), target.getSnapshotFormat());

            // TODO: Implement EMF Compare integration
            // For now, return a simple indicator that comparison is needed
            log.warn("EMF Compare not yet integrated, returning placeholder diff");
            return new byte[0];
        } catch (Exception e) {
            throw new VersionControlException("Failed to compare versions", e);
        }
    }

    /**
     * Rollback a document to a specific version.
     * Creates a new version with the content from the target version.
     */
    @Transactional
    public Version rollback(Long documentId, Long targetVersion, String author, String description) {
        // Get the target version to rollback to
        Version targetVersionEntity = getVersion(documentId, targetVersion);

        // Create a new version with the target version's content
        String rollbackDescription = description != null ? description :
            String.format("Rollback to version %d", targetVersion);

        return createVersion(
            documentId,
            targetVersionEntity.getModelSnapshot(),
            author,
            rollbackDescription,
            targetVersionEntity.getSnapshotFormat()
        );
    }

    /**
     * Count total versions for a document.
     */
    @Transactional(readOnly = true)
    public long countVersions(Long documentId) {
        return versionRepository.countByDocument_DocumentId(documentId);
    }

    /**
     * Convert Version entity to VersionInfo DTO.
     */
    private VersionInfo toVersionInfo(Version version) {
        return VersionInfo.builder()
            .versionId(version.getVersionId())
            .documentId(version.getDocument().getDocumentId())
            .versionNumber(version.getVersionNumber())
            .timestamp(version.getTimestamp())
            .author(version.getAuthor())
            .changeDescription(version.getChangeDescription())
            .checksum(version.getChecksum())
            .size(version.getModelSnapshot() != null ? (long) version.getModelSnapshot().length : 0L)
            .build();
    }
}
