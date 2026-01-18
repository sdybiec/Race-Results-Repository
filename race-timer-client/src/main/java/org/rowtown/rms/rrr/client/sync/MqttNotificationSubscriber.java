package org.rowtown.rms.rrr.client.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handles MQTT subscriptions for real-time document notifications.
 *
 * <p>This class manages the connection to the MQTT broker and distributes
 * notifications to registered listeners. It supports multiple subscriptions
 * to different regattas and automatic reconnection.</p>
 */
@Slf4j
public class MqttNotificationSubscriber {

    private final String brokerUrl;
    private final String clientId;
    private final ObjectMapper objectMapper;
    private final Map<String, List<DocumentEventListener>> listenersByRegatta;
    private final Set<String> subscribedTopics;
    private final List<ConnectionListener> connectionListeners;

    private MqttClient mqttClient;
    private boolean connected;

    /**
     * Create a new MQTT notification subscriber.
     *
     * @param brokerUrl MQTT broker URL (e.g., "tcp://localhost:1883")
     * @param clientId Unique client ID for this connection
     */
    public MqttNotificationSubscriber(String brokerUrl, String clientId) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.listenersByRegatta = new ConcurrentHashMap<>();
        this.subscribedTopics = ConcurrentHashMap.newKeySet();
        this.connectionListeners = new CopyOnWriteArrayList<>();
        this.connected = false;
    }

    /**
     * Connect to the MQTT broker.
     *
     * @throws MqttException if connection fails
     */
    public void connect() throws MqttException {
        if (connected && mqttClient != null && mqttClient.isConnected()) {
            log.debug("Already connected to MQTT broker");
            return;
        }

        log.info("Connecting to MQTT broker: {}", brokerUrl);

        mqttClient = new MqttClient(brokerUrl, clientId);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);

        // Set up callbacks
        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT connection lost", cause);
                connected = false;
                notifyAllListeners(listener -> listener.onDisconnected());
                notifyConnectionListeners(ConnectionListener::onDisconnected);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                handleNotification(topic, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used for subscriptions
            }
        });

        // Connect
        boolean wasConnected = connected;
        mqttClient.connect(options);
        connected = true;

        log.info("Successfully connected to MQTT broker");

        // Notify all listeners
        if (wasConnected) {
            notifyAllListeners(listener -> listener.onReconnected());
            notifyConnectionListeners(ConnectionListener::onReconnected);
        } else {
            notifyAllListeners(listener -> listener.onConnected());
            notifyConnectionListeners(ConnectionListener::onConnected);
        }

        // Resubscribe to all topics if reconnecting
        resubscribeAll();
    }

    /**
     * Disconnect from the MQTT broker.
     */
    public void disconnect() {
        if (mqttClient != null && connected) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                connected = false;
                log.info("Disconnected from MQTT broker");
            } catch (MqttException e) {
                log.error("Error disconnecting from MQTT broker", e);
            }
        }
    }

    /**
     * Check if connected to the MQTT broker.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected && mqttClient != null && mqttClient.isConnected();
    }

    /**
     * Subscribe to notifications for a regatta.
     *
     * @param regattaId The regatta ID to monitor
     * @param listener The listener to notify of events
     * @return A subscription that can be cancelled
     * @throws MqttException if subscription fails
     */
    public Subscription subscribe(String regattaId, DocumentEventListener listener) throws MqttException {
        if (!isConnected()) {
            connect();
        }

        // Add listener to the map
        listenersByRegatta.computeIfAbsent(regattaId, k -> new CopyOnWriteArrayList<>())
                          .add(listener);

        // Subscribe to MQTT topics for this regatta
        String startListTopic = "regatta/" + regattaId + "/startlist";
        String resultsTopic = "regatta/" + regattaId + "/results/+";

        if (!subscribedTopics.contains(startListTopic)) {
            mqttClient.subscribe(startListTopic, 1);
            subscribedTopics.add(startListTopic);
            log.info("Subscribed to MQTT topic: {}", startListTopic);
        }

        if (!subscribedTopics.contains(resultsTopic)) {
            mqttClient.subscribe(resultsTopic, 1);
            subscribedTopics.add(resultsTopic);
            log.info("Subscribed to MQTT topic: {}", resultsTopic);
        }

        return new Subscription(regattaId, listener, this);
    }

    /**
     * Unsubscribe from notifications for a regatta.
     *
     * @param regattaId The regatta ID to stop monitoring
     * @param listener The specific listener to remove
     */
    public void unsubscribe(String regattaId, DocumentEventListener listener) {
        List<DocumentEventListener> listeners = listenersByRegatta.get(regattaId);
        if (listeners != null) {
            listeners.remove(listener);

            // If no more listeners for this regatta, unsubscribe from topics
            if (listeners.isEmpty()) {
                listenersByRegatta.remove(regattaId);

                try {
                    String startListTopic = "regatta/" + regattaId + "/startlist";
                    String resultsTopic = "regatta/" + regattaId + "/results/+";

                    if (mqttClient != null && mqttClient.isConnected()) {
                        mqttClient.unsubscribe(startListTopic);
                        mqttClient.unsubscribe(resultsTopic);
                        subscribedTopics.remove(startListTopic);
                        subscribedTopics.remove(resultsTopic);
                        log.info("Unsubscribed from MQTT topics for regatta: {}", regattaId);
                    }
                } catch (MqttException e) {
                    log.error("Error unsubscribing from MQTT topics", e);
                }
            }
        }
    }

    /**
     * Handle an incoming MQTT notification.
     *
     * @param topic The MQTT topic the message arrived on
     * @param message The MQTT message
     */
    private void handleNotification(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            log.debug("Received MQTT notification on topic {}: {}", topic, payload);

            // Parse notification
            NotificationEvent notification = objectMapper.readValue(payload, NotificationEvent.class);

            // Find listeners for this regatta
            String regattaId = notification.regattaId;
            List<DocumentEventListener> listeners = listenersByRegatta.get(regattaId);

            if (listeners != null && !listeners.isEmpty()) {
                log.debug("Found {} listeners for regatta {}", listeners.size(), regattaId);

                // Convert to DocumentEvent
                DocumentEvent event = convertToDocumentEvent(notification);

                // Notify all listeners for this regatta
                for (DocumentEventListener listener : listeners) {
                    try {
                        notifyListener(listener, event);
                    } catch (Exception e) {
                        log.error("Error notifying listener", e);
                    }
                }
            } else {
                log.debug("No listeners registered for regatta: {}", regattaId);
            }

        } catch (Exception e) {
            log.error("Error handling MQTT notification", e);
        }
    }

    /**
     * Convert raw notification to DocumentEvent.
     */
    private DocumentEvent convertToDocumentEvent(NotificationEvent notification) {
        return DocumentEvent.builder()
            .eventType(parseEventType(notification.eventType))
            .documentId(notification.documentId)
            .documentType(notification.documentType)
            .regattaId(notification.regattaId)
            .timerId(notification.timerId)
            .versionNumber(notification.versionNumber)
            .author(notification.author)
            .changeDescription(notification.changeDescription)
            .timestamp(parseTimestamp(notification.timestamp))
            .syncSuccessful(false) // Will be set to true after successful sync
            .build();
    }

    /**
     * Parse event type string to enum.
     */
    private DocumentEvent.EventType parseEventType(String eventType) {
        try {
            return DocumentEvent.EventType.valueOf(eventType);
        } catch (Exception e) {
            log.warn("Unknown event type: {}", eventType);
            return DocumentEvent.EventType.DOCUMENT_CREATED; // Default
        }
    }

    /**
     * Parse timestamp string to LocalDateTime.
     */
    private java.time.LocalDateTime parseTimestamp(String timestamp) {
        try {
            return java.time.LocalDateTime.parse(timestamp);
        } catch (Exception e) {
            log.warn("Failed to parse timestamp: {}", timestamp);
            return java.time.LocalDateTime.now();
        }
    }

    /**
     * Notify a specific listener based on event type.
     */
    private void notifyListener(DocumentEventListener listener, DocumentEvent event) {
        switch (event.getEventType()) {
            case DOCUMENT_CREATED:
                listener.onDocumentCreated(event);
                break;
            case VERSION_CREATED:
            case FIELD_CHANGED:
                listener.onDocumentUpdated(event);
                break;
            case DOCUMENT_DELETED:
                listener.onDocumentDeleted(event);
                break;
            default:
                log.warn("Unknown event type: {}", event.getEventType());
        }
    }

    /**
     * Resubscribe to all previously subscribed topics (after reconnect).
     */
    private void resubscribeAll() {
        if (!isConnected()) {
            return;
        }

        try {
            for (String topic : subscribedTopics) {
                mqttClient.subscribe(topic, 1);
                log.info("Resubscribed to MQTT topic: {}", topic);
            }
        } catch (MqttException e) {
            log.error("Error resubscribing to MQTT topics", e);
        }
    }

    /**
     * Notify all registered listeners with a callback.
     *
     * @param callback The callback to invoke on each listener
     */
    private void notifyAllListeners(ListenerCallback callback) {
        for (List<DocumentEventListener> listeners : listenersByRegatta.values()) {
            for (DocumentEventListener listener : listeners) {
                try {
                    callback.call(listener);
                } catch (Exception e) {
                    log.error("Error notifying listener", e);
                }
            }
        }
    }

    @FunctionalInterface
    private interface ListenerCallback {
        void call(DocumentEventListener listener);
    }

    /**
     * Adds a connection listener to receive connection state changes.
     *
     * @param listener the connection listener to add
     */
    public void addConnectionListener(ConnectionListener listener) {
        if (listener != null && !connectionListeners.contains(listener)) {
            connectionListeners.add(listener);
        }
    }

    /**
     * Removes a connection listener.
     *
     * @param listener the connection listener to remove
     */
    public void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    /**
     * Notifies all connection listeners with a callback.
     *
     * @param callback The callback to invoke on each listener
     */
    private void notifyConnectionListeners(java.util.function.Consumer<ConnectionListener> callback) {
        for (ConnectionListener listener : connectionListeners) {
            try {
                callback.accept(listener);
            } catch (Exception e) {
                log.error("Error notifying connection listener", e);
            }
        }
    }

    /**
     * Listener interface for connection state changes.
     */
    public interface ConnectionListener {
        /**
         * Called when initially connected to the MQTT broker.
         */
        void onConnected();

        /**
         * Called when reconnected after a connection loss.
         */
        void onReconnected();

        /**
         * Called when connection to MQTT broker is lost.
         */
        void onDisconnected();
    }

    /**
     * Internal notification event structure matching server format.
     */
    static class NotificationEvent {
        public String eventType;
        public Long documentId;
        public String documentType;
        public String regattaId;
        public String timerId;
        public Long versionNumber;
        public String timestamp;
        public String author;
        public String changeDescription;
    }
}
