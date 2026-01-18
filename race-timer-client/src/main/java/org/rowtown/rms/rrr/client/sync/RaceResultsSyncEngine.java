package org.rowtown.rms.rrr.client.sync;

import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.model.SyncStatus;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Synchronization engine for Race Results documents.
 * Handles uploading locally captured results to the repository.
 */
@Slf4j
public class RaceResultsSyncEngine {

    private final LocalStorageManager storage;
    private final RepositoryClient apiClient;
    private final String regattaId;
    private final String timerId;

    public RaceResultsSyncEngine(LocalStorageManager storage, RepositoryClient apiClient,
                                String regattaId, String timerId) {
        this.storage = storage;
        this.apiClient = apiClient;
        this.regattaId = regattaId;
        this.timerId = timerId;
    }

    /**
     * Synchronize all pending Race Results to server.
     * Uploads documents with PENDING or FAILED sync status.
     *
     * @return SyncResult with statistics
     */
    public SyncResult synchronizePending() {
        log.info("Starting Race Results synchronization for timer: {}", timerId);

        SyncResult result = new SyncResult();

        try {
            // Check if server is reachable
            if (!apiClient.isServerReachable()) {
                log.warn("Server not reachable, working offline");
                result.offline = true;
                return result;
            }

            // Get all documents needing sync
            List<LocalDocument> pendingDocs = storage.findPendingSync();

            // Filter to only our Race Results
            List<LocalDocument> ourResults = pendingDocs.stream()
                .filter(doc -> "RACE_RESULTS".equals(doc.getDocumentType()))
                .filter(doc -> timerId.equals(doc.getTimerId()))
                .toList();

            log.info("Found {} Race Results documents to sync", ourResults.size());
            result.totalDocuments = ourResults.size();

            for (LocalDocument localDoc : ourResults) {
                try {
                    boolean success = syncDocument(localDoc);
                    if (success) {
                        result.syncedDocuments++;
                    } else {
                        result.failedDocuments++;
                    }
                } catch (Exception e) {
                    log.error("Failed to sync document {}", localDoc.getLocalId(), e);
                    markSyncFailed(localDoc, e.getMessage());
                    result.failedDocuments++;
                }
            }

            log.info("Sync complete: {} synced, {} failed out of {} total",
                result.syncedDocuments, result.failedDocuments, result.totalDocuments);

        } catch (Exception e) {
            log.error("Error during synchronization", e);
            result.error = e.getMessage();
        }

        return result;
    }

