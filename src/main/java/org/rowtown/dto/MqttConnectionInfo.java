package org.rowtown.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for MQTT connection information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MqttConnectionInfo {
    private String brokerUrl;
    private String topicPrefix;
    private String startListTopicPattern;
    private String raceResultsTopicPattern;
}
