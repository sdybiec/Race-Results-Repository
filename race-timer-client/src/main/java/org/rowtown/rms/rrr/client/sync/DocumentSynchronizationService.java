package org.rowtown.rms.rrr.client.sync;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;

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
 * </ul>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * // Create the sync service
 * DocumentSynchronizationService syncService = new DocumentSynchronizationService(
 *     repositoryClient,
 *     storageManager,
 *     "tcp://repository.example.com:1883",
 *     "timer001-client"
 * );
 *
 * // Start the service
 * syncService.start();
 *
 * // Subscribe to a regatta
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
 *     }
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
    private final ExecutorService eventExecutor;

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

        this.repositoryClient = repositoryClient;
        this.storageManager = storageManager;
        this.mqttSubscriber = new MqttNotificationSubscriber(mqttBrokerUrl, mqttClientId);
        this.autoSyncManager = new AutoSyncManager(repositoryClient, storageManager);
        this.eventExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r);
            thread.setName("event-notifier-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
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
        mqttSubscriber.connect();
        started = true;
        log.info("Document synchronization service started successfully");
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
     * Subscribe to document events for a specific regatta.
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
        if (!started) {
            throw new IllegalStateException("Service not started. Call start() first.");
        }

        log.info("Subscribing to regatta: {}", regattaId);

        // Wrap the listener to add auto-sync behavior
        DocumentEventListener syncListener = new SyncingListener(listener, regattaId);

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
     * Wrapper listener that adds automatic synchronization behavior.
     */
    private class SyncingListener implements DocumentEventListener {
        private final DocumentEventListener delegate;
        private final String regattaId;

        public SyncingListener(DocumentEventListener delegate, String regattaId) {
            this.delegate = delegate;
            this.regattaId = regattaId;
        }

        @Override
        public void onDocumentCreated(DocumentEvent event) {
            // Async fetch and cache the document, then notify
            syncAndNotify(event, delegate::onDocumentCreated);
        }

        @Override
        public void onDocumentUpdated(DocumentEvent event) {
            // Async fetch and cache the document, then notify
            syncAndNotify(event, delegate::onDocumentUpdated);
        }

        @Override
        public void onDocumentDeleted(DocumentEvent event) {
            // No need to fetch for delete events
            eventExecutor.submit(() -> {
                try {
                    delegate.onDocumentDeleted(event);
                } catch (Exception e) {
                    log.error("Error in listener callback", e);
                }
            });
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
