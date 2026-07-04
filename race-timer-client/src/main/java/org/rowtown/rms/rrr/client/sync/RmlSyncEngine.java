package org.rowtown.rms.rrr.client.sync;

import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.model.SyncStatus;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

/**
 * Synchronization engine for the RML (Regatta Definition) document — one per
 * regatta edition.
 *
 * <p>The client both <em>consumes</em> the regatta definition ({@link #synchronize()}
 * downloads and caches it) and <em>produces</em> it ({@link #saveLocal} + {@link #push}
 * upload changes). The regatta key is derived from the model itself.</p>
 */
@Slf4j
public class RmlSyncEngine {

    private static final String TYPE = "RML";

    private final LocalStorageManager storage;
    private final RepositoryClient apiClient;
    private final String regattaId;
    private final String regattaStartDate;

    public RmlSyncEngine(LocalStorageManager storage, RepositoryClient apiClient,
                         String regattaId, String regattaStartDate) {
        this.storage = storage;
        this.apiClient = apiClient;
        this.regattaId = regattaId;
        this.regattaStartDate = regattaStartDate;
    }

    // ---- Consume: download the regatta definition from the server ----

    /**
     * Download the RML for this regatta edition and update local storage.
     *
     * @return true if a definition was downloaded/refreshed
     */
    public boolean synchronize() {
        log.info("Synchronizing Regatta Definition for regatta: {}", regattaId);
        try {
            if (!apiClient.isServerReachable()) {
                log.warn("Server not reachable, working offline");
                return false;
            }

            RepositoryClient.DocumentResponse serverDoc =
                apiClient.getRegattaDefinition(regattaId, regattaStartDate);
            if (serverDoc == null) {
                log.warn("No Regatta Definition found on server for regatta: {}", regattaId);
                return false;
            }

            Optional<LocalDocument> localOpt = findLocal();
            return localOpt.isPresent()
                ? updateExisting(localOpt.get(), serverDoc)
                : createLocal(serverDoc);

        } catch (Exception e) {
            log.error("Failed to synchronize Regatta Definition", e);
            return false;
        }
    }

    public boolean forceRefresh() {
        return synchronize();
    }

    public Optional<LocalDocument> getLocal() {
        return findLocal();
    }

    public boolean needsSync() {
        Optional<LocalDocument> local = findLocal();
        return local.isEmpty() || local.get().getSyncStatus() == SyncStatus.FAILED;
    }

    public Long getMinutesSinceLastSync() {
        Optional<LocalDocument> local = findLocal();
        if (local.isEmpty() || local.get().getLastSyncedAt() == null) {
            return null;
        }
        return Duration.between(local.get().getLastSyncedAt(), LocalDateTime.now()).toMinutes();
    }

    private Optional<LocalDocument> findLocal() {
        var docs = storage.findByRegattaAndType(regattaId, TYPE);
        return docs.isEmpty() ? Optional.empty() : Optional.of(docs.get(0));
    }

    private boolean createLocal(RepositoryClient.DocumentResponse serverDoc) {
        LocalDocument localDoc = LocalDocument.builder()
            .serverId(serverDoc.documentId)
            .regattaId(serverDoc.regattaId)
            .regattaStartDate(serverDoc.regattaStartDate)
            .documentType(TYPE)
            .author(serverDoc.author)
            .description(serverDoc.description)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .localVersion(1L)
            .serverVersion(serverDoc.latestVersion)
            .syncStatus(SyncStatus.SYNCED)
            .lastSyncedAt(LocalDateTime.now())
            .modelData(serverDoc.modelData)
            .serializationFormat("XMI")
            .retryCount(0)
            .build();
        storage.save(localDoc);
        log.info("Created local Regatta Definition (server_id: {}, version: {})",
            serverDoc.documentId, serverDoc.latestVersion);
        return true;
    }

    private boolean updateExisting(LocalDocument localDoc, RepositoryClient.DocumentResponse serverDoc) {
        if (serverDoc.latestVersion.equals(localDoc.getServerVersion())) {
            log.debug("Local Regatta Definition is up to date (version: {})", serverDoc.latestVersion);
            return true;
        }
        if (serverDoc.latestVersion < localDoc.getServerVersion()) {
            log.warn("Server version ({}) older than local ({}), skipping",
                serverDoc.latestVersion, localDoc.getServerVersion());
            return false;
        }
        localDoc.setModelData(serverDoc.modelData);
        localDoc.setServerVersion(serverDoc.latestVersion);
        localDoc.setLocalVersion(localDoc.getLocalVersion() != null ? localDoc.getLocalVersion() + 1 : 1);
        localDoc.setModifiedAt(LocalDateTime.now());
        localDoc.setSyncStatus(SyncStatus.SYNCED);
        localDoc.setLastSyncedAt(LocalDateTime.now());
        localDoc.setLastSyncError(null);
        storage.save(localDoc);
        log.info("Updated local Regatta Definition to version {}", serverDoc.latestVersion);
        return true;
    }

