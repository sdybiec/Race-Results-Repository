package org.rowtown.rms.rrr.client.sync;

import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Automatically fetches and caches documents from the server when notified of changes.
 *
 * <p>This class handles the heavy lifting of document synchronization:
 * <ul>
 *   <li>Fetches documents from the server API</li>
 *   <li>Saves them to local storage</li>
 *   <li>Handles errors and retries</li>
 *   <li>Manages async operations</li>
 * </ul>
 */
@Slf4j
public class AutoSyncManager {

    private final RepositoryClient repositoryClient;
    private final LocalStorageManager storageManager;
    private final ExecutorService syncExecutor;

    /**
     * Create a new auto-sync manager.
     *
     * @param repositoryClient Client for fetching documents from server
     * @param storageManager Manager for local document storage
     */
    public AutoSyncManager(RepositoryClient repositoryClient, LocalStorageManager storageManager) {
        this.repositoryClient = repositoryClient;
        this.storageManager = storageManager;
        this.syncExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread thread = new Thread(r);
            thread.setName("auto-sync-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Synchronize a document based on a notification event.
     *
     * <p>This method:
     * <ol>
     *   <li>Fetches the latest version from the server</li>
     *   <li>Saves it to local storage</li>
     *   <li>Returns the synchronized document</li>
     * </ol>
     *
     * @param documentId The ID of the document to sync
     * @param versionNumber The version number from the notification
     * @return A CompletableFuture that resolves to the synchronized document
     */
    public CompletableFuture<LocalDocument> syncDocument(Long documentId, Long versionNumber) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Syncing document {} version {}", documentId, versionNumber);

                // Fetch document from server
                var serverDoc = repositoryClient.getDocument(documentId);

                // Convert to LocalDocument
                LocalDocument localDoc = convertToLocalDocument(serverDoc);

                // Save to local storage
                storageManager.saveDocument(localDoc);

                log.info("Successfully synced document {} version {}", documentId, versionNumber);
                return localDoc;

            } catch (Exception e) {
                log.error("Failed to sync document {} version {}", documentId, versionNumber, e);
                throw new SyncException("Failed to sync document " + documentId, e);
            }
        }, syncExecutor);
    }

    /**
     * Batch synchronize multiple documents efficiently.
     *
     * <p>Fetches and saves multiple documents in parallel.</p>
     *
     * @param documentIds List of document IDs to sync
     * @return A CompletableFuture that resolves when all documents are synced
     */
    public CompletableFuture<List<LocalDocument>> syncBatch(List<Long> documentIds) {
        List<CompletableFuture<LocalDocument>> futures = documentIds.stream()
            .map(id -> syncDocument(id, null))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }

    /**
     * Check if a document should be fetched based on local cache.
     *
     * @param documentId The document ID
     * @param serverVersion The server version number
     * @return true if document should be fetched, false if cached version is current
     */
    public boolean shouldFetchDocument(Long documentId, Long serverVersion) {
        try {
            LocalDocument cached = storageManager.getDocument(documentId);
            if (cached == null) {
                return true; // Not cached, must fetch
            }

            // Fetch if server version is newer
            return serverVersion == null || cached.getServerVersion() == null ||
                   serverVersion > cached.getServerVersion();

        } catch (Exception e) {
            log.error("Error checking cached document", e);
            return true; // On error, fetch to be safe
        }
    }

    /**
     * Convert server DocumentResponse to LocalDocument.
     *
     * @param serverDoc The server document response
     * @return A LocalDocument with sync metadata
     */
    private LocalDocument convertToLocalDocument(RepositoryClient.DocumentResponse serverDoc) {
        return LocalDocument.builder()
            .serverId(serverDoc.getDocumentId())
            .regattaId(serverDoc.getRegattaId())
            .timerId(serverDoc.getTimerId())
            .milestoneId(serverDoc.getMilestoneId())
            .documentType(serverDoc.getType())
            .versionType(serverDoc.getVersionType())
            .author(serverDoc.getAuthor())
            .description(serverDoc.getDescription())
            .createdAt(serverDoc.getCreatedAt())
            .modifiedAt(LocalDateTime.now())
            .serverVersion(serverDoc.getLatestVersion())
            .localVersion(serverDoc.getLatestVersion())
            .syncStatus(org.rowtown.rms.rrr.client.model.SyncStatus.SYNCED)
            .lastSyncedAt(LocalDateTime.now())
            .modelData(serverDoc.getModelData())
            .serializationFormat("XMI")
            .build();
    }

    /**
     * Shutdown the sync executor.
     *
     * <p>Should be called when the sync manager is no longer needed.</p>
     */
    public void shutdown() {
        syncExecutor.shutdown();
        log.info("AutoSyncManager shutdown complete");
    }

    /**
     * Exception thrown when synchronization fails.
     */
    public static class SyncException extends RuntimeException {
        public SyncException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
