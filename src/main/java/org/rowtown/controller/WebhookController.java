package org.rowtown.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.rowtown.dto.MqttConnectionInfo;
import org.rowtown.dto.WebhookSubscriptionInfo;
import org.rowtown.dto.WebhookSubscriptionRequest;
import org.rowtown.service.MqttPublishingService;
import org.rowtown.service.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for notification subscriptions (webhooks and MQTT).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification subscription API")
public class WebhookController {

    private final WebhookService webhookService;
    private final MqttPublishingService mqttService;

    @PostMapping("/webhooks/subscribe")
    @Operation(summary = "Subscribe to webhook", description = "Register a webhook for document change notifications")
    public ResponseEntity<WebhookSubscriptionInfo> subscribeWebhook(@RequestBody WebhookSubscriptionRequest request) {
        WebhookSubscriptionInfo info = webhookService.subscribe(request);
        return new ResponseEntity<>(info, HttpStatus.CREATED);
    }

    @DeleteMapping("/webhooks/{id}")
    @Operation(summary = "Unsubscribe webhook", description = "Remove a webhook subscription")
    public ResponseEntity<Void> unsubscribeWebhook(@PathVariable Long id) {
        webhookService.unsubscribe(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/webhooks")
    @Operation(summary = "List webhooks", description = "List all webhook subscriptions")
    public ResponseEntity<List<WebhookSubscriptionInfo>> listWebhooks() {
        List<WebhookSubscriptionInfo> webhooks = webhookService.listSubscriptions();
        return ResponseEntity.ok(webhooks);
    }

    @GetMapping("/mqtt/info")
    @Operation(summary = "Get MQTT info", description = "Get MQTT broker connection information")
    public ResponseEntity<MqttConnectionInfo> getMqttInfo() {
        MqttConnectionInfo info = mqttService.getConnectionInfo();
        return ResponseEntity.ok(info);
    }
}
