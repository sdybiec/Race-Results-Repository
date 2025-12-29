package org.rowtown.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rowtown.domain.DocumentType;
import org.rowtown.notification.NotificationEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service for coordinating notifications across MQTT and Webhooks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final MqttPublishingService mqttService;
    private final WebhookService webhookService;

    /**
     * Publish a document change notification.
     */
    public void notifyDocumentChange(Long documentId, DocumentType documentType, String regattaId,
                                    String timerId, Long versionNumber, String author,
                                    String changeDescription, NotificationEvent.EventType eventType) {
        NotificationEvent event = NotificationEvent.builder()
            .eventType(eventType)
            .documentId(documentId)
            .documentType(documentType)
            .regattaId(regattaId)
            .timerId(timerId)
            .versionNumber(versionNumber)
            .timestamp(LocalDateTime.now())
            .author(author)
            .changeDescription(changeDescription)
            .build();

        // Publish to MQTT
        mqttService.publishNotification(event);

        // Deliver to webhooks
        webhookService.deliverNotification(event);

        log.info("Sent notifications for document {} version {}", documentId, versionNumber);
    }

    /**
     * Notify about a new version being created.
     */
    public void notifyVersionCreated(Long documentId, DocumentType documentType, String regattaId,
                                    String timerId, Long versionNumber, String author, String changeDescription) {
        notifyDocumentChange(documentId, documentType, regattaId, timerId, versionNumber,
            author, changeDescription, NotificationEvent.EventType.VERSION_CREATED);
    }

    /**
     * Notify about a new document being created.
     */
    public void notifyDocumentCreated(Long documentId, DocumentType documentType, String regattaId,
                                     String timerId, String author) {
        notifyDocumentChange(documentId, documentType, regattaId, timerId, 1L,
            author, "Document created", NotificationEvent.EventType.DOCUMENT_CREATED);
    }
}
