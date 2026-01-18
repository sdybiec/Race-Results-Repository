package org.rowtown.rms.rrr.client.sync;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main service for managing event-driven document synchronization.
 *
 * <p>This service provides a high-level API for subscribing to document changes
 * and receiving automatic updates when documents are modified on the server.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Real-time notifications via MQTT</li>
 *   <li>Automatic document fetching and caching</li>
 *   <li>Offline-first design with local storage</li>
 *   <li>Event-driven callbacks for application integration</li>
 *   <li>Automatic reconnection and error handling</li>
 *   <li>Conflict resolution with multiple strategies (LastWriteWins, ManualMerge, Custom)</li>
 *   <li>Offline operation queue with automatic replay</li>
 *   <li>Selective sync and event filtering</li>
 * </ul>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * // Create the sync service with builder
 * DocumentSynchronizationService syncService = DocumentSynchronizationService.builder()
 *     .repositoryClient(repositoryClient)
 *     .storageManager(storageManager)
 *     .mqttBrokerUrl("tcp://repository.example.com:1883")
 *     .mqttClientId("timer001-client")
 *     .conflictResolver(new LastWriteWinsResolver())
 *     .enableOfflineQueue(true)
 *     .build();
 *
 * // Start the service
 * syncService.start();
 *
 * // Subscribe with options
 * SubscriptionOptions options = SubscriptionOptions.builder()
 *     .syncFilter(SyncFilter.resultsOnly())
 *     .autoSync(true)
 *     .build();
 *
 * Subscription subscription = syncService.subscribe("CHARLES_REGATTA_2026",
 *     new DocumentEventListener() {
 *         @Override
 *         public void onDocumentCreated(DocumentEvent event) {
 *             System.out.println("New document: " + event.getDocumentType());
 *             updateUI(event.getDocument());
 *         }
 *
 *         @Override
 *         public void onDocumentUpdated(DocumentEvent event) {
 *             System.out.println("Updated to version " + event.getVersionNumber());
 *             refreshDocument(event.getDocument());
 *         }
 *
 *         @Override
 *         public void onSyncError(SyncError error) {
 *             System.err.println("Sync error: " + error.getMessage());
 *         }
 *     },
 *     options
 * );
 *
 * // When done
 * subscription.cancel();
 * syncService.stop();
 * }</pre>
 */
@Slf4j
public class DocumentSynchronizationService {

    private final RepositoryClient repositoryClient;
    private final LocalStorageManager storageManager;
    private final MqttNotificationSubscriber mqttSubscriber;
    private final AutoSyncManager autoSyncManager;
    private final OfflineOperationQueue operationQueue;
    private final OperationReplayer operationReplayer;
    private final ConflictResolver defaultConflictResolver;
    private final ExecutorService eventExecutor;
    private final boolean offlineQueueEnabled;

    private boolean started = false;

    /**
     * Create a new document synchronization service.
     *
     * @param repositoryClient Client for fetching documents from the server
     * @param storageManager Manager for local document storage
     * @param mqttBrokerUrl MQTT broker URL (e.g., "tcp://localhost:1883")
     * @param mqttClientId Unique client ID for MQTT connection
     */
    public DocumentSynchronizationService(
            RepositoryClient repositoryClient,
            LocalStorageManager storageManager,
            String mqttBrokerUrl,
            String mqttClientId) {
        this(repositoryClient, storageManager, mqttBrokerUrl, mqttClientId,
             new LastWriteWinsResolver(), true);
    }

