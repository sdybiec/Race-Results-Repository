package org.rowtown.rms.rrr.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.rowtown.rms.rrr.domain.entity.Document;
import org.rowtown.rms.rrr.domain.entity.DocumentMetadata;
import org.rowtown.rms.rrr.domain.entity.Version;
import org.rowtown.rms.rrr.repository.DocumentMetadataRepository;
import org.rowtown.rms.rrr.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persists model upgrades so the (potentially expensive, multi-stage) upgrade
 * done on load is paid only once per document.
 *
 * <p>When a stored document is on an older metamodel version than the current
 * one, its bytes are re-loaded (the {@link ModelResourceSetFactory}, e.g. an
 * upgrading {@code ResourceSet}, upgrades them in memory), re-serialized to the
 * current version, and appended as a new document version. The document's
 * {@code model_ns_uri} is updated to current, so subsequent loads skip the
 * upgrade entirely.</p>
 *
 * <p>Two triggers:</p>
 * <ul>
 *   <li><b>Lazy</b> — {@link #requestUpgradeAsync(Long)} is fired from read paths;
 *       the read returns immediately and the write-back happens off the hot path,
 *       deduplicated per document.</li>
 *   <li><b>Batch</b> — {@link #upgradeAll(DocumentType)} drains all
 *       older-version documents of a type (e.g. after publishing a new model).</li>
 * </ul>
 *
 * <p>Each upgrade runs in its own transaction, re-checks the version under the
 * transaction (idempotent), and never persists a failed/partial upgrade.</p>
 */
@Service
@Slf4j
public class ModelUpgradePersistenceService {

    private static final String SYSTEM_AUTHOR = "system";

    private final DocumentRepository documentRepository;
    private final VersionControlService versionControlService;
    private final ModelSerializationService serializationService;
    private final DocumentMetadataRepository metadataRepository;
    private final CurrentModelNamespaces namespaces;
    private final TransactionTemplate transactionTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "model-upgrade");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public ModelUpgradePersistenceService(DocumentRepository documentRepository,
                                          VersionControlService versionControlService,
                                          ModelSerializationService serializationService,
                                          DocumentMetadataRepository metadataRepository,
                                          CurrentModelNamespaces namespaces,
                                          PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.versionControlService = versionControlService;
        this.serializationService = serializationService;
        this.metadataRepository = metadataRepository;
        this.namespaces = namespaces;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * @return true if the document's stored model namespace is older than current.
     */
    public boolean needsUpgrade(Document document) {
        return document.getModelNsUri() != null
            && !namespaces.isCurrent(document.getDocumentType(), document.getModelNsUri());
    }

    /**
     * Request a deferred (asynchronous) upgrade-and-persist for a document. Safe to
     * call from read paths and to call repeatedly — duplicate in-flight requests
     * for the same document are ignored.
     */
    public void requestUpgradeAsync(Long documentId) {
        if (!inFlight.add(documentId)) {
            return;
        }
        try {
            executor.submit(() -> {
                try {
                    upgradeDocument(documentId);
                } catch (Exception e) {
                    log.warn("Deferred model upgrade failed for document {}: {}", documentId, e.getMessage());
                } finally {
                    inFlight.remove(documentId);
                }
            });
        } catch (RuntimeException e) {
            // e.g. executor rejected the task during shutdown
            inFlight.remove(documentId);
            throw e;
        }
    }

    /**
     * Upgrade a single document in its own transaction.
     *
     * @return true if a new (current-version) version was written
     */
    public boolean upgradeDocument(Long documentId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> upgradeWithinTransaction(documentId)));
    }

    /**
     * Upgrade all documents of a type that are not on the current model version.
     *
     * @return the number of documents upgraded
     */
    public int upgradeAll(DocumentType type) {
        String current = namespaces.currentNsUri(type);
        if (current == null) {
            return 0;
        }
        List<Long> ids = documentRepository.findIdsNeedingUpgrade(type, current);
        int upgraded = 0;
        for (Long id : ids) {
            try {
                if (upgradeDocument(id)) {
                    upgraded++;
                }
            } catch (Exception e) {
                log.warn("Batch upgrade failed for document {}: {}", id, e.getMessage());
            }
        }
        log.info("Batch upgrade of {} complete: {}/{} upgraded", type, upgraded, ids.size());
        return upgraded;
    }

    private boolean upgradeWithinTransaction(Long documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null || !needsUpgrade(document)) {
            return false; // idempotent re-check under the transaction
        }

        String fromNsUri = document.getModelNsUri();
        String toNsUri = namespaces.currentNsUri(document.getDocumentType());

        Version latest = versionControlService.getLatestVersion(documentId);
        SerializationFormat format = latest.getSnapshotFormat();

        byte[] upgraded;
        try {
            Object model = serializationService.deserialize(latest.getModelSnapshot(), format);
            if (!(model instanceof EObject eObject)) {
                return false;
            }
            upgraded = serializationService.serialize(eObject, format);
        } catch (Exception e) {
            // Never persist a failed/partial upgrade.
            log.warn("Could not upgrade document {} from {} to {}: {}",
                documentId, fromNsUri, toNsUri, e.getMessage());
            return false;
        }

        versionControlService.createVersion(documentId, upgraded, SYSTEM_AUTHOR,
            "Auto-upgrade " + fromNsUri + " -> " + toNsUri, format);

        document.setModelNsUri(toNsUri);
        documentRepository.save(document);

        recordProvenance(document, fromNsUri, toNsUri);

        log.info("Upgraded document {} from {} to {}", documentId, fromNsUri, toNsUri);
        return true;
    }

    private void recordProvenance(Document document, String fromNsUri, String toNsUri) {
        upsertMetadata(document, "upgradedFrom", fromNsUri);
        upsertMetadata(document, "upgradedTo", toNsUri);
        upsertMetadata(document, "upgradedAt", LocalDateTime.now().toString());
    }

    private void upsertMetadata(Document document, String key, String value) {
        DocumentMetadata meta = metadataRepository
            .findByDocument_DocumentIdAndKey(document.getDocumentId(), key)
            .orElseGet(() -> DocumentMetadata.builder().document(document).key(key).build());
        meta.setValue(value);
        metadataRepository.save(meta);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
