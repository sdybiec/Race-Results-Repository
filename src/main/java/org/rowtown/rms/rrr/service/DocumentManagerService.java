package org.rowtown.rms.rrr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.rowtown.rms.rrr.domain.entity.*;
import org.rowtown.rms.rrr.dto.DocumentRequest;
import org.rowtown.rms.rrr.dto.DocumentResponse;
import org.rowtown.rms.rrr.exception.ConflictException;
import org.rowtown.rms.rrr.exception.ResourceNotFoundException;
import org.rowtown.rms.rrr.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing documents in the repository.
 * Handles document creation, updates, and retrieval with version control integration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentManagerService {

    private final DocumentRepository documentRepository;
    private final VersionControlService versionControlService;
    private final DocumentMetadataRepository metadataRepository;
    private final DocumentTagRepository tagRepository;
    private final DocumentOwnershipRepository ownershipRepository;

    /**
     * Create a new document with an initial version.
     */
    @Transactional
    public DocumentResponse createDocument(DocumentRequest request) {
        // Check for conflicts (e.g., duplicate Start List for same regatta)
        validateDocumentCreation(request);

        // Create document entity
        Document document = Document.builder()
            .documentType(request.getType())
            .regattaId(request.getRegattaId())
            .timerId(request.getTimerId())
            .milestoneId(request.getMilestoneId())
            .versionType(request.getVersionType())
            .author(request.getAuthor())
            .description(request.getDescription())
            .createdAt(LocalDateTime.now())
            .latestVersion(0L)
            .build();

        document = documentRepository.save(document);

        // Add metadata
        if (request.getMetadata() != null) {
            for (Map.Entry<String, String> entry : request.getMetadata().entrySet()) {
                DocumentMetadata metadata = DocumentMetadata.builder()
                    .document(document)
                    .key(entry.getKey())
                    .value(entry.getValue())
                    .build();
                metadataRepository.save(metadata);
            }
        }

        // Add tags
        if (request.getTags() != null) {
            for (String tagName : request.getTags()) {
                DocumentTag tag = DocumentTag.builder()
                    .document(document)
                    .tagName(tagName)
                    .build();
                tagRepository.save(tag);
            }
        }

        // Create ownership record
        DocumentOwnership ownership = DocumentOwnership.builder()
            .document(document)
            .ownerUserId(request.getAuthor())
            .build();
        ownershipRepository.save(ownership);

        // Create initial version
        versionControlService.createVersion(
            document.getDocumentId(),
            request.getModelData(),
            request.getAuthor(),
            "Initial version",
            SerializationFormat.XMI
        );

        log.info("Created document {} of type {} for regatta {}",
            document.getDocumentId(), request.getType(), request.getRegattaId());

        return toDocumentResponse(document, request.getModelData());
    }

    /**
     * Update a document, creating a new version.
     */
    @Transactional
    public DocumentResponse updateDocument(Long documentId, byte[] modelData, String author,
                                          String changeDescription, SerializationFormat format) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // Create new version
        versionControlService.createVersion(documentId, modelData, author, changeDescription, format);

        log.info("Updated document {} - new version {}", documentId, document.getLatestVersion());

        return toDocumentResponse(document, modelData);
    }

    /**
     * Retrieve a document with a specific version.
     */
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long documentId, Long versionNumber) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        Version version;
        if (versionNumber != null) {
            version = versionControlService.getVersion(documentId, versionNumber);
        } else {
            version = versionControlService.getLatestVersion(documentId);
        }

        return toDocumentResponse(document, version.getModelSnapshot());
    }

    /**
     * Retrieve the latest version of a document.
     */
    @Transactional(readOnly = true)
    public DocumentResponse getLatestDocument(Long documentId) {
        return getDocument(documentId, null);
    }

    /**
     * Delete a document and all its versions.
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        documentRepository.delete(document);
        log.info("Deleted document {}", documentId);
    }

    /**
     * Validate document creation to prevent conflicts.
     */
    private void validateDocumentCreation(DocumentRequest request) {
        switch (request.getType()) {
            case START_LIST:
                // Only one Start List per regatta
                documentRepository.findByRegattaIdAndDocumentType(
                    request.getRegattaId(), request.getType())
                    .ifPresent(existing -> {
                        throw new ConflictException(
                            "Start List already exists for regatta: " + request.getRegattaId());
                    });
                break;

            case RACE_RESULTS:
                // One Race Results per regatta/timer/milestone combination
                if (request.getTimerId() != null && request.getMilestoneId() != null) {
                    documentRepository.findByRegattaIdAndTimerIdAndMilestoneId(
                        request.getRegattaId(), request.getTimerId(), request.getMilestoneId())
                        .ifPresent(existing -> {
                            throw new ConflictException(
                                String.format("Race Results already exists for regatta=%s, timer=%s, milestone=%s",
                                    request.getRegattaId(), request.getTimerId(), request.getMilestoneId()));
                        });
                }
                break;
        }
    }

    /**
     * Convert Document entity to DocumentResponse DTO.
     */
    private DocumentResponse toDocumentResponse(Document document, byte[] modelData) {
        // Fetch metadata
        Map<String, String> metadata = metadataRepository.findByDocument_DocumentId(document.getDocumentId())
            .stream()
            .collect(Collectors.toMap(DocumentMetadata::getKey, DocumentMetadata::getValue));

        // Fetch tags
        Set<String> tags = tagRepository.findByDocument_DocumentId(document.getDocumentId())
            .stream()
            .map(DocumentTag::getTagName)
            .collect(Collectors.toSet());

        return DocumentResponse.builder()
            .documentId(document.getDocumentId())
            .type(document.getDocumentType())
            .regattaId(document.getRegattaId())
            .timerId(document.getTimerId())
            .milestoneId(document.getMilestoneId())
            .versionType(document.getVersionType())
            .author(document.getAuthor())
            .createdAt(document.getCreatedAt())
            .latestVersion(document.getLatestVersion())
            .description(document.getDescription())
            .metadata(metadata)
            .tags(tags)
            .modelData(modelData)
            .build();
    }
}
