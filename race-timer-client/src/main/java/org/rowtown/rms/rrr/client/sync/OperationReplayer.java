package org.rowtown.rms.rrr.client.sync;

import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replays offline operations when connection is restored.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Retrieving pending operations from the queue</li>
 *   <li>Replaying them to the server in order</li>
 *   <li>Detecting conflicts with server state</li>
 *   <li>Resolving conflicts using the configured strategy</li>
 *   <li>Updating the queue with results</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe and uses an executor for async replay.
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * OperationReplayer replayer = new OperationReplayer(
 *     repositoryClient,
 *     storageManager,
 *     operationQueue,
 *     conflictResolver
 * );
 *
 * // Replay all pending operations
 * replayer.replayPendingOperations()
 *     .thenAccept(result -> {
 *         System.out.println("Replayed: " + result.getSuccessCount());
 *         System.out.println("Failed: " + result.getFailureCount());
 *         System.out.println("Conflicts: " + result.getConflictCount());
 *     });
 * }</pre>
 */
@Slf4j
public class OperationReplayer {

    private final RepositoryClient repositoryClient;
    private final LocalStorageManager storageManager;
    private final OfflineOperationQueue operationQueue;
    private final ConflictResolver conflictResolver;
    private final ExecutorService replayExecutor;

    /**
     * Creates an operation replayer.
     *
     * @param repositoryClient client for server API calls
     * @param storageManager local storage manager
     * @param operationQueue offline operation queue
     * @param conflictResolver conflict resolution strategy
     */
    public OperationReplayer(
            RepositoryClient repositoryClient,
            LocalStorageManager storageManager,
            OfflineOperationQueue operationQueue,
            ConflictResolver conflictResolver) {
        this.repositoryClient = repositoryClient;
        this.storageManager = storageManager;
        this.operationQueue = operationQueue;
        this.conflictResolver = conflictResolver;
        this.replayExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Replays all pending operations from the queue.
     *
     * @return a future that completes with the replay result
     */
    public CompletableFuture<ReplayResult> replayPendingOperations() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting replay of pending operations");

                List<PendingOperation> pending = operationQueue.getPendingOperations();
                if (pending.isEmpty()) {
                    log.info("No pending operations to replay");
                    return new ReplayResult(0, 0, 0);
                }

                log.info("Found {} pending operations to replay", pending.size());

                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger failureCount = new AtomicInteger(0);
                AtomicInteger conflictCount = new AtomicInteger(0);

                for (PendingOperation operation : pending) {
                    try {
                        operationQueue.markReplaying(operation.getOperationId());
                        replayOperation(operation);
                        operationQueue.markSucceeded(operation.getOperationId());
                        successCount.incrementAndGet();
                    } catch (ConflictException e) {
                        log.warn("Conflict during replay of operation {}: {}",
                                operation.getOperationId(), e.getMessage());
                        handleConflict(operation, e.getConflict());
                        conflictCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("Failed to replay operation {}: {}",
                                operation.getOperationId(), e.getMessage());
                        operationQueue.markFailed(operation.getOperationId(), e.getMessage());
                        failureCount.incrementAndGet();
                    }
                }

                ReplayResult result = new ReplayResult(
                        successCount.get(),
                        failureCount.get(),
                        conflictCount.get());

                log.info("Replay completed: {} succeeded, {} failed, {} conflicts",
                        result.getSuccessCount(),
                        result.getFailureCount(),
                        result.getConflictCount());

                return result;
            } catch (SQLException e) {
                log.error("Database error during replay", e);
                throw new RuntimeException("Failed to replay operations", e);
            }
        }, replayExecutor);
    }

    /**
     * Replays a single operation to the server.
     *
     * @param operation the operation to replay
     * @throws IOException if server communication fails
     * @throws ConflictException if the operation conflicts with server state
     */
    private void replayOperation(PendingOperation operation) throws IOException, ConflictException {
        log.debug("Replaying {} operation for document {}",
                operation.getOperationType(), operation.getDocumentId());

        switch (operation.getOperationType()) {
            case CREATE:
                replayCreate(operation);
                break;
            case UPDATE:
            case UPLOAD:
                replayUpdate(operation);
                break;
            case DELETE:
                replayDelete(operation);
                break;
            default:
                throw new IllegalArgumentException("Unknown operation type: " + operation.getOperationType());
        }
    }

    private void replayCreate(PendingOperation operation) throws IOException, ConflictException {
        LocalDocument document = operation.getDocument();

        // Check if document already exists on server (might have been synced by another client)
        try {
            var serverDoc = repositoryClient.getDocument(document.getServerId());
            if (serverDoc != null) {
                log.warn("Document {} already exists on server, treating as update",
                        document.getServerId());
                replayUpdate(operation);
                return;
            }
        } catch (IOException e) {
            // Document doesn't exist - proceed with create
        }

        // Upload document to server
        var response = repositoryClient.createDocument(document);
        log.info("Created document {} on server", response.getDocumentId());
    }

    private void replayUpdate(PendingOperation operation) throws IOException, ConflictException {
        LocalDocument localDocument = operation.getDocument();

        // Fetch current server version
        var serverResponse = repositoryClient.getDocument(localDocument.getServerId());
        LocalDocument serverDocument = convertToLocalDocument(serverResponse);

        // Check for conflicts
        if (hasConflict(localDocument, serverDocument)) {
            log.warn("Detected conflict for document {} (local v{}, server v{})",
                    localDocument.getServerId(),
                    localDocument.getServerVersion(),
                    serverDocument.getServerVersion());

            // Create conflict object
            Conflict conflict = Conflict.builder()
                    .documentId(localDocument.getServerId())
                    .documentType(localDocument.getDocumentType())
                    .regattaId(localDocument.getRegattaId())
                    .timerId(localDocument.getTimerId())
                    .localVersion(localDocument)
                    .serverVersion(serverDocument)
                    .detectedAt(LocalDateTime.now())
                    .trigger(Conflict.ConflictTrigger.OFFLINE_REPLAY)
                    .description("Local modification conflicts with server version during offline replay")
                    .build();

            throw new ConflictException(conflict);
        }

        // No conflict - upload local version
        var response = repositoryClient.updateDocument(localDocument.getServerId(), localDocument);
        log.info("Updated document {} on server (version {})",
                response.getDocumentId(), response.getVersion());
    }

    private void replayDelete(PendingOperation operation) throws IOException {
        repositoryClient.deleteDocument(operation.getDocumentId());
        log.info("Deleted document {} on server", operation.getDocumentId());
    }

    /**
     * Handles a conflict by using the configured conflict resolver.
     *
     * @param operation the operation that conflicted
     * @param conflict the detected conflict
     */
    private void handleConflict(PendingOperation operation, Conflict conflict) {
        try {
            log.info("Resolving conflict for operation {} using {}",
                    operation.getOperationId(),
                    conflictResolver.getClass().getSimpleName());

            ConflictResolution resolution = conflictResolver.resolve(conflict).join();

            // Apply resolution
            if (resolution.isRequiresLocalUpdate()) {
                storageManager.saveDocument(resolution.getResolvedDocument());
            }

            if (resolution.isRequiresServerUpdate()) {
                repositoryClient.updateDocument(
                        resolution.getResolvedDocument().getServerId(),
                        resolution.getResolvedDocument());
            }

            // Mark operation as succeeded
            operationQueue.markSucceeded(operation.getOperationId());

            log.info("Conflict resolved for operation {}: {}",
                    operation.getOperationId(),
                    resolution.getResolutionNotes());

        } catch (Exception e) {
            log.error("Failed to resolve conflict for operation {}: {}",
                    operation.getOperationId(), e.getMessage());
            try {
                operationQueue.markConflicted(operation.getOperationId(),
                        "Conflict resolution failed: " + e.getMessage());
            } catch (SQLException sqlEx) {
                log.error("Failed to mark operation as conflicted", sqlEx);
            }
        }
    }

    /**
     * Checks if there's a conflict between local and server versions.
     */
    private boolean hasConflict(LocalDocument local, LocalDocument server) {
        // Conflict if server version is newer than what local was based on
        if (server.getServerVersion() > local.getServerVersion()) {
            return true;
        }

        // Additional conflict detection based on timestamps
        if (local.getLastModified() != null && server.getLastModified() != null) {
            return !local.getLastModified().equals(server.getLastModified());
        }

        return false;
    }

    /**
     * Converts API response to LocalDocument (placeholder - implement based on your API)
     */
    private LocalDocument convertToLocalDocument(Object serverResponse) {
        // TODO: Implement based on your RepositoryClient response type
        // This is a placeholder
        return new LocalDocument();
    }

    /**
     * Shuts down the replay executor.
     */
    public void shutdown() {
        replayExecutor.shutdown();
        log.info("Operation replayer shut down");
    }

    /**
     * Result of a replay operation.
     */
    public static class ReplayResult {
        private final int successCount;
        private final int failureCount;
        private final int conflictCount;

        public ReplayResult(int successCount, int failureCount, int conflictCount) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.conflictCount = conflictCount;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public int getConflictCount() {
            return conflictCount;
        }

        public int getTotalCount() {
            return successCount + failureCount + conflictCount;
        }
    }

    /**
     * Exception thrown when a conflict is detected during replay.
     */
    public static class ConflictException extends Exception {
        private final Conflict conflict;

        public ConflictException(Conflict conflict) {
            super("Operation conflicts with server state");
            this.conflict = conflict;
        }

        public Conflict getConflict() {
            return conflict;
        }
    }
}
