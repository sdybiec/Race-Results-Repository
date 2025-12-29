package org.rowtown.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for webhook subscription information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookSubscriptionInfo {
    private Long subscriptionId;
    private Long documentId;
    private String regattaId;
    private String webhookUrl;
    private List<String> events;
    private LocalDateTime createdAt;
    private Boolean active;
    private Integer failedDeliveries;
}
