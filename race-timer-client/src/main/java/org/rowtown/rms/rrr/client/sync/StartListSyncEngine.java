package org.rowtown.rms.rrr.client.sync;

import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.model.SyncStatus;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Synchronization engine for Start List documents.
 * Handles downloading and keeping the local Start List updated.
 */
@Slf4j
public class StartListSyncEngine {

    private final LocalStorageManager storage;
    private final RepositoryClient apiClient;
    private final String regattaId;

    public StartListSyncEngine(LocalStorageManager storage, RepositoryClient apiClient, String regattaId) {
        this.storage = storage;
        this.apiClient = apiClient;
        this.regattaId = regattaId;
    }

    /**
     * Synchronize Start List from server.
     * Downloads the latest version and updates local storage.
     *
     * @return true if sync was successful, false otherwise
     */
    public boolean synchronize() {
        log.info("Starting Start List synchronization for regatta: {}", regattaId);

        try {
            // Check if server is reachable
            if (!apiClient.isServerReachable()) {
                log.warn("Server not reachable, working offline");
                return false;
            }

            // Get Start List from server
            RepositoryClient.DocumentResponse serverDoc = apiClient.getStartList(regattaId);

            if (serverDoc == null) {
                log.warn("No Start List found on server for regatta: {}", regattaId);
                return false;
            }

            // Check if we have a local copy
            Optional<LocalDocument> localDocOpt = findLocalStartList();

            if (localDocOpt.isPresent()) {
                return updateExistingStartList(localDocOpt.get(), serverDoc);
            } else {
                return createLocalStartList(serverDoc);
            }

        } catch (Exception e) {
            log.error("Failed to synchronize Start List", e);
            return false;
        }
    }

    /**
     * Force refresh of Start List from server.
     * Called when notification indicates Start List has changed.
     */
    public boolean forceRefresh() {
        log.info("Force refreshing Start List for regatta: {}", regattaId);
        return synchronize();
    }

    /**
     * Get the local Start List.
     */
    public Optional<LocalDocument> getLocalStartList() {
        return findLocalStartList();
    }

    /**
     * Find local Start List document.
     */
    private Optional<LocalDocument> findLocalStartList() {
        var docs = storage.findByRegattaAndType(regattaId, "START_LIST");
        return docs.isEmpty() ? Optional.empty() : Optional.of(docs.get(0));
    }

    /**
     * Create a new local Start List from server data.
     */
    private boolean createLocalStartList(RepositoryClient.DocumentResponse serverDoc) {
        try {
            LocalDocument localDoc = LocalDocument.builder()
                .serverId(serverDoc.documentId)
                .regattaId(serverDoc.regattaId)
                .documentType("START_LIST")
                .author(serverDoc.author)
                .description(serverDoc.description)
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .localVersion(1L)
                .serverVersion(serverDoc.latestVersion)
                .syncStatus(SyncStatus.SYNCED)
                .lastSyncedAt(LocalDateTime.now())
                .modelData(serverDoc.modelData)
                .serializationFormat("JSON")
                .build();

            storage.save(localDoc);
            log.info("Created local Start List (server_id: {}, version: {})",
                serverDoc.documentId, serverDoc.latestVersion);

            return true;

        } catch (Exception e) {
            log.error("Failed to create local Start List", e);
            return false;
        }
    }

    /**
     * Update existing local Start List with server data.
     */
    private boolean updateExistingStartList(LocalDocument localDoc,
                                           RepositoryClient.DocumentResponse serverDoc) {
        try {
            // Check if server version is newer
            if (serverDoc.latestVersion.equals(localDoc.getServerVersion())) {
                log.debug("Local Start List is up to date (version: {})", serverDoc.latestVersion);
                return true;
            }

            if (serverDoc.latestVersion < localDoc.getServerVersion()) {
                log.warn("Server version ({}) is older than local version ({}), skipping update",
                    serverDoc.latestVersion, localDoc.getServerVersion());
                return false;
            }

            // Update local document with server data
            localDoc.setModelData(serverDoc.modelData);
            localDoc.setServerVersion(serverDoc.latestVersion);
            localDoc.setLocalVersion(localDoc.getLocalVersion() != null ? localDoc.getLocalVersion() + 1 : 1);
            localDoc.setModifiedAt(LocalDateTime.now());
            localDoc.setSyncStatus(SyncStatus.SYNCED);
            localDoc.setLastSyncedAt(LocalDateTime.now());
            localDoc.setLastSyncError(null);

            storage.save(localDoc);

            log.info("Updated local Start List to version {}", serverDoc.latestVersion);
            return true;

        } catch (Exception e) {
            log.error("Failed to update local Start List", e);

            // Mark as failed but keep old data
            localDoc.setSyncStatus(SyncStatus.FAILED);
            localDoc.setLastSyncError(e.getMessage());
            localDoc.setRetryCount(localDoc.getRetryCount() != null ? localDoc.getRetryCount() + 1 : 1);
            storage.save(localDoc);

            return false;
        }
    }

    /**
     * Check if local Start List needs synchronization.
     */
    public boolean needsSync() {
        Optional<LocalDocument> localDoc = findLocalStartList();

        if (localDoc.isEmpty()) {
            return true; // No local copy, need to download
        }

        // Check if sync failed before
        return localDoc.get().getSyncStatus() == SyncStatus.FAILED;
    }

    /**
     * Get time since last sync.
     */
    public Long getMinutesSinceLastSync() {
        Optional<LocalDocument> localDoc = findLocalStartList();

        if (localDoc.isEmpty() || localDoc.get().getLastSyncedAt() == null) {
            return null;
        }

        LocalDateTime lastSync = localDoc.get().getLastSyncedAt();
        return java.time.Duration.between(lastSync, LocalDateTime.now()).toMinutes();
    }
}
