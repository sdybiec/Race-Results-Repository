package org.rowtown.rms.rrr.client.sync;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents an active subscription to document events.
 *
 * <p>A subscription is created when calling {@link DocumentSynchronizationService#subscribe}
 * and can be cancelled by calling {@link #cancel()}.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * Subscription subscription = syncService.subscribe("REGATTA_2026", listener);
 * // ... do work ...
 * subscription.cancel(); // Stop receiving notifications
 * }</pre>
 */
@Slf4j
@Getter
public class Subscription {

    private final String regattaId;
    private final DocumentEventListener listener;
    private final MqttNotificationSubscriber mqttSubscriber;
    private boolean active;

    /**
     * Create a new subscription.
     *
     * @param regattaId The regatta ID being monitored
     * @param listener The event listener
     * @param mqttSubscriber The MQTT subscriber managing the connection
     */
    public Subscription(String regattaId, DocumentEventListener listener, MqttNotificationSubscriber mqttSubscriber) {
        this.regattaId = regattaId;
        this.listener = listener;
        this.mqttSubscriber = mqttSubscriber;
        this.active = true;
    }

    /**
     * Cancel this subscription.
     *
     * <p>After cancellation, the listener will no longer receive events for this regatta.
     * The underlying MQTT connection will be closed if this is the last active subscription.</p>
     *
     * <p>This method is idempotent - calling it multiple times has no effect after the first call.</p>
     */
    public void cancel() {
        if (!active) {
            log.debug("Subscription already cancelled for regatta: {}", regattaId);
            return;
        }

        try {
            mqttSubscriber.unsubscribe(regattaId, listener);
            active = false;
            log.info("Cancelled subscription for regatta: {}", regattaId);
        } catch (Exception e) {
            log.error("Error cancelling subscription for regatta: {}", regattaId, e);
        }
    }

    /**
     * Check if this subscription is still active.
     *
     * @return true if active, false if cancelled
     */
    public boolean isActive() {
        return active;
    }
}
