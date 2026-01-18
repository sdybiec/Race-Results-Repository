package org.rowtown.rms.rrr.client.sync;

/**
 * Listener for document synchronization events.
 *
 * <p>Implement this interface to receive real-time notifications about document changes
 * in the repository. When a notification is received, the document is automatically
 * fetched from the server and cached locally before the listener methods are called.</p>
 *
 * <p>All listener methods are called on a background thread, so implementations should
 * be thread-safe if they modify shared state.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * syncService.subscribe("CHARLES_REGATTA_2026", new DocumentEventListener() {
 *     @Override
 *     public void onDocumentCreated(DocumentEvent event) {
 *         System.out.println("New document: " + event.getDocumentType());
 *         updateUI(event.getDocument());
 *     }
 *
 *     @Override
 *     public void onDocumentUpdated(DocumentEvent event) {
 *         System.out.println("Document updated to version " + event.getVersionNumber());
 *         refreshDocument(event.getDocument());
 *     }
 * });
 * }</pre>
 */
public interface DocumentEventListener {

    /**
     * Called when a new document is created remotely.
     *
     * <p>The document is already cached locally when this method is called.
     * The {@code event.getDocument()} will contain the full document data
     * unless the fetch failed ({@code event.isSyncSuccessful() == false}).</p>
     *
     * @param event The document creation event with full document data
     */
    void onDocumentCreated(DocumentEvent event);

    /**
     * Called when a document is updated remotely (new version created).
     *
     * <p>The local cache is updated with the new version before this method is called.
     * The {@code event.getDocument()} will contain the latest version data
     * unless the fetch failed ({@code event.isSyncSuccessful() == false}).</p>
     *
     * @param event The document update event with latest version data
     */
    void onDocumentUpdated(DocumentEvent event);

    /**
     * Called when a document is deleted remotely.
     *
     * <p>The document is removed from the local cache before this method is called.
     * The {@code event.getDocument()} will be null for delete events.</p>
     *
     * @param event The document deletion event
     */
    void onDocumentDeleted(DocumentEvent event);

    /**
     * Called when synchronization encounters an error.
     *
     * <p>This method is called for various error conditions including:
     * <ul>
     *   <li>Failed to fetch document from server</li>
     *   <li>Failed to save document to local storage</li>
     *   <li>Network errors</li>
     *   <li>MQTT connection issues</li>
     * </ul>
     *
     * <p>The client implementation should decide whether to retry manually,
     * alert the user, or handle the error gracefully. Some errors will be
     * retried automatically (indicated by {@code error.isWillRetry()}).</p>
     *
     * @param error Details about the synchronization error
     */
    void onSyncError(SyncError error);

    /**
     * Called when the connection to the MQTT broker is established.
     *
     * <p>This is called on initial connection and after successful reconnection
     * following a disconnect. It's a good place to update UI to show "online" status.</p>
     *
     * <p>Default implementation does nothing.</p>
     */
    default void onConnected() {
        // Default: no action
    }

    /**
     * Called when the connection to the MQTT broker is lost.
     *
     * <p>The synchronization service will automatically attempt to reconnect.
     * This is a good place to update UI to show "offline" status or queue operations.</p>
     *
     * <p>Default implementation does nothing.</p>
     */
    default void onDisconnected() {
        // Default: no action
    }

    /**
     * Called when reconnection to the MQTT broker succeeds after a disconnect.
     *
     * <p>This is useful for triggering a manual sync to catch up on any missed
     * notifications during the disconnection period.</p>
     *
     * <p>Default implementation does nothing.</p>
     */
    default void onReconnected() {
        // Default: no action
    }
}
