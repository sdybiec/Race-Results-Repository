package org.rowtown.rms.rrr.client.sync;

import org.rowtown.rms.rrr.client.api.RepositoryClient;
import org.rowtown.rms.rrr.client.model.LocalDocument;
import org.rowtown.rms.rrr.client.storage.LocalStorageManager;
import org.rowtown.rms.rrr.client.sync.conflict.*;
import org.rowtown.rms.rrr.client.sync.filter.SyncFilter;
import org.rowtown.rms.rrr.client.sync.filter.SubscriptionOptions;
import org.rowtown.rms.rrr.client.sync.queue.PendingOperation;

import java.util.concurrent.CompletableFuture;

/**
 * Comprehensive example demonstrating advanced synchronization features:
 * - Conflict resolution strategies
 * - Offline operations queue
 * - Selective sync
 * - Event filtering
 */
public class AdvancedSyncExample {

    public static void main(String[] args) throws Exception {
        // Setup
        String serverUrl = "https://repository.example.com";
        String jwtToken = "your-jwt-token";
        String mqttBrokerUrl = "tcp://repository.example.com:1883";
        String clientId = "timer-client-001";
        String dbPath = "./local_storage.db";
        String regattaId = "CHARLES_REGATTA_2026";

        RepositoryClient repositoryClient = new RepositoryClient(serverUrl, jwtToken);
        LocalStorageManager storageManager = new LocalStorageManager(dbPath);

        // ===== EXAMPLE 1: LastWriteWins Conflict Resolution =====
        System.out.println("\n===== Example 1: LastWriteWins Conflict Resolution =====");

        DocumentSynchronizationService lwwService = DocumentSynchronizationService.builder()
            .repositoryClient(repositoryClient)
            .storageManager(storageManager)
            .mqttBrokerUrl(mqttBrokerUrl)
            .mqttClientId(clientId)
            .conflictResolver(new LastWriteWinsResolver())
            .enableOfflineQueue(true)
            .build();

        lwwService.start();

        Subscription lwwSubscription = lwwService.subscribe(regattaId, new DocumentEventListener() {
            @Override
            public void onDocumentCreated(DocumentEvent event) {
                System.out.println("✓ Document created: " + event.getDocumentType());
            }

            @Override
            public void onDocumentUpdated(DocumentEvent event) {
                System.out.println("✓ Document updated (version " + event.getVersionNumber() + ")");
                // Conflicts automatically resolved using timestamp comparison
            }

            @Override
            public void onDocumentDeleted(DocumentEvent event) {
                System.out.println("✓ Document deleted: " + event.getDocumentId());
            }

            @Override
            public void onSyncError(SyncError error) {
                System.err.println("✗ Sync error: " + error.getMessage());
            }
        });

        Thread.sleep(5000); // Let it run for a bit
        lwwSubscription.cancel();
        lwwService.stop();

        // ===== EXAMPLE 2: Manual Conflict Resolution =====
        System.out.println("\n===== Example 2: Manual Conflict Resolution =====");

        ManualMergeResolver manualResolver = new ManualMergeResolver(conflict -> {
            // Prompt user to resolve conflict
            System.out.println("\n⚠ CONFLICT DETECTED:");
            System.out.println("Document ID: " + conflict.getDocumentId());
            System.out.println("Local version: " + conflict.getLocalVersion().getServerVersion());
            System.out.println("Server version: " + conflict.getServerVersion().getServerVersion());

            // In a real app, show a dialog to the user
            // For this example, automatically choose server version
            return CompletableFuture.completedFuture(conflict.getServerVersion());
        });

        DocumentSynchronizationService manualService = DocumentSynchronizationService.builder()
            .repositoryClient(repositoryClient)
            .storageManager(storageManager)
            .mqttBrokerUrl(mqttBrokerUrl)
            .mqttClientId(clientId + "-manual")
            .conflictResolver(manualResolver)
            .enableOfflineQueue(true)
            .build();

        manualService.start();
        Thread.sleep(5000);
        manualService.stop();

        // ===== EXAMPLE 3: Custom Conflict Resolution =====
        System.out.println("\n===== Example 3: Custom Conflict Resolution =====");

        CustomConflictResolver customResolver = new CustomConflictResolver(conflict -> {
            // Merge specific fields based on business rules
            LocalDocument merged = new LocalDocument();

            // For start lists: prefer server version for entry data
            // For results: prefer local version for timer data
            if ("STARTLIST".equals(conflict.getDocumentType())) {
                // Use server version as base
                merged = conflict.getServerVersion();
                System.out.println("→ Using server version for start list");
            } else if ("RESULTS".equals(conflict.getDocumentType())) {
                // Use local version for results (timer is authoritative)
                merged = conflict.getLocalVersion();
                System.out.println("→ Using local version for results (timer is authoritative)");
            } else {
                // Default to server version
                merged = conflict.getServerVersion();
            }

            return CompletableFuture.completedFuture(
                new CustomConflictResolver.MergeResult(merged, true, "Custom business rule merge")
            );
        });

        DocumentSynchronizationService customService = DocumentSynchronizationService.builder()
            .repositoryClient(repositoryClient)
            .storageManager(storageManager)
            .mqttBrokerUrl(mqttBrokerUrl)
            .mqttClientId(clientId + "-custom")
            .conflictResolver(customResolver)
            .enableOfflineQueue(true)
            .build();

        customService.start();
        Thread.sleep(5000);
        customService.stop();

        // ===== EXAMPLE 4: Selective Sync - Results Only =====
        System.out.println("\n===== Example 4: Selective Sync - Results Only =====");

        DocumentSynchronizationService resultsService = DocumentSynchronizationService.builder()
            .repositoryClient(repositoryClient)
            .storageManager(storageManager)
            .mqttBrokerUrl(mqttBrokerUrl)
            .mqttClientId(clientId + "-results")
            .conflictResolver(new LastWriteWinsResolver())
            .enableOfflineQueue(true)
            .build();

        resultsService.start();

        // Subscribe with filter for results only
        SubscriptionOptions resultsOnly = SubscriptionOptions.builder()
            .syncFilter(SyncFilter.resultsOnly())
            .autoSync(true)
            .build();

        Subscription resultsSubscription = resultsService.subscribe(regattaId,
            new DocumentEventListener() {
                @Override
                public void onDocumentCreated(DocumentEvent event) {
                    System.out.println("✓ New result created: " + event.getDocumentId());
                    // Only results will trigger this
                }

                @Override
                public void onDocumentUpdated(DocumentEvent event) {
                    System.out.println("✓ Result updated: " + event.getDocumentId());
                }

                @Override
                public void onDocumentDeleted(DocumentEvent event) {
                    System.out.println("✓ Result deleted: " + event.getDocumentId());
                }

                @Override
                public void onSyncError(SyncError error) {
                    System.err.println("✗ Error: " + error.getMessage());
                }
            },
            resultsOnly
        );

        Thread.sleep(5000);
        resultsSubscription.cancel();
        resultsService.stop();

        // ===== EXAMPLE 5: Event Filtering =====
        System.out.println("\n===== Example 5: Event Filtering =====");

        DocumentSynchronizationService filteredService = DocumentSynchronizationService.builder()
            .repositoryClient(repositoryClient)
            .storageManager(storageManager)
            .mqttBrokerUrl(mqttBrokerUrl)
            .mqttClientId(clientId + "-filtered")
            .conflictResolver(new LastWriteWinsResolver())
            .enableOfflineQueue(true)
            .build();

        filteredService.start();

        // Only notify for updates after version 10, excluding deletes
        SubscriptionOptions filteredOptions = SubscriptionOptions.builder()
            .syncFilter(SyncFilter.acceptAll()) // Sync everything
            .eventFilter(
                SyncFilter.byMinVersion(10)
                    .and(SyncFilter.excludeDeletes())
            )
            .autoSync(true)
            .build();

        Subscription filteredSubscription = filteredService.subscribe(regattaId,
            new DocumentEventListener() {
                @Override
                public void onDocumentCreated(DocumentEvent event) {
                    System.out.println("✓ High-version document created: v" + event.getVersionNumber());
                }

                @Override
                public void onDocumentUpdated(DocumentEvent event) {
                    System.out.println("✓ High-version update: v" + event.getVersionNumber());
                    // Only versions >= 10 will trigger this
                }

                @Override
                public void onDocumentDeleted(DocumentEvent event) {
                    // This will never be called due to event filter
                }

                @Override
                public void onSyncError(SyncError error) {
                    System.err.println("✗ Error: " + error.getMessage());
                }
            },
            filteredOptions
        );

        Thread.sleep(5000);
        filteredSubscription.cancel();
        filteredService.stop();

        // ===== EXAMPLE 6: Combined Filters =====
        System.out.println("\n===== Example 6: Combined Filters =====");

        DocumentSynchronizationService combinedService = DocumentSynchronizationService.builder()
            .repositoryClient(repositoryClient)
            .storageManager(storageManager)
            .mqttBrokerUrl(mqttBrokerUrl)
            .mqttClientId(clientId + "-combined")
            .conflictResolver(new LastWriteWinsResolver())
            .enableOfflineQueue(true)
            .build();

        combinedService.start();

        // Sync results for a specific timer, but only notify for creates
        SubscriptionOptions combinedOptions = SubscriptionOptions.builder()
            .syncFilter(
                SyncFilter.resultsOnly()
                    .and(SyncFilter.byTimerId("timer-1"))
            )
            .eventFilter(SyncFilter.createsOnly())
            .autoSync(true)
            .backgroundSync(true) // Keep syncing updates in background
            .build();

        Subscription combinedSubscription = combinedService.subscribe(regattaId,
            new DocumentEventListener() {
                @Override
                public void onDocumentCreated(DocumentEvent event) {
                    System.out.println("✓ New result for timer-1: " + event.getDocumentId());
                    // Only new results for timer-1
                }

                @Override
                public void onDocumentUpdated(DocumentEvent event) {
                    // Updates are synced to storage but listener not notified
                }

                @Override
                public void onDocumentDeleted(DocumentEvent event) {
                    // Not notified
                }

                @Override
                public void onSyncError(SyncError error) {
                    System.err.println("✗ Error: " + error.getMessage());
                }
            },
            combinedOptions
        );

        Thread.sleep(5000);
        combinedSubscription.cancel();
        combinedService.stop();

        // ===== EXAMPLE 7: Offline Operations Queue =====
        System.out.println("\n===== Example 7: Offline Operations Queue =====");

        DocumentSynchronizationService offlineService = DocumentSynchronizationService.builder()
            .repositoryClient(repositoryClient)
            .storageManager(storageManager)
            .mqttBrokerUrl(mqttBrokerUrl)
            .mqttClientId(clientId + "-offline")
            .conflictResolver(new LastWriteWinsResolver())
            .enableOfflineQueue(true)
            .build();

        offlineService.start();

        // Check queue size
        int queueSize = offlineService.getQueueSize();
        System.out.println("Pending operations in queue: " + queueSize);

        // Queue an offline operation
        LocalDocument modifiedDoc = new LocalDocument();
        modifiedDoc.setServerId(12345L);
        modifiedDoc.setRegattaId(regattaId);
        modifiedDoc.setDocumentType("RESULTS");

        PendingOperation offlineUpdate = PendingOperation.builder()
            .operationType(PendingOperation.OperationType.UPDATE)
            .document(modifiedDoc)
            .documentId(modifiedDoc.getServerId())
            .regattaId(regattaId)
            .performedAt(java.time.LocalDateTime.now())
            .status(PendingOperation.OperationStatus.PENDING)
            .build();

        offlineService.queueOfflineOperation(offlineUpdate);
        System.out.println("✓ Queued offline update for document " + modifiedDoc.getServerId());

        // When connection is restored, operations are automatically replayed
        Thread.sleep(5000);
        offlineService.stop();

        // ===== EXAMPLE 8: Type-Based Custom Resolution =====
        System.out.println("\n===== Example 8: Type-Based Custom Resolution =====");

        CustomConflictResolver typeBasedResolver = CustomConflictResolver.typeBasedResolver(documentType -> {
            switch (documentType) {
                case "STARTLIST":
                    // Always prefer server version for start lists
                    return conflict -> CompletableFuture.completedFuture(
                        ConflictResolution.serverWins(conflict)
                    );
                case "RESULTS":
                    // Always prefer local version for results (timer is authoritative)
                    return conflict -> CompletableFuture.completedFuture(
                        ConflictResolution.localWins(conflict)
                    );
                default:
                    // Use timestamp for other types
                    return new LastWriteWinsResolver();
            }
        });

        DocumentSynchronizationService typeBasedService = DocumentSynchronizationService.builder()
            .repositoryClient(repositoryClient)
            .storageManager(storageManager)
            .mqttBrokerUrl(mqttBrokerUrl)
            .mqttClientId(clientId + "-typebased")
            .conflictResolver(typeBasedResolver)
            .enableOfflineQueue(true)
            .build();

        typeBasedService.start();
        System.out.println("✓ Using type-based conflict resolution:");
        System.out.println("  - STARTLIST: server wins");
        System.out.println("  - RESULTS: local wins");
        System.out.println("  - Others: last write wins");

        Thread.sleep(5000);
        typeBasedService.stop();

        System.out.println("\n===== All Examples Completed =====");
    }
}