    /**
     * Synchronize a single document.
     */
    private boolean syncDocument(LocalDocument localDoc) throws IOException {
        // Mark as syncing
        localDoc.setSyncStatus(SyncStatus.SYNCING);
        storage.save(localDoc);

        try {
            if (localDoc.getServerId() == null) {
                // Create new document on server
                return createOnServer(localDoc);
            } else {
                // Update existing document on server
                return updateOnServer(localDoc);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Sync failed", e);
        }
    }

    /**
     * Create a new Race Results document on the server.
     */
    private boolean createOnServer(LocalDocument localDoc) throws IOException {
        log.info("Creating new Race Results on server for milestone: {}", localDoc.getMilestoneId());

        RepositoryClient.DocumentRequest request = new RepositoryClient.DocumentRequest();
        request.type = "RACE_RESULTS";
        request.regattaId = localDoc.getRegattaId();
        request.timerId = localDoc.getTimerId();
        request.milestoneId = localDoc.getMilestoneId();
        request.versionType = localDoc.getVersionType();
        request.author = localDoc.getAuthor();
        request.description = localDoc.getDescription();
        request.tags = new HashSet<>();
        request.metadata = new HashMap<>();
        request.modelData = localDoc.getModelData();

        RepositoryClient.DocumentResponse response = apiClient.createDocument(request);

        if (response == null) {
            throw new IOException("Failed to create document on server: response is null");
        }

        // Update local document with server ID and version
        localDoc.setServerId(response.documentId);
        localDoc.setServerVersion(response.latestVersion);
        localDoc.setSyncStatus(SyncStatus.SYNCED);
        localDoc.setLastSyncedAt(LocalDateTime.now());
        localDoc.setLastSyncError(null);
        localDoc.setRetryCount(0);

        storage.save(localDoc);

        log.info("Created Race Results on server (server_id: {}, version: {})",
            response.documentId, response.latestVersion);

        return true;
    }

    /**
     * Update existing Race Results document on the server.
     */
    private boolean updateOnServer(LocalDocument localDoc) throws IOException {
        log.info("Updating Race Results on server (server_id: {})", localDoc.getServerId());

        String changeDescription = String.format("Update from timer %s at %s",
            timerId, LocalDateTime.now());

        RepositoryClient.DocumentResponse response = apiClient.updateDocument(
            localDoc.getServerId(),
            localDoc.getModelData(),
            changeDescription
        );

        if (response == null) {
            throw new IOException("Failed to update document on server: response is null");
        }

        // Update local document with new server version
        localDoc.setServerVersion(response.latestVersion);
        localDoc.setSyncStatus(SyncStatus.SYNCED);
        localDoc.setLastSyncedAt(LocalDateTime.now());
        localDoc.setLastSyncError(null);
        localDoc.setRetryCount(0);

        storage.save(localDoc);

        log.info("Updated Race Results on server (server_id: {}, new version: {})",
            response.documentId, response.latestVersion);

        return true;
    }

    /**
     * Save Race Results locally (to be synced later).
     */
    public LocalDocument saveLocal(String milestoneId, String versionType,
                                   String author, byte[] modelData) {
        log.info("Saving Race Results locally for milestone: {}", milestoneId);

        LocalDocument localDoc = LocalDocument.builder()
            .regattaId(regattaId)
            .timerId(timerId)
            .milestoneId(milestoneId)
            .documentType("RACE_RESULTS")
            .versionType(versionType)
            .author(author)
            .description("Race results for " + milestoneId)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .localVersion(1L)
            .syncStatus(SyncStatus.PENDING)
            .modelData(modelData)
            .serializationFormat("JSON")
            .retryCount(0)
            .build();

        return storage.save(localDoc);
    }

    /**
     * Update Race Results locally.
     */
    public LocalDocument updateLocal(Long localId, byte[] modelData) {
        log.info("Updating local Race Results: {}", localId);

        LocalDocument localDoc = storage.findById(localId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + localId));

        localDoc.setModelData(modelData);
        localDoc.setModifiedAt(LocalDateTime.now());
        localDoc.setLocalVersion(localDoc.getLocalVersion() != null ? localDoc.getLocalVersion() + 1 : 1);
        localDoc.setSyncStatus(SyncStatus.PENDING);

        return storage.save(localDoc);
    }

    /**
     * Mark a document sync as failed.
     */
    private void markSyncFailed(LocalDocument localDoc, String error) {
        localDoc.setSyncStatus(SyncStatus.FAILED);
        localDoc.setLastSyncError(error);
        localDoc.setRetryCount(localDoc.getRetryCount() != null ? localDoc.getRetryCount() + 1 : 1);

        try {
            storage.save(localDoc);
        } catch (Exception e) {
            log.error("Failed to save error status to database for document {}", localDoc.getLocalId(), e);
        }
    }

    /**
     * Get count of pending documents.
     */
    public int getPendingCount() {
        List<LocalDocument> pending = storage.findPendingSync();
        return (int) pending.stream()
            .filter(doc -> "RACE_RESULTS".equals(doc.getDocumentType()))
            .filter(doc -> timerId.equals(doc.getTimerId()))
            .count();
    }

    /**
     * Get all local Race Results for this timer.
     */
    public List<LocalDocument> getAllLocal() {
        return storage.findByRegattaAndType(regattaId, "RACE_RESULTS").stream()
            .filter(doc -> timerId.equals(doc.getTimerId()))
            .toList();
    }

    /**
     * Result of synchronization operation.
     */
    public static class SyncResult {
        public int totalDocuments;
        public int syncedDocuments;
        public int failedDocuments;
        public boolean offline;
        public String error;

        public boolean isSuccess() {
            return !offline && error == null && failedDocuments == 0;
        }

        @Override
        public String toString() {
            return String.format("SyncResult[total=%d, synced=%d, failed=%d, offline=%b]",
                totalDocuments, syncedDocuments, failedDocuments, offline);
        }
    }
}
