package org.rowtown.repository;

import org.rowtown.domain.entity.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for WebhookSubscription entity operations.
 */
@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {

    /**
     * Find all active subscriptions for a specific document.
     */
    List<WebhookSubscription> findByDocumentIdAndActiveTrue(Long documentId);

    /**
     * Find all active subscriptions for a regatta.
     */
    List<WebhookSubscription> findByRegattaIdAndActiveTrue(String regattaId);

    /**
     * Find all subscriptions for a specific document (including inactive).
     */
    List<WebhookSubscription> findByDocumentId(Long documentId);

    /**
     * Find all subscriptions for a regatta (including inactive).
     */
    List<WebhookSubscription> findByRegattaId(String regattaId);
}