    // ---- Produce: save the regatta definition and push it to the server ----

    /**
     * Save (create or replace) the regatta definition locally, marked PENDING for
     * upload. The regatta key is derived from the model.
     */
    public LocalDocument saveLocal(byte[] modelData, String author,
                                   String derivedRegattaId, String derivedStartDate) {
        Optional<LocalDocument> existing = findLocal();
        if (existing.isPresent()) {
            LocalDocument doc = existing.get();
            doc.setModelData(modelData);
            doc.setModifiedAt(LocalDateTime.now());
            doc.setLocalVersion(doc.getLocalVersion() != null ? doc.getLocalVersion() + 1 : 1);
            doc.setSyncStatus(SyncStatus.PENDING);
            return storage.save(doc);
        }

        LocalDocument doc = LocalDocument.builder()
            .regattaId(derivedRegattaId)
            .regattaStartDate(derivedStartDate)
            .documentType(TYPE)
            .author(author)
            .description("Regatta definition for " + derivedRegattaId)
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .localVersion(1L)
            .syncStatus(SyncStatus.PENDING)
            .modelData(modelData)
            .serializationFormat("XMI")
            .retryCount(0)
            .build();
        return storage.save(doc);
    }

    /**
     * Push the pending regatta definition to the server (create or update).
     *
     * @return true if pushed (or already synced), false if offline / failed
     */
    public boolean push() {
        Optional<LocalDocument> opt = findLocal();
        if (opt.isEmpty()) {
            return false;
        }
        LocalDocument doc = opt.get();
        if (doc.getSyncStatus() == SyncStatus.SYNCED) {
            return true;
        }
        if (!apiClient.isServerReachable()) {
            log.warn("Server not reachable, Regatta Definition remains pending");
            return false;
        }
        try {
            doc.setSyncStatus(SyncStatus.SYNCING);
            storage.save(doc);
            if (doc.getServerId() == null) {
                createOnServer(doc);
            } else {
                updateOnServer(doc);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to push Regatta Definition", e);
            markFailed(doc, e.getMessage());
            return false;
        }
    }

    private void createOnServer(LocalDocument doc) throws Exception {
        RepositoryClient.DocumentRequest request = new RepositoryClient.DocumentRequest();
        request.type = TYPE;
        request.regattaId = doc.getRegattaId();
        request.regattaStartDate = doc.getRegattaStartDate();
        request.author = doc.getAuthor();
        request.description = doc.getDescription();
        request.tags = new HashSet<>();
        request.metadata = new HashMap<>();
        request.modelData = doc.getModelData();

        RepositoryClient.DocumentResponse response = apiClient.createDocument(request);
        if (response == null) {
            throw new java.io.IOException("Failed to create Regatta Definition: response is null");
        }
        doc.setServerId(response.documentId);
        doc.setServerVersion(response.latestVersion);
        markSynced(doc);
        log.info("Created Regatta Definition on server (server_id: {}, version: {})",
            response.documentId, response.latestVersion);
    }

    private void updateOnServer(LocalDocument doc) throws Exception {
        String changeDescription = "Regatta definition update at " + LocalDateTime.now();
        RepositoryClient.DocumentResponse response = apiClient.updateDocument(
            doc.getServerId(), doc.getModelData(), changeDescription);
        if (response == null) {
            throw new java.io.IOException("Failed to update Regatta Definition: response is null");
        }
        doc.setServerVersion(response.latestVersion);
        markSynced(doc);
        log.info("Updated Regatta Definition on server (server_id: {}, new version: {})",
            response.documentId, response.latestVersion);
    }

    /**
     * Delete the regatta definition locally and (if present) on the server.
     */
    public boolean delete() {
        Optional<LocalDocument> opt = findLocal();
        if (opt.isEmpty()) {
            return false;
        }
        LocalDocument doc = opt.get();
        try {
            if (doc.getServerId() != null && apiClient.isServerReachable()) {
                apiClient.deleteDocument(doc.getServerId());
            }
            storage.delete(doc.getLocalId());
            log.info("Deleted Regatta Definition (server_id: {})", doc.getServerId());
            return true;
        } catch (Exception e) {
            log.error("Failed to delete Regatta Definition", e);
            return false;
        }
    }

    private void markSynced(LocalDocument doc) {
        doc.setSyncStatus(SyncStatus.SYNCED);
        doc.setLastSyncedAt(LocalDateTime.now());
        doc.setLastSyncError(null);
        doc.setRetryCount(0);
        storage.save(doc);
    }

    private void markFailed(LocalDocument doc, String error) {
        doc.setSyncStatus(SyncStatus.FAILED);
        doc.setLastSyncError(error);
        doc.setRetryCount(doc.getRetryCount() != null ? doc.getRetryCount() + 1 : 1);
        try {
            storage.save(doc);
        } catch (Exception e) {
            log.error("Failed to save error status for Regatta Definition", e);
        }
    }
}
