package org.rowtown.rms.rrr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.rowtown.rms.rrr.domain.entity.*;
import org.rowtown.rms.rrr.dto.DocumentRequest;
import org.rowtown.rms.rrr.dto.DocumentResponse;
import org.rowtown.rms.rrr.exception.ConflictException;
import org.rowtown.rms.rrr.exception.ResourceNotFoundException;
import org.rowtown.rms.rrr.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
    private final StartListDocumentRepository startListDocumentRepository;
    private final RaceResultsDocumentRepository raceResultsDocumentRepository;
    private final TdiModelInspector tdiModelInspector;
    private final EmfDocumentInspector emfDocumentInspector;
    private final ModelValidationService modelValidationService;
    private final ModelUpgradePersistenceService modelUpgradePersistenceService;
    private final RegattaDefinitionDocumentRepository regattaDefinitionDocumentRepository;
    private final VersionControlService versionControlService;
    private final DocumentMetadataRepository metadataRepository;
    private final DocumentTagRepository tagRepository;
    private final DocumentOwnershipRepository ownershipRepository;
    private final NotificationService notificationService;

    /**
     * Create a new document with an initial version.
     */
    @Transactional
    public DocumentResponse createDocument(DocumentRequest request) {
        // Reject malformed / wrong-namespace models before doing any work (400).
        DocumentType type = request.getType();
        modelValidationService.validate(request.getModelData(), SerializationFormat.XMI, type);

        // Build the correct document subtype and validate its key.
        Document document;
        if (type == DocumentType.START_LIST) {
            validateStartListCreation(request);
            StartListDocument startList = new StartListDocument();
            applyCommonFields(startList, request);
            document = startList;
        } else if (type == DocumentType.RML) {
            // The regatta key (name + start date) is derived from the RML model
            // itself and is authoritative.
            RegattaDefinitionDocument rml = new RegattaDefinitionDocument();
            applyCommonFields(rml, request);
            applyRmlDerivedKey(rml, request);
            validateRmlCreation(rml);
            document = rml;
        } else {
            // Race Results: raceId is derived from the submitted model, not the client.
            String raceId = tdiModelInspector.extractRaceId(request.getModelData());
            validateRaceResultsCreation(request, raceId);
            RaceResultsDocument raceResults = new RaceResultsDocument();
            applyCommonFields(raceResults, request);
            raceResults.setRaceId(raceId);
            raceResults.setMilestoneId(request.getMilestoneId());
            raceResults.setTimerRole(request.getTimer());
            document = raceResults;
        }

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

        // Send notifications
        notificationService.notifyDocumentCreated(
            document.getDocumentId(),
            document.getDocumentType(),
            document.getRegattaId(),
            timerLabel(document),
            request.getAuthor()
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

        // Reject malformed / wrong-namespace models before creating a version (400).
        modelValidationService.validate(modelData, format, document.getDocumentType());

        // Create new version
        Version newVersion = versionControlService.createVersion(documentId, modelData, author, changeDescription, format);

        // Send notifications
        notificationService.notifyVersionCreated(
            documentId,
            document.getDocumentType(),
            document.getRegattaId(),
            timerLabel(document),
            newVersion.getVersionNumber(),
            author,
            changeDescription
        );

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

        // If the stored model is on an older metamodel version, upgrade-and-persist
        // it in the background so the cost is paid once, not on every read.
        if (modelUpgradePersistenceService.needsUpgrade(document)) {
            modelUpgradePersistenceService.requestUpgradeAsync(documentId);
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
     * Apply the fields common to every document kind.
     */
    private void applyCommonFields(Document document, DocumentRequest request) {
        // documentType is a read-only mapping over the discriminator column (the
        // discriminator persists it); set it in memory so the create response and
        // notifications carry the correct type before the entity is reloaded.
        document.setDocumentType(request.getType());
        document.setRegattaId(request.getRegattaId());
        document.setRegattaStartDate(request.getRegattaStartDate());
        document.setAuthor(request.getAuthor());
        document.setDescription(request.getDescription());
        document.setLatestVersion(0L);
        // Record the metamodel namespace so each document is self-describing.
        document.setModelNsUri(emfDocumentInspector.rootNamespace(request.getModelData()));
    }

    /**
     * Derive the regatta key (name + start date) from the RML model and set it as
     * authoritative, rejecting any client-supplied value that disagrees.
     */
    private void applyRmlDerivedKey(RegattaDefinitionDocument rml, DocumentRequest request) {
        byte[] modelData = request.getModelData();
        String name = emfDocumentInspector.rootAttribute(modelData, "name");
        String startDateRaw = emfDocumentInspector.rootAttribute(modelData, "startDate");
        if (name == null || startDateRaw == null) {
            throw new IllegalArgumentException(
                "RML model must specify 'name' and 'startDate' on the root Regatta element");
        }
        LocalDate startDate = parseRegattaStartDate(startDateRaw);

        // Server-authoritative: reject if a client-supplied key disagrees with the model.
        if (request.getRegattaId() != null && !name.equals(request.getRegattaId())) {
            throw new IllegalArgumentException(String.format(
                "regattaId '%s' does not match the RML model's regatta name '%s'",
                request.getRegattaId(), name));
        }
        if (request.getRegattaStartDate() != null && !startDate.equals(request.getRegattaStartDate())) {
            throw new IllegalArgumentException(String.format(
                "regattaStartDate '%s' does not match the RML model's startDate '%s'",
                request.getRegattaStartDate(), startDate));
        }

        rml.setRegattaId(name);
        rml.setRegattaStartDate(startDate);
    }

    /**
     * Parse the RML root {@code startDate} attribute into a {@link LocalDate}.
     * The value is a custom EMF datatype; ISO-8601 (yyyy-MM-dd) is expected.
     */
    private LocalDate parseRegattaStartDate(String raw) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "Could not parse RML regatta startDate '" + raw + "' (expected ISO-8601 yyyy-MM-dd)");
        }
    }

    /**
     * Validate RML creation: at most one Regatta Definition per regatta edition.
     */
    private void validateRmlCreation(RegattaDefinitionDocument rml) {
        regattaDefinitionDocumentRepository
            .findByRegattaIdAndRegattaStartDate(rml.getRegattaId(), rml.getRegattaStartDate())
            .ifPresent(existing -> {
                throw new ConflictException(String.format(
                    "Regatta Definition (RML) already exists for regatta '%s' on %s",
                    rml.getRegattaId(), rml.getRegattaStartDate()));
            });
    }

    /**
     * Timer role label for notifications (null for non-Race-Results documents).
     */
    private String timerLabel(Document document) {
        if (document instanceof RaceResultsDocument raceResults && raceResults.getTimerRole() != null) {
            return raceResults.getTimerRole().name();
        }
        return null;
    }

    /**
     * A regatta is identified by name + start date, which is therefore required.
     */
    private void requireRegattaKey(DocumentRequest request) {
        if (request.getRegattaStartDate() == null) {
            throw new IllegalArgumentException("regattaStartDate is required (regatta name + start date form the key)");
        }
    }

    /**
     * Validate Start List creation: at most one per regatta edition.
     */
    private void validateStartListCreation(DocumentRequest request) {
        requireRegattaKey(request);
        startListDocumentRepository
            .findByRegattaIdAndRegattaStartDate(request.getRegattaId(), request.getRegattaStartDate())
            .ifPresent(existing -> {
                throw new ConflictException(String.format(
                    "Start List already exists for regatta '%s' on %s",
                    request.getRegattaId(), request.getRegattaStartDate()));
            });
    }

    /**
     * Validate Race Results creation. The key is
     * (regatta name + start date, race, milestone, timer); raceId is derived
     * from the model, the rest come from the request.
     */
    private void validateRaceResultsCreation(DocumentRequest request, String raceId) {
        requireRegattaKey(request);
        if (raceId == null) {
            throw new IllegalArgumentException(
                "raceId could not be derived from the model; a Race Results model must identify its race");
        }
        if (request.getMilestoneId() == null) {
            throw new IllegalArgumentException("milestoneId is required for Race Results");
        }
        if (request.getTimer() == null) {
            throw new IllegalArgumentException("timer is required for Race Results");
        }
        raceResultsDocumentRepository
            .findByRegattaIdAndRegattaStartDateAndRaceIdAndMilestoneIdAndTimerRole(
                request.getRegattaId(), request.getRegattaStartDate(),
                raceId, request.getMilestoneId(), request.getTimer())
            .ifPresent(existing -> {
                throw new ConflictException(String.format(
                    "Race Results already exist for regatta '%s' on %s, race=%s, milestone=%s, timer=%s",
                    request.getRegattaId(), request.getRegattaStartDate(),
                    raceId, request.getMilestoneId(), request.getTimer()));
            });
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

        DocumentResponse.DocumentResponseBuilder builder = DocumentResponse.builder()
            .documentId(document.getDocumentId())
            .type(document.getDocumentType())
            .regattaId(document.getRegattaId())
            .regattaStartDate(document.getRegattaStartDate())
            .modelNsUri(document.getModelNsUri())
            .author(document.getAuthor())
            .createdAt(document.getCreatedAt())
            .latestVersion(document.getLatestVersion())
            .description(document.getDescription())
            .metadata(metadata)
            .tags(tags)
            .modelData(modelData);

        if (document instanceof RaceResultsDocument raceResults) {
            builder.raceId(raceResults.getRaceId())
                .milestoneId(raceResults.getMilestoneId())
                .timer(raceResults.getTimerRole());
        }

        return builder.build();
    }
}