    /**
     * Create a new document synchronization service with custom configuration.
     *
     * @param repositoryClient Client for fetching documents from the server
     * @param storageManager Manager for local document storage
     * @param mqttBrokerUrl MQTT broker URL (e.g., "tcp://localhost:1883")
     * @param mqttClientId Unique client ID for MQTT connection
     * @param conflictResolver Strategy for resolving conflicts
     * @param enableOfflineQueue Whether to enable offline operation queue
     */
    public DocumentSynchronizationService(
            RepositoryClient repositoryClient,
            LocalStorageManager storageManager,
            String mqttBrokerUrl,
            String mqttClientId,
            ConflictResolver conflictResolver,
            boolean enableOfflineQueue) {

        this.repositoryClient = repositoryClient;
        this.storageManager = storageManager;
        this.mqttSubscriber = new MqttNotificationSubscriber(mqttBrokerUrl, mqttClientId);
        this.autoSyncManager = new AutoSyncManager(repositoryClient, storageManager);
        this.defaultConflictResolver = conflictResolver != null ? conflictResolver : new LastWriteWinsResolver();
        this.offlineQueueEnabled = enableOfflineQueue;

        if (enableOfflineQueue) {
            // Create queue database in same directory as main database
            String queueDbPath = "offline_operations.db";
            this.operationQueue = new OfflineOperationQueue(queueDbPath);
            this.operationReplayer = new OperationReplayer(
                repositoryClient, storageManager, operationQueue, defaultConflictResolver);
        } else {
            this.operationQueue = null;
            this.operationReplayer = null;
        }

        this.eventExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r);
            thread.setName("event-notifier-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Creates a builder for DocumentSynchronizationService.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DocumentSynchronizationService.
     */
    public static class Builder {
        private RepositoryClient repositoryClient;
        private LocalStorageManager storageManager;
        private String mqttBrokerUrl;
        private String mqttClientId;
        private ConflictResolver conflictResolver = new LastWriteWinsResolver();
        private boolean enableOfflineQueue = true;

        public Builder repositoryClient(RepositoryClient repositoryClient) {
            this.repositoryClient = repositoryClient;
            return this;
        }

        public Builder storageManager(LocalStorageManager storageManager) {
            this.storageManager = storageManager;
            return this;
        }

        public Builder mqttBrokerUrl(String mqttBrokerUrl) {
            this.mqttBrokerUrl = mqttBrokerUrl;
            return this;
        }

        public Builder mqttClientId(String mqttClientId) {
            this.mqttClientId = mqttClientId;
            return this;
        }

        public Builder conflictResolver(ConflictResolver conflictResolver) {
            this.conflictResolver = conflictResolver;
            return this;
        }

        public Builder enableOfflineQueue(boolean enable) {
            this.enableOfflineQueue = enable;
            return this;
        }

        public DocumentSynchronizationService build() {
            if (repositoryClient == null || storageManager == null ||
                mqttBrokerUrl == null || mqttClientId == null) {
                throw new IllegalStateException("Required fields not set");
            }
            return new DocumentSynchronizationService(
                repositoryClient, storageManager, mqttBrokerUrl, mqttClientId,
                conflictResolver, enableOfflineQueue);
        }
    }

    /**
     * Start the synchronization service.
     *
     * <p>Connects to the MQTT broker and initializes event listeners.
     * Must be called before subscribing to any regattas.</p>
     *
     * @throws MqttException if connection to MQTT broker fails
     */
    public void start() throws MqttException {
        if (started) {
            log.warn("Synchronization service already started");
            return;
        }

        log.info("Starting document synchronization service...");

        // Initialize offline operation queue if enabled
        if (offlineQueueEnabled && operationQueue != null) {
            try {
                operationQueue.initialize();
                int queueSize = operationQueue.getQueueSize();
                if (queueSize > 0) {
                    log.info("Found {} pending operations in queue", queueSize);
                }
            } catch (SQLException e) {
                log.error("Failed to initialize offline operation queue", e);
                throw new RuntimeException("Failed to initialize operation queue", e);
            }
        }

        // Connect to MQTT broker
        mqttSubscriber.connect();

        // Add connection listener for replay on reconnect
        if (offlineQueueEnabled && operationReplayer != null) {
            mqttSubscriber.addConnectionListener(new MqttNotificationSubscriber.ConnectionListener() {
                @Override
                public void onConnected() {
                    log.info("MQTT connected - replaying pending operations");
                    replayPendingOperations();
                }

                @Override
                public void onReconnected() {
                    log.info("MQTT reconnected - replaying pending operations");
                    replayPendingOperations();
                }

                @Override
                public void onDisconnected() {
                    log.info("MQTT disconnected");
                }
            });
        }

        started = true;
        log.info("Document synchronization service started successfully");

        // Replay any pending operations from previous sessions
        if (offlineQueueEnabled) {
            replayPendingOperations();
        }
    }

    /**
     * Replays pending operations from the offline queue.
     */
    private void replayPendingOperations() {
        if (operationReplayer == null) {
            return;
        }

        operationReplayer.replayPendingOperations()
            .thenAccept(result -> {
                if (result.getTotalCount() > 0) {
                    log.info("Replay completed: {} succeeded, {} failed, {} conflicts",
                        result.getSuccessCount(),
                        result.getFailureCount(),
                        result.getConflictCount());
                }
            })
            .exceptionally(error -> {
                log.error("Failed to replay pending operations", error);
                return null;
            });
    }

    /**
     * Stop the synchronization service.
     *
     * <p>Disconnects from MQTT broker and shuts down background threads.
     * After stopping, the service can be restarted by calling {@link #start()} again.</p>
     */
    public void stop() {
        if (!started) {
            log.warn("Synchronization service not started");
            return;
        }

        log.info("Stopping document synchronization service...");
        mqttSubscriber.disconnect();
        autoSyncManager.shutdown();

        if (operationReplayer != null) {
            operationReplayer.shutdown();
        }

        if (operationQueue != null) {
            operationQueue.close();
        }

        eventExecutor.shutdown();
        started = false;
        log.info("Document synchronization service stopped");
    }

    /**
     * Check if the service is running.
     *
     * @return true if started and connected, false otherwise
     */
    public boolean isRunning() {
        return started && mqttSubscriber.isConnected();
    }

    /**
     * Subscribe to document events for a specific regatta with default options.
     *
     * <p>The listener will be notified of all document changes in real-time.
     * Documents are automatically fetched from the server and cached locally
     * before the listener is notified.</p>
     *
     * @param regattaId The regatta ID to monitor
     * @param listener Callback for document events
     * @return A subscription that can be cancelled
     * @throws MqttException if subscription fails
     * @throws IllegalStateException if service not started
     */
    public Subscription subscribe(String regattaId, DocumentEventListener listener) throws MqttException {
        return subscribe(regattaId, listener, SubscriptionOptions.defaults());
    }

    /**
     * Subscribe to document events for a specific regatta with custom options.
     *
     * <p>The listener will be notified of document changes that pass the configured filters.
     * Documents are automatically fetched from the server and cached locally
     * before the listener is notified (if autoSync is enabled).</p>
     *
     * @param regattaId The regatta ID to monitor
     * @param listener Callback for document events
     * @param options Subscription configuration options
     * @return A subscription that can be cancelled
     * @throws MqttException if subscription fails
     * @throws IllegalStateException if service not started
     */
    public Subscription subscribe(String regattaId, DocumentEventListener listener, SubscriptionOptions options)
            throws MqttException {
        if (!started) {
            throw new IllegalStateException("Service not started. Call start() first.");
        }

        log.info("Subscribing to regatta: {} with options", regattaId);

        // Determine which conflict resolver to use
        ConflictResolver resolver = options.getConflictResolver() != null
            ? options.getConflictResolver()
            : defaultConflictResolver;

        // Wrap the listener to add auto-sync, filtering, and conflict resolution
        DocumentEventListener syncListener = new SyncingListener(
            listener, regattaId, options, resolver);

        // Subscribe to MQTT notifications
        return mqttSubscriber.subscribe(regattaId, syncListener);
    }

    /**
     * Subscribe to a specific document (more granular than regatta-level).
     *
     * <p>Note: Currently not implemented - use regatta-level subscription and
     * filter events by documentId in your listener.</p>
     *
     * @param documentId The document ID to monitor
     * @param listener Callback for document events
     * @return A subscription that can be cancelled
     * @throws UnsupportedOperationException always (not yet implemented)
     */
    public Subscription subscribeToDocument(Long documentId, DocumentEventListener listener) {
        throw new UnsupportedOperationException(
            "Document-level subscriptions not yet implemented. " +
            "Use regatta-level subscription and filter by documentId."
        );
    }

    /**
     * Queues an offline operation for later replay.
     */
    public void queueOfflineOperation(PendingOperation operation) {
        if (!offlineQueueEnabled || operationQueue == null) {
            log.warn("Offline queue not enabled, operation will be lost");
            return;
        }

        try {
            operationQueue.enqueue(operation);
            log.info("Queued offline operation: {}", operation.getOperationType());
        } catch (SQLException e) {
            log.error("Failed to queue offline operation", e);
        }
    }

    /**
     * Gets the current size of the offline operation queue.
     */
    public int getQueueSize() {
        if (!offlineQueueEnabled || operationQueue == null) {
            return 0;
        }

        try {
            return operationQueue.getQueueSize();
        } catch (SQLException e) {
            log.error("Failed to get queue size", e);
            return -1;
        }
    }

    /**
     * Wrapper listener that adds automatic synchronization, filtering, and conflict resolution.
     */
    private class SyncingListener implements DocumentEventListener {
        private final DocumentEventListener delegate;
        private final String regattaId;
        private final SubscriptionOptions options;
        private final ConflictResolver conflictResolver;

        public SyncingListener(DocumentEventListener delegate, String regattaId,
                             SubscriptionOptions options, ConflictResolver conflictResolver) {
            this.delegate = delegate;
            this.regattaId = regattaId;
            this.options = options;
            this.conflictResolver = conflictResolver;
        }

        @Override
        public void onDocumentCreated(DocumentEvent event) {
            if (!shouldProcess(event)) {
                return;
            }

            if (options.isAutoSync()) {
                syncAndNotify(event, delegate::onDocumentCreated);
            } else {
                notifyListener(event, delegate::onDocumentCreated);
            }
        }

        @Override
        public void onDocumentUpdated(DocumentEvent event) {
            if (!shouldProcess(event)) {
                return;
            }

            if (options.isAutoSync()) {
                syncAndNotify(event, delegate::onDocumentUpdated);
            } else {
                notifyListener(event, delegate::onDocumentUpdated);
            }
        }

        @Override
        public void onDocumentDeleted(DocumentEvent event) {
            if (!shouldProcess(event)) {
                return;
            }

            // No need to fetch for delete events
            notifyListener(event, delegate::onDocumentDeleted);
        }

        @Override
        public void onSyncError(SyncError error) {
            eventExecutor.submit(() -> {
                try {
                    delegate.onSyncError(error);
                } catch (Exception e) {
                    log.error("Error in listener callback", e);
                }
            });
        }

        @Override
        public void onConnected() {
            delegate.onConnected();
        }

        @Override
        public void onDisconnected() {
            delegate.onDisconnected();
        }

        @Override
        public void onReconnected() {
            delegate.onReconnected();
        }

        /**
         * Checks if event should be processed based on filters.
         */
        private boolean shouldProcess(DocumentEvent event) {
            // Apply sync filter
            if (!options.getSyncFilter().shouldSync(event)) {
                log.debug("Event filtered out by sync filter: {}", event.getDocumentId());
                return false;
            }

            // Apply event filter
            if (!options.getEventFilter().shouldSync(event)) {
                log.debug("Event filtered out by event filter: {}", event.getDocumentId());
                // Still sync to local storage if autoSync is enabled, just don't notify listener
                if (options.isAutoSync() && options.isBackgroundSync()) {
                    syncToLocalStorage(event);
                }
                return false;
            }

            return true;
        }

        /**
         * Syncs to local storage without notifying listener.
         */
        private void syncToLocalStorage(DocumentEvent event) {
            autoSyncManager.syncDocument(event.getDocumentId(), event.getVersionNumber())
                .exceptionally(error -> {
                    log.warn("Background sync failed for document {}", event.getDocumentId());
                    return null;
                });
        }

        /**
         * Notifies listener without syncing.
         */
        private void notifyListener(DocumentEvent event, java.util.function.Consumer<DocumentEvent> callback) {
            eventExecutor.submit(() -> {
                try {
                    callback.accept(event);
                } catch (Exception e) {
                    log.error("Error in listener callback", e);
                }
            });
        }

        /**
         * Sync document and notify listener.
         */
        private void syncAndNotify(DocumentEvent event, java.util.function.Consumer<DocumentEvent> callback) {
            autoSyncManager.syncDocument(event.getDocumentId(), event.getVersionNumber())
                .thenAccept(document -> {
                    // Update event with synchronized document
                    event.setDocument(document);
                    event.setSyncSuccessful(true);

                    // Notify listener on event executor thread
                    eventExecutor.submit(() -> {
                        try {
                            callback.accept(event);
                        } catch (Exception e) {
                            log.error("Error in listener callback", e);
                        }
                    });
                })
                .exceptionally(error -> {
                    log.error("Failed to sync document {}", event.getDocumentId(), error);

                    // Mark sync as failed
                    event.setSyncSuccessful(false);

                    // Create sync error
                    SyncError syncError = SyncError.builder()
                        .errorType(SyncError.ErrorType.DOCUMENT_FETCH_FAILED)
                        .message("Failed to fetch document " + event.getDocumentId())
                        .documentId(event.getDocumentId())
                        .regattaId(regattaId)
                        .cause(error)
                        .timestamp(LocalDateTime.now())
                        .retryCount(0)
                        .willRetry(false)
                        .build();

                    // Notify listener of error
                    onSyncError(syncError);

                    return null;
                });
        }
    }
}
