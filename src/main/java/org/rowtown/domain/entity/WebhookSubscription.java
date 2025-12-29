package org.rowtown.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a webhook subscription for document change notifications.
 */
@Entity
@Table(name = "webhook_subscriptions",
    indexes = {
        @Index(name = "idx_document", columnList = "document_id"),
        @Index(name = "idx_regatta", columnList = "regattaId")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "regattaId")
    private String regattaId;

    @Column(name = "webhook_url", nullable = false, length = 1024)
    private String webhookUrl;

    @Column(name = "secret_key", nullable = false)
    private String secretKey;

    @Column(name = "events", columnDefinition = "JSON")
    private String events;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "failed_deliveries", nullable = false)
    @Builder.Default
    private Integer failedDeliveries = 0;

    @Column(name = "last_failure")
    private LocalDateTime lastFailure;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
