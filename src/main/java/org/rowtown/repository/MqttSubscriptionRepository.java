package org.rowtown.repository;

import org.rowtown.domain.entity.MqttSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for MqttSubscription entity operations.
 */
@Repository
public interface MqttSubscriptionRepository extends JpaRepository<MqttSubscription, Long> {

    /**
     * Find all active subscriptions for a user.
     */
    List<MqttSubscription> findByUserIdAndActiveTrue(String userId);

    /**
     * Find all subscriptions for a user (including inactive).
     */
    List<MqttSubscription> findByUserId(String userId);

    /**
     * Find subscription by user and topic pattern.
     */
    List<MqttSubscription> findByUserIdAndTopicPattern(String userId, String topicPattern);
}
