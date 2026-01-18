package org.rowtown.rms.rrr.client.sync;

import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;

/**
 * Example demonstrating how to use the event-driven document synchronization API.
 *
 * <p>This example shows:
 * <ul>
 *   <li>Setting up the synchronization service</li>
 *   <li>Subscribing to document events</li>
 *   <li>Handling different event types</li>
 *   <li>Error handling and recovery</li>
 * </ul>
 */
public class DocumentSynchronizationExample {

    public static void main(String[] args) throws Exception {
        // Configuration
        String serverUrl = "http://localhost:8080";
        String jwtToken = "your-jwt-token-here";
        String mqttBrokerUrl = "tcp://localhost:1883";
        String clientId = "timer001-client";
        String dbPath = "./timer-data.db";
        String regattaId = "CHARLES_REGATTA_2026";

        // Initialize components
        RepositoryClient repositoryClient = new RepositoryClient(serverUrl, jwtToken);
        LocalStorageManager storageManager = new LocalStorageManager(dbPath);

        // Create synchronization service
        DocumentSynchronizationService syncService = new DocumentSynchronizationService(
            repositoryClient,
            storageManager,
            mqttBrokerUrl,
            clientId
        );

        // Start the service (connects to MQTT broker)
        syncService.start();
        System.out.println("Synchronization service started");

        // Subscribe to regatta events with custom listener
        Subscription subscription = syncService.subscribe(regattaId, new DocumentEventListener() {

            @Override
            public void onDocumentCreated(DocumentEvent event) {
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("📄 NEW DOCUMENT CREATED");
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("Type: " + event.getDocumentType());
                System.out.println("ID: " + event.getDocumentId());
                System.out.println("Author: " + event.getAuthor());
                System.out.println("Version: " + event.getVersionNumber());

                if (event.isSyncSuccessful() && event.getDocument() != null) {
                    System.out.println("✓ Document synced and cached locally");
                    System.out.println("Local ID: " + event.getDocument().getLocalId());

                    // Update your UI or trigger other actions
                    if ("START_LIST".equals(event.getDocumentType())) {
                        System.out.println("→ Updating start list display...");
                        // updateStartListUI(event.getDocument());
                    }
                } else {
                    System.out.println("✗ Sync failed - will retry");
                }
                System.out.println();
            }

            @Override
            public void onDocumentUpdated(DocumentEvent event) {
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("📝 DOCUMENT UPDATED");
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("Type: " + event.getDocumentType());
                System.out.println("ID: " + event.getDocumentId());
                System.out.println("New Version: " + event.getVersionNumber());
                System.out.println("Change: " + event.getChangeDescription());
                System.out.println("Author: " + event.getAuthor());

                if (event.isSyncSuccessful() && event.getDocument() != null) {
                    System.out.println("✓ Latest version synced");

                    // Handle updates based on document type
                    if ("RACE_RESULTS".equals(event.getDocumentType())) {
                        System.out.println("→ Timer " + event.getTimerId() + " published new results");
                        System.out.println("→ Checking for conflicts...");
                        // checkForConflicts(event.getDocument());
                    }
                } else {
                    System.out.println("✗ Sync failed");
                }
                System.out.println();
            }

            @Override
            public void onDocumentDeleted(DocumentEvent event) {
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("🗑️  DOCUMENT DELETED");
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("Type: " + event.getDocumentType());
                System.out.println("ID: " + event.getDocumentId());
                System.out.println("→ Removing from local cache...");
                System.out.println();
            }

            @Override
            public void onSyncError(SyncError error) {
                System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.err.println("❌ SYNC ERROR");
                System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.err.println("Type: " + error.getErrorType());
                System.err.println("Message: " + error.getMessage());
                System.err.println("Document ID: " + error.getDocumentId());
                System.err.println("Retry Count: " + error.getRetryCount());
                System.err.println("Will Retry: " + error.isWillRetry());

                if (error.getCause() != null) {
                    System.err.println("Cause: " + error.getCause().getMessage());
                }

                // Handle different error types
                switch (error.getErrorType()) {
                    case MQTT_DISCONNECTED:
                        System.err.println("→ Working in offline mode...");
                        break;
                    case DOCUMENT_FETCH_FAILED:
                        System.err.println("→ Will fetch document when connection restored");
                        break;
                    case NETWORK_ERROR:
                        System.err.println("→ Check network connection");
                        break;
                    default:
                        System.err.println("→ Unknown error");
                }
                System.err.println();
            }

            @Override
            public void onConnected() {
                System.out.println("✓ Connected to notification service - receiving real-time updates");
            }

            @Override
            public void onDisconnected() {
                System.out.println("✗ Disconnected from notification service - working offline");
            }

            @Override
            public void onReconnected() {
                System.out.println("✓ Reconnected to notification service");
                System.out.println("→ Triggering manual sync to catch up on missed events...");
                // Optionally trigger a manual sync here to catch up
            }
        });

        System.out.println("✓ Subscribed to regatta: " + regattaId);
        System.out.println("Listening for document changes...");
        System.out.println();

        // Keep the application running to receive notifications
        // In a real application, this would be your main event loop
        System.out.println("Press Ctrl+C to stop...");
        Thread.sleep(Long.MAX_VALUE);

        // Cleanup (this would typically be in a shutdown hook)
        subscription.cancel();
        syncService.stop();
        storageManager.close();
        System.out.println("Shutdown complete");
    }
}
