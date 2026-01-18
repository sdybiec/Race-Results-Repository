package org.rowtown.rms.rrr.client.sync;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration options for document synchronization subscriptions.
 *
 * <p>This class encapsulates all the configuration options for a subscription,
 * including filters, conflict resolution, and sync behavior.
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Subscribe with selective sync for results only
 * SubscriptionOptions options = SubscriptionOptions.builder()
 *     .syncFilter(SyncFilter.resultsOnly())
 *     .autoSync(true)
 *     .build();
 *
 * Subscription sub = syncService.subscribe(regattaId, listener, options);
 *
 * // Subscribe with custom conflict resolution and event filtering
 * SubscriptionOptions options = SubscriptionOptions.builder()
 *     .syncFilter(SyncFilter.byTimerId("timer-1"))
 *     .eventFilter(event -> event.getVersionNumber() > 10)
 *     .conflictResolver(new ManualMergeResolver(this::promptUser))
 *     .autoSync(true)
 *     .initialSync(true)
 *     .build();
 * }</pre>
 */
@Data
@Builder
public class SubscriptionOptions {

    /**
     * Filter for selective synchronization.
     * Only events passing this filter will be synced from the server.
     * Default: accept all events.
     */
    @Builder.Default
    private SyncFilter syncFilter = SyncFilter.acceptAll();

    /**
     * Filter for event delivery.
     * Only events passing this filter will be delivered to the listener.
     * Note: Events are still synced to local storage even if filtered from listener.
     * Default: accept all events.
     */
    @Builder.Default
    private SyncFilter eventFilter = SyncFilter.acceptAll();

    /**
     * Conflict resolver for this subscription.
     * If null, uses the service's default resolver.
     * Default: null (use service default).
     */
    private ConflictResolver conflictResolver;

    /**
     * Whether to automatically fetch and cache documents when notifications arrive.
     * If false, only the notification metadata will be provided to listeners.
     * Default: true.
     */
    @Builder.Default
    private boolean autoSync = true;

    /**
     * Whether to perform an initial sync of all documents for this regatta
     * when the subscription is created.
     * Default: false.
     */
    @Builder.Default
    private boolean initialSync = false;

    /**
     * Whether to queue operations performed while offline.
     * If true, operations will be replayed when connection is restored.
     * Default: true.
     */
    @Builder.Default
    private boolean queueOfflineOperations = true;

    /**
     * Whether to notify listener for events that occurred while offline
     * during replay.
     * Default: false.
     */
    @Builder.Default
    private boolean notifyOnReplay = false;

    /**
     * Maximum number of retry attempts for failed sync operations.
     * Default: 3.
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * Whether to sync documents in the background even if no listener callbacks are triggered.
     * Useful for pre-loading data.
     * Default: true.
     */
    @Builder.Default
    private boolean backgroundSync = true;

    /**
     * Creates default subscription options with all defaults.
     */
    public static SubscriptionOptions defaults() {
        return SubscriptionOptions.builder().build();
    }

    /**
     * Creates options for start lists only.
     */
    public static SubscriptionOptions startListsOnly() {
        return SubscriptionOptions.builder()
                .syncFilter(SyncFilter.startListsOnly())
                .build();
    }

    /**
     * Creates options for results only.
     */
    public static SubscriptionOptions resultsOnly() {
        return SubscriptionOptions.builder()
                .syncFilter(SyncFilter.resultsOnly())
                .build();
    }

    /**
     * Creates options for a specific timer.
     */
    public static SubscriptionOptions forTimer(String timerId) {
        return SubscriptionOptions.builder()
                .syncFilter(SyncFilter.byTimerId(timerId))
                .build();
    }

    /**
     * Creates options with initial sync enabled.
     */
    public static SubscriptionOptions withInitialSync() {
        return SubscriptionOptions.builder()
                .initialSync(true)
                .build();
    }

    /**
     * Creates options for metadata-only (no auto-sync).
     */
    public static SubscriptionOptions metadataOnly() {
        return SubscriptionOptions.builder()
                .autoSync(false)
                .build();
    }
}
