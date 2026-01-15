package org.rowtown.rms.rrr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.notification.NotificationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Service for publishing notifications to MQTT broker.
 */
@Service
@Slf4j
public class MqttPublishingService {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Value("${mqtt.topic.prefix}")
    private String topicPrefix;

    private MqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            mqttClient = new MqttClient(brokerUrl, clientId);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            if (!username.isEmpty()) {
                options.setUserName(username);
                options.setPassword(password.toCharArray());
            }

            mqttClient.connect(options);
            log.info("Connected to MQTT broker: {}", brokerUrl);
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
            }
        } catch (MqttException e) {
            log.error("Error disconnecting from MQTT broker", e);
        }
    }

    /**
     * Publish a notification event to MQTT.
     */
    public void publishNotification(NotificationEvent event) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            log.warn("MQTT client not connected, skipping notification");
            return;
        }

        try {
            String topic = buildTopic(event);
            String payload = objectMapper.writeValueAsString(event);

            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            message.setRetained(false);

            mqttClient.publish(topic, message);
            log.debug("Published notification to topic: {}", topic);
        } catch (Exception e) {
            log.error("Failed to publish MQTT notification", e);
        }
    }

    /**
     * Build MQTT topic based on event details.
     * Format: regatta/{regattaId}/startlist or regatta/{regattaId}/results/{timerId}
     */
    private String buildTopic(NotificationEvent event) {
        StringBuilder topic = new StringBuilder(topicPrefix);
        topic.append("/").append(event.getRegattaId());

        if (event.getDocumentType() == DocumentType.START_LIST) {
            topic.append("/startlist");
        } else if (event.getDocumentType() == DocumentType.RACE_RESULTS) {
            topic.append("/results/").append(event.getTimerId());
        }

        return topic.toString();
    }

    /**
     * Get connection information for clients.
     */
    public org.rowtown.rms.rrr.dto.MqttConnectionInfo getConnectionInfo() {
        return org.rowtown.rms.rrr.dto.MqttConnectionInfo.builder()
            .brokerUrl(brokerUrl)
            .topicPrefix(topicPrefix)
            .startListTopicPattern(topicPrefix + "/{regattaId}/startlist")
            .raceResultsTopicPattern(topicPrefix + "/{regattaId}/results/{timerId}")
            .build();
    }
}
