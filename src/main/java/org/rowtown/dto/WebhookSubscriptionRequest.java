package org.rowtown.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for webhook subscription request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookSubscriptionRequest {
    private Long documentId;
    private String regattaId;
    private String webhookUrl;
    private String secretKey;
    private List<String> events;
}
