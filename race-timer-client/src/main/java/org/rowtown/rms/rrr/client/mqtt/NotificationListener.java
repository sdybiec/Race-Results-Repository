package org.rowtown.rms.rrr.client.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MQTT listener for receiving Start List update notifications.
 */
@Slf4j
public class NotificationListener {

    private final String brokerUrl;
    private final String clientId;
    private final String regattaId;
    private final ObjectMapper objectMapper;
    private final List<StartListChangeHandler> handlers;

    private MqttClient mqttClient;
    private boolean connected;

    public NotificationListener(String brokerUrl, String clientId, String regattaId) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.regattaId = regattaId;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.handlers = new CopyOnWriteArrayList<>();
        this.connected = false;
    }

    /**
     * Connect to MQTT broker and subscribe to Start List changes.
     */
    public void connect() throws MqttException {
        if (connected) {
            log.warn("Already connected to MQTT broker");
            return;
        }

        mqttClient = new MqttClient(brokerUrl, clientId);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT connection lost", cause);
                connected = false;
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                handleMessage(topic, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used for subscriber
            }
        });

        mqttClient.connect(options);

        // Subscribe to Start List and Regatta Definition (RML) changes for this
        // regatta. Handlers route by the documentType in the message payload.
        String startListTopic = "regatta/" + regattaId + "/startlist";
        String rmlTopic = "regatta/" + regattaId + "/regatta-definition";
        mqttClient.subscribe(startListTopic, 1);
        mqttClient.subscribe(rmlTopic, 1);

        connected = true;
        log.info("Connected to MQTT broker and subscribed to: {}, {}", startListTopic, rmlTopic);
    }

    /**
     * Disconnect from MQTT broker.
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
     * Check if connected to MQTT broker.
     */
    public boolean isConnected() {
        return connected && mqttClient != null && mqttClient.isConnected();
    }

    /**
     * Add a handler for Start List changes.
     */
    public void addChangeHandler(StartListChangeHandler handler) {
        handlers.add(handler);
    }

    /**
     * Remove a change handler.
     */
    public void removeChangeHandler(StartListChangeHandler handler) {
        handlers.remove(handler);
    }

    /**
     * Handle incoming MQTT message.
     */
    private void handleMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            log.debug("Received MQTT message on {}: {}", topic, payload);

            NotificationEvent event = objectMapper.readValue(payload, NotificationEvent.class);

            // Notify all handlers
            for (StartListChangeHandler handler : handlers) {
                try {
                    handler.onStartListChanged(event);
                } catch (Exception e) {
                    log.error("Error in change handler", e);
                }
            }

        } catch (Exception e) {
            log.error("Error handling MQTT message", e);
        }
    }

    /**
     * Interface for handling Start List changes.
     */
    public interface StartListChangeHandler {
        void onStartListChanged(NotificationEvent event);
    }

    /**
     * Notification event from server.
     */
    public static class NotificationEvent {
        public String eventType;
        public Long documentId;
        public String documentType;
        public String regattaId;
        public Long versionNumber;
        public String timestamp;
        public String author;
        public String changeDescription;
        public List<String> changedFields;
    }
}
