package org.rowtown.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.domain.DocumentType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event representing a document change notification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEvent {
    private EventType eventType;
    private Long documentId;
    private DocumentType documentType;
    private String regattaId;
    private String timerId;
    private Long versionNumber;
    private LocalDateTime timestamp;
    private String author;
    private String changeDescription;
    private List<String> changedFields;
    private byte[] documentData; // Full document for webhooks

    public enum EventType {
        VERSION_CREATED,
        DOCUMENT_CREATED,
        FIELD_CHANGED,
        DOCUMENT_DELETED
    }
}
