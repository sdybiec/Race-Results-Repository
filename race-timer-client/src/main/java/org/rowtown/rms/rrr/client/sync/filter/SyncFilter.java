package org.rowtown.rms.rrr.client.sync.filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Filter for selective document synchronization.
 *
 * <p>Filters determine which documents should be synchronized based on criteria
 * like document type, ID, regatta, or custom predicates.
 *
 * <p><b>Thread Safety:</b> Implementations should be thread-safe.
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Only sync start lists
 * SyncFilter filter = SyncFilter.byDocumentType("STARTLIST");
 *
 * // Only sync specific documents
 * SyncFilter filter = SyncFilter.byDocumentIds(123L, 456L, 789L);
 *
 * // Only sync for specific timer
 * SyncFilter filter = SyncFilter.byTimerId("timer-1");
 *
 * // Combine filters
 * SyncFilter filter = SyncFilter.byDocumentType("RESULTS")
 *     .and(SyncFilter.byTimerId("timer-1"));
 *
 * // Custom filter logic
 * SyncFilter filter = SyncFilter.custom(event ->
 *     "RESULTS".equals(event.getDocumentType()) &&
 *     event.getVersionNumber() > 5
 * );
 * }</pre>
 */
@FunctionalInterface
public interface SyncFilter {

    /**
     * Tests whether a document event should be synchronized.
     *
     * @param event the document event to test
     * @return true if the event should be synced, false otherwise
     */
    boolean shouldSync(DocumentEvent event);

    /**
     * Combines this filter with another using AND logic.
     *
     * @param other the other filter
     * @return a combined filter that requires both filters to pass
     */
    default SyncFilter and(SyncFilter other) {
        return event -> this.shouldSync(event) && other.shouldSync(event);
    }

    /**
     * Combines this filter with another using OR logic.
     *
     * @param other the other filter
     * @return a combined filter that requires either filter to pass
     */
    default SyncFilter or(SyncFilter other) {
        return event -> this.shouldSync(event) || other.shouldSync(event);
    }

    /**
     * Negates this filter.
     *
     * @return a filter that passes when this filter fails
     */
    default SyncFilter negate() {
        return event -> !this.shouldSync(event);
    }

    // Static factory methods

    /**
     * Creates a filter that accepts all events.
     */
    static SyncFilter acceptAll() {
        return event -> true;
    }

    /**
     * Creates a filter that rejects all events.
     */
    static SyncFilter rejectAll() {
        return event -> false;
    }

    /**
     * Creates a filter that only accepts specific document types.
     *
     * @param documentTypes the document types to accept
     * @return a filter for the specified document types
     */
    static SyncFilter byDocumentType(String... documentTypes) {
        Set<String> types = new HashSet<>(Arrays.asList(documentTypes));
        return event -> event.getDocumentType() != null &&
                        types.contains(event.getDocumentType());
    }

    /**
     * Creates a filter that only accepts specific document IDs.
     *
     * @param documentIds the document IDs to accept
     * @return a filter for the specified document IDs
     */
    static SyncFilter byDocumentIds(Long... documentIds) {
        Set<Long> ids = new HashSet<>(Arrays.asList(documentIds));
        return event -> event.getDocumentId() != null &&
                        ids.contains(event.getDocumentId());
    }

    /**
     * Creates a filter that only accepts events for a specific regatta.
     *
     * @param regattaId the regatta ID
     * @return a filter for the specified regatta
     */
    static SyncFilter byRegattaId(String regattaId) {
        return event -> regattaId != null &&
                        regattaId.equals(event.getRegattaId());
    }

    /**
     * Creates a filter that only accepts events for a specific timer.
     *
     * @param timerId the timer ID
     * @return a filter for the specified timer
     */
    static SyncFilter byTimerId(String timerId) {
        return event -> timerId != null &&
                        timerId.equals(event.getTimerId());
    }

    /**
     * Creates a filter that only accepts specific event types.
     *
     * @param eventTypes the event types to accept
     * @return a filter for the specified event types
     */
    static SyncFilter byEventType(DocumentEvent.EventType... eventTypes) {
        Set<DocumentEvent.EventType> types = new HashSet<>(Arrays.asList(eventTypes));
        return event -> event.getEventType() != null &&
                        types.contains(event.getEventType());
    }

    /**
     * Creates a filter that only accepts events from a specific author.
     *
     * @param author the author name
     * @return a filter for the specified author
     */
    static SyncFilter byAuthor(String author) {
        return event -> author != null &&
                        author.equals(event.getAuthor());
    }

    /**
     * Creates a filter with custom logic.
     *
     * @param predicate the custom filter predicate
     * @return a filter using the provided predicate
     */
    static SyncFilter custom(Predicate<DocumentEvent> predicate) {
        return predicate::test;
    }

    /**
     * Creates a filter that only syncs documents created or modified after a certain version.
     *
     * @param minVersion the minimum version number
     * @return a filter for versions >= minVersion
     */
    static SyncFilter byMinVersion(long minVersion) {
        return event -> event.getVersionNumber() != null &&
                        event.getVersionNumber() >= minVersion;
    }

    /**
     * Creates a filter for start lists only.
     */
    static SyncFilter startListsOnly() {
        return byDocumentType("STARTLIST");
    }

    /**
     * Creates a filter for results only.
     */
    static SyncFilter resultsOnly() {
        return byDocumentType("RESULTS");
    }

    /**
     * Creates a filter that excludes deletes.
     */
    static SyncFilter excludeDeletes() {
        return event -> event.getEventType() != DocumentEvent.EventType.DOCUMENT_DELETED;
    }

    /**
     * Creates a filter that only includes creates.
     */
    static SyncFilter createsOnly() {
        return byEventType(DocumentEvent.EventType.DOCUMENT_CREATED);
    }

    /**
     * Creates a filter that only includes updates.
     */
    static SyncFilter updatesOnly() {
        return byEventType(
                DocumentEvent.EventType.VERSION_CREATED,
                DocumentEvent.EventType.FIELD_CHANGED);
    }
}
