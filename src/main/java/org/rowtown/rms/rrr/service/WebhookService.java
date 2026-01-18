package org.rowtown.rms.rrr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rowtown.rms.rrr.domain.entity.WebhookSubscription;
import org.rowtown.rms.rrr.dto.WebhookSubscriptionInfo;
import org.rowtown.rms.rrr.dto.WebhookSubscriptionRequest;
import org.rowtown.rms.rrr.notification.NotificationEvent;
import org.rowtown.rms.rrr.repository.WebhookSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing webhook subscriptions and delivering notifications.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Value("${webhook.retry.max-attempts}")
    private int maxRetryAttempts;

    @Value("${webhook.retry.initial-delay}")
    private long initialDelay;

    @Value("${webhook.timeout}")
    private int timeout;

    /**
     * Create a new webhook subscription.
     */
    @Transactional
    public WebhookSubscriptionInfo subscribe(WebhookSubscriptionRequest request) {
        WebhookSubscription subscription = WebhookSubscription.builder()
            .documentId(request.getDocumentId())
            .regattaId(request.getRegattaId())
            .webhookUrl(request.getWebhookUrl())
            .secretKey(request.getSecretKey())
            .events(serializeEvents(request.getEvents()))
            .active(true)
            .failedDeliveries(0)
            .build();

        subscription = subscriptionRepository.save(subscription);
        log.info("Created webhook subscription: {}", subscription.getSubscriptionId());

        return toSubscriptionInfo(subscription);
    }

    /**
     * Serialize events list to JSON string.
     */
    private String serializeEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(events);
        } catch (Exception e) {
            log.error("Failed to serialize events", e);
            return null;
        }
    }

    /**
     * Delete a webhook subscription.
     */
    @Transactional
    public void unsubscribe(Long subscriptionId) {
        subscriptionRepository.deleteById(subscriptionId);
        log.info("Deleted webhook subscription: {}", subscriptionId);
    }

    /**
     * List all webhook subscriptions for current user.
     */
    @Transactional(readOnly = true)
    public List<WebhookSubscriptionInfo> listSubscriptions() {
        return subscriptionRepository.findAll().stream()
            .map(this::toSubscriptionInfo)
            .collect(Collectors.toList());
    }

    /**
     * Deliver notification to webhooks asynchronously with retry logic.
     */
    @Async
    public void deliverNotification(NotificationEvent event) {
        List<WebhookSubscription> subscriptions = subscriptionRepository.findAll().stream()
            .filter(sub -> sub.getActive())
            .filter(sub -> matchesSubscription(sub, event))
            .collect(Collectors.toList());

        for (WebhookSubscription subscription : subscriptions) {
            // Check if subscription is interested in this event type
            if (isInterestedInEvent(subscription, event.getEventType().toString())) {
                deliverToWebhook(subscription, event);
            }
        }
    }

    /**
     * Check if subscription matches the event.
     */
    private boolean matchesSubscription(WebhookSubscription subscription, NotificationEvent event) {
        // Document-specific subscription
        if (subscription.getDocumentId() != null) {
            return subscription.getDocumentId().equals(event.getDocumentId());
        }

        // Regatta-wide subscription
        if (subscription.getRegattaId() != null) {
            return subscription.getRegattaId().equals(event.getRegattaId());
        }

        return false;
    }

    /**
     * Check if subscription is interested in this event type.
     */
    private boolean isInterestedInEvent(WebhookSubscription subscription, String eventType) {
        if (subscription.getEvents() == null || subscription.getEvents().isEmpty()) {
            return true; // No filter means interested in all events
        }

        try {
            String eventsJson = subscription.getEvents();

            // Handle legacy double-encoded data (from old bug)
            // If it starts with a quote, it's double-encoded, so unwrap it
            if (eventsJson.startsWith("\"") && eventsJson.endsWith("\"")) {
                // Remove outer quotes and unescape
                eventsJson = objectMapper.readValue(eventsJson, String.class);
            }

            String[] events = objectMapper.readValue(eventsJson, String[].class);
            return Arrays.asList(events).contains(eventType);
        } catch (Exception e) {
            log.error("Failed to parse subscription events", e);
            return true; // On error, deliver anyway
        }
    }

    /**
     * Deliver notification to a specific webhook with exponential backoff retry.
     */
    private void deliverToWebhook(WebhookSubscription subscription, NotificationEvent event) {
        int attempt = 0;
        long delay = initialDelay;

        while (attempt < maxRetryAttempts) {
            try {
                if (attempt > 0) {
                    Thread.sleep(delay);
                }

                // Prepare payload
                String payload = objectMapper.writeValueAsString(event);

                // Calculate HMAC signature
                String signature = calculateHmacSignature(payload, subscription.getSecretKey());

                // Prepare headers
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Webhook-Signature", signature);
                headers.set("X-Event-Type", event.getEventType().toString());

                // Send request
                HttpEntity<String> request = new HttpEntity<>(payload, headers);
                ResponseEntity<String> response = restTemplate.exchange(
                    subscription.getWebhookUrl(),
                    HttpMethod.POST,
                    request,
                    String.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.debug("Successfully delivered webhook to: {}", subscription.getWebhookUrl());
                    resetFailureCount(subscription);
                    return;
                }

            } catch (Exception e) {
                log.warn("Webhook delivery attempt {} failed for {}: {}",
                    attempt + 1, subscription.getWebhookUrl(), e.getMessage());
            }

            attempt++;
            delay *= 2; // Exponential backoff: 2s, 4s, 8s, 16s
        }

        // All retries failed
        incrementFailureCount(subscription);
    }

    /**
     * Calculate HMAC-SHA256 signature for webhook payload.
     */
    private String calculateHmacSignature(String payload, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC signature", e);
        }
    }

    /**
     * Reset failure count after successful delivery.
     */
    @Transactional
    protected void resetFailureCount(WebhookSubscription subscription) {
        subscription.setFailedDeliveries(0);
        subscription.setLastFailure(null);
        subscriptionRepository.save(subscription);
    }

    /**
     * Increment failure count and potentially deactivate webhook.
     */
    @Transactional
    protected void incrementFailureCount(WebhookSubscription subscription) {
        subscription.setFailedDeliveries(subscription.getFailedDeliveries() + 1);
        subscription.setLastFailure(LocalDateTime.now());

        // Deactivate after too many failures
        if (subscription.getFailedDeliveries() >= 10) {
            subscription.setActive(false);
            log.warn("Deactivated webhook {} due to excessive failures", subscription.getSubscriptionId());
        }

        subscriptionRepository.save(subscription);
    }

    private WebhookSubscriptionInfo toSubscriptionInfo(WebhookSubscription subscription) {
        List<String> events = null;
        try {
            if (subscription.getEvents() != null) {
                events = Arrays.asList(objectMapper.readValue(subscription.getEvents(), String[].class));
            }
        } catch (Exception e) {
            log.error("Failed to parse events", e);
        }

        return WebhookSubscriptionInfo.builder()
            .subscriptionId(subscription.getSubscriptionId())
            .documentId(subscription.getDocumentId())
            .regattaId(subscription.getRegattaId())
            .webhookUrl(subscription.getWebhookUrl())
            .events(events)
            .createdAt(subscription.getCreatedAt())
            .active(subscription.getActive())
            .failedDeliveries(subscription.getFailedDeliveries())
            .build();
    }
}
