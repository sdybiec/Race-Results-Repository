package org.rowtown.rms.rrr.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.*;
import org.rowtown.rms.rrr.config.EmbeddedMqttBrokerConfig;
import org.rowtown.rms.rrr.config.TestSecurityConfig;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.dto.DocumentRequest;
import org.rowtown.rms.rrr.dto.DocumentResponse;
import org.rowtown.rms.rrr.dto.WebhookSubscriptionRequest;
import org.rowtown.rms.rrr.notification.NotificationEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for NotificationService and data synchronization.
 * Tests MQTT and Webhook notification delivery and client synchronization workflows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({EmbeddedMqttBrokerConfig.class, TestSecurityConfig.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationSynchronizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static WireMockServer wireMockServer;
    private MqttClient mqttClient;
    private List<NotificationEvent> receivedMqttEvents;
    private CountDownLatch mqttLatch;

    @BeforeAll
    static void setupWireMock() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterAll
    static void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setup() throws Exception {
        receivedMqttEvents = new CopyOnWriteArrayList<>();
        wireMockServer.resetAll();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
            mqttClient.close();
        }
    }

    @Test
    @Order(1)
    void testMqttNotificationOnDocumentCreation() throws Exception {
        // Setup MQTT subscription
        String regattaId = "MQTT_TEST_2025";
        String topic = "regatta/" + regattaId + "/startlist";
        mqttLatch = new CountDownLatch(1);

        setupMqttClient(topic);

        // Create a Start List document
        DocumentRequest request = DocumentRequest.builder()
            .type(DocumentType.START_LIST)
            .regattaStartDate(java.time.LocalDate.of(2024, 5, 17))
            .regattaId(regattaId)
            .author("test@example.com")
            .description("Start List for MQTT test")
            .modelData(org.rowtown.rms.rrr.testutil.SampleData.raceResultsModel("1a"))
            .build();

        MvcResult result = mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        DocumentResponse createdDoc = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            DocumentResponse.class);

        // Wait for MQTT notification (max 5 seconds)
        boolean received = mqttLatch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "MQTT notification should be received within 5 seconds");

        // Verify notification content
        assertFalse(receivedMqttEvents.isEmpty(), "Should have received at least one MQTT event");
        NotificationEvent event = receivedMqttEvents.get(0);

        assertEquals(NotificationEvent.EventType.DOCUMENT_CREATED, event.getEventType());
        assertEquals(createdDoc.getDocumentId(), event.getDocumentId());
        assertEquals(DocumentType.START_LIST, event.getDocumentType());
        assertEquals(regattaId, event.getRegattaId());
        assertNotNull(event.getTimestamp());
        assertEquals("test@example.com", event.getAuthor());
    }

    @Test
    @Order(2)
    void testMqttNotificationOnRaceResults() throws Exception {
        // Setup MQTT subscription for race results
        String regattaId = "RACE_MQTT_2025";
        // The results topic segment is now the timer role (see TimerRole).
        String timerId = "PRIMARY";
        String topic = "regatta/" + regattaId + "/results/" + timerId;
        mqttLatch = new CountDownLatch(1);

        setupMqttClient(topic);

        // Create a Race Results document
        DocumentRequest request = DocumentRequest.builder()
            .type(DocumentType.RACE_RESULTS)
            .regattaStartDate(java.time.LocalDate.of(2024, 5, 17))
            .regattaId(regattaId)
            .milestoneId("finish")
            .timer(org.rowtown.rms.rrr.domain.TimerRole.PRIMARY)
            .author("timer@example.com")
            .description("Race results for MQTT test")
            .modelData(org.rowtown.rms.rrr.testutil.SampleData.raceResultsModel("1a"))
            .build();

        MvcResult result = mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        DocumentResponse createdDoc = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            DocumentResponse.class);

        // Wait for MQTT notification
        boolean received = mqttLatch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "MQTT notification for race results should be received");

        // Verify notification content
        NotificationEvent event = receivedMqttEvents.get(0);
        assertEquals(NotificationEvent.EventType.DOCUMENT_CREATED, event.getEventType());
        assertEquals(createdDoc.getDocumentId(), event.getDocumentId());
        assertEquals(DocumentType.RACE_RESULTS, event.getDocumentType());
        assertEquals(regattaId, event.getRegattaId());
        assertEquals(timerId, event.getTimerId());
    }

    @Test
    @Order(3)
    void testWebhookNotificationWithSignature() throws Exception {
        String webhookUrl = "http://localhost:8089/webhook";
        String secretKey = "test-secret-key-123";

        // Setup webhook mock
        stubFor(WireMock.post(urlEqualTo("/webhook"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"received\"}")));

        // Subscribe to webhook
        WebhookSubscriptionRequest webhookRequest = WebhookSubscriptionRequest.builder()
            .regattaId("WEBHOOK_TEST_2025")
            .webhookUrl(webhookUrl)
            .secretKey(secretKey)
            .events(List.of("DOCUMENT_CREATED", "VERSION_CREATED"))
            .build();

        mockMvc.perform(post("/api/v1/webhooks/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(webhookRequest)))
            .andExpect(status().isCreated());

        // Create a document to trigger webhook
        DocumentRequest docRequest = DocumentRequest.builder()
            .type(DocumentType.START_LIST)
            .regattaStartDate(java.time.LocalDate.of(2024, 5, 17))
            .regattaId("WEBHOOK_TEST_2025")
            .author("webhook@example.com")
            .description("Webhook test document")
            .modelData(org.rowtown.rms.rrr.testutil.SampleData.raceResultsModel("1a"))
            .build();

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(docRequest)))
            .andExpect(status().isCreated());

        // Wait for async webhook delivery (max 3 seconds)
        Thread.sleep(3000);

        // Verify webhook was called
        verify(postRequestedFor(urlEqualTo("/webhook"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("X-Event-Type", equalTo("DOCUMENT_CREATED"))
            .withHeader("X-Webhook-Signature", matching(".*"))); // HMAC signature present

        // Verify webhook call count (exactly 1)
        verify(1, postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    @Order(4)
    void testWebhookRetryOnFailure() throws Exception {
        String webhookUrl = "http://localhost:8089/failing-webhook";
        String secretKey = "retry-test-secret";

        // Setup webhook to fail first 2 times, then succeed
        stubFor(WireMock.post(urlEqualTo("/failing-webhook"))
            .inScenario("Retry")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("First Retry"));

        stubFor(WireMock.post(urlEqualTo("/failing-webhook"))
            .inScenario("Retry")
            .whenScenarioStateIs("First Retry")
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("Second Retry"));

        stubFor(WireMock.post(urlEqualTo("/failing-webhook"))
            .inScenario("Retry")
            .whenScenarioStateIs("Second Retry")
            .willReturn(aResponse().withStatus(200)));

        // Subscribe to webhook
        WebhookSubscriptionRequest webhookRequest = WebhookSubscriptionRequest.builder()
            .regattaId("RETRY_TEST_2025")
            .webhookUrl(webhookUrl)
            .secretKey(secretKey)
            .events(List.of("DOCUMENT_CREATED"))
            .build();

        mockMvc.perform(post("/api/v1/webhooks/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(webhookRequest)))
            .andExpect(status().isCreated());

        // Create a document to trigger webhook
        DocumentRequest docRequest = DocumentRequest.builder()
            .type(DocumentType.START_LIST)
            .regattaStartDate(java.time.LocalDate.of(2024, 5, 17))
            .regattaId("RETRY_TEST_2025")
            .author("retry@example.com")
            .description("Retry test document")
            .modelData(org.rowtown.rms.rrr.testutil.SampleData.raceResultsModel("1a"))
            .build();

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(docRequest)))
            .andExpect(status().isCreated());

        // Wait for retries to complete (initial + 2 retries at 100ms + 200ms + processing time)
        Thread.sleep(5000);

        // Verify webhook was called multiple times (initial + retries, at least 2 times)
        verify(moreThanOrExactly(2), postRequestedFor(urlEqualTo("/failing-webhook")));
    }

    @Test
    @Order(5)
    void testClientSynchronizationWorkflow() throws Exception {
        // This test simulates a client synchronization workflow:
        // 1. Client subscribes to MQTT notifications
        // 2. Server creates/updates documents
        // 3. Client receives notifications and could sync data

        String regattaId = "SYNC_TEST_2025";
        String startListTopic = "regatta/" + regattaId + "/startlist";
        mqttLatch = new CountDownLatch(2); // Expect 2 notifications (create + update)

        setupMqttClient(startListTopic);

        // Step 1: Create initial Start List
        DocumentRequest createRequest = DocumentRequest.builder()
            .type(DocumentType.START_LIST)
            .regattaStartDate(java.time.LocalDate.of(2024, 5, 17))
            .regattaId(regattaId)
            .author("sync@example.com")
            .description("Initial start list")
            .modelData(org.rowtown.rms.rrr.testutil.SampleData.raceResultsModel("1a"))
            .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        DocumentResponse doc = objectMapper.readValue(
            createResult.getResponse().getContentAsString(),
            DocumentResponse.class);

        // Give time for first notification
        Thread.sleep(500);

        // Step 2: Update Start List (triggers another notification)
        // Ingest validation requires a loadable TDI model, not placeholder text.
        mockMvc.perform(put("/api/v1/documents/" + doc.getDocumentId())
                .param("changeDescription", "Updated start list")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(org.rowtown.rms.rrr.testutil.SampleData.raceResultsFinal()))
            .andExpect(status().isOk());

        // Wait for both notifications
        boolean received = mqttLatch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive both MQTT notifications");

        // Verify we got 2 events
        assertEquals(2, receivedMqttEvents.size(), "Should have received 2 notifications");

        // Verify first event (document creation)
        NotificationEvent createEvent = receivedMqttEvents.get(0);
        assertEquals(NotificationEvent.EventType.DOCUMENT_CREATED, createEvent.getEventType());
        assertEquals(doc.getDocumentId(), createEvent.getDocumentId());
        assertEquals(1L, createEvent.getVersionNumber());

        // Verify second event (version creation)
        NotificationEvent updateEvent = receivedMqttEvents.get(1);
        assertEquals(NotificationEvent.EventType.VERSION_CREATED, updateEvent.getEventType());
        assertEquals(doc.getDocumentId(), updateEvent.getDocumentId());
        assertEquals(2L, updateEvent.getVersionNumber());
        assertEquals("Updated start list", updateEvent.getChangeDescription());

        // In a real client, these notifications would trigger data sync operations
        // The client would fetch the latest version and update its local cache
    }

    @Test
    @Order(6)
    void testMultipleTimersSynchronization() throws Exception {
        // Test scenario: Multiple race timers updating results simultaneously
        String regattaId = "MULTI_TIMER_SYNC_2025";

        // Subscribe to wildcard topic for all timers (simulate regatta monitoring)
        String wildcardTopic = "regatta/" + regattaId + "/results/+";
        mqttLatch = new CountDownLatch(3); // Expect 3 timer results

        setupMqttClient(wildcardTopic);

        // Timer 1 submits results
        DocumentRequest timer1Request = DocumentRequest.builder()
            .type(DocumentType.RACE_RESULTS)
            .regattaStartDate(java.time.LocalDate.of(2024, 5, 17))
            .regattaId(regattaId)
            .milestoneId("finish")
            .timer(org.rowtown.rms.rrr.domain.TimerRole.PRIMARY)
            .author("timer1@example.com")
            .description("Timer 1 results")
            .modelData(org.rowtown.rms.rrr.testutil.SampleData.raceResultsModel("1a"))
            .build();

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(timer1Request)))
            .andExpect(status().isCreated());

        // Timer 2 submits results
        DocumentRequest timer2Request = DocumentRequest.builder()
            .type(DocumentType.RACE_RESULTS)
            .regattaStartDate(java.time.LocalDate.of(2024, 5, 17))
            .regattaId(regattaId)
            .milestoneId("finish")
            .timer(org.rowtown.rms.rrr.domain.TimerRole.FIRST_BACKUP)
            .author("timer2@example.com")
            .description("Timer 2 results")
            .modelData(org.rowtown.rms.rrr.testutil.SampleData.raceResultsModel("1a"))
            .build();

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(timer2Request)))
            .andExpect(status().isCreated());

        // Timer 3 submits results
        DocumentRequest timer3Request = DocumentRequest.builder()
            .type(DocumentType.RACE_RESULTS)
            .regattaStartDate(java.time.LocalDate.of(2024, 5, 17))
            .regattaId(regattaId)
            .milestoneId("finish")
            .timer(org.rowtown.rms.rrr.domain.TimerRole.SECOND_BACKUP)
            .author("timer3@example.com")
            .description("Timer 3 results")
            .modelData(org.rowtown.rms.rrr.testutil.SampleData.raceResultsModel("1a"))
            .build();

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(timer3Request)))
            .andExpect(status().isCreated());

        // Wait for all 3 notifications
        boolean received = mqttLatch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive notifications from all 3 timers");

        // Verify all 3 events were received
        assertEquals(3, receivedMqttEvents.size(), "Should have received 3 timer notifications");

        // Verify timer IDs
        Set<String> timerIds = new HashSet<>();
        for (NotificationEvent event : receivedMqttEvents) {
            assertEquals(NotificationEvent.EventType.DOCUMENT_CREATED, event.getEventType());
            assertEquals(DocumentType.RACE_RESULTS, event.getDocumentType());
            assertEquals(regattaId, event.getRegattaId());
            timerIds.add(event.getTimerId());
        }

        assertEquals(3, timerIds.size(), "Should have 3 unique timer roles");
        assertTrue(timerIds.contains("PRIMARY"));
        assertTrue(timerIds.contains("FIRST_BACKUP"));
        assertTrue(timerIds.contains("SECOND_BACKUP"));
    }

    @Test
    @Order(7)
    void testWebhookUnsubscribe() throws Exception {
        String webhookUrl = "http://localhost:8089/unsubscribe-test";
        String secretKey = "unsub-secret";

        // Setup webhook
        stubFor(WireMock.post(urlEqualTo("/unsubscribe-test"))
            .willReturn(aResponse().withStatus(200)));

        // Subscribe
        WebhookSubscriptionRequest webhookRequest = WebhookSubscriptionRequest.builder()
            .regattaId("UNSUB_TEST_2025")
            .webhookUrl(webhookUrl)
            .secretKey(secretKey)
            .events(List.of("DOCUMENT_CREATED"))
            .build();

        MvcResult subResult = mockMvc.perform(post("/api/v1/webhooks/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(webhookRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        String subscriptionId = objectMapper.readTree(subResult.getResponse().getContentAsString())
            .get("subscriptionId").asText();

        // Create document - should trigger webhook
        DocumentRequest docRequest = DocumentRequest.builder()
            .type(DocumentType.START_LIST)
            .regattaStartDate(java.time.LocalDate.of(2024, 5, 17))
            .regattaId("UNSUB_TEST_2025")
            .author("unsub@example.com")
            .description("Test before unsubscribe")
            .modelData(org.rowtown.rms.rrr.testutil.SampleData.raceResultsModel("1a"))
            .build();

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(docRequest)))
            .andExpect(status().isCreated());

        Thread.sleep(1000);

        // Verify webhook was called (at least once)
        verify(postRequestedFor(urlEqualTo("/unsubscribe-test")));

        // Unsubscribe
        mockMvc.perform(delete("/api/v1/webhooks/" + subscriptionId))
            .andExpect(status().isNoContent());

        // Reset mock
        wireMockServer.resetRequests();

        // Create another document - should NOT trigger webhook
        DocumentRequest docRequest2 = DocumentRequest.builder()
            .type(DocumentType.START_LIST)
            .regattaStartDate(java.time.LocalDate.of(2024, 5, 17))
            .regattaId("UNSUB_TEST_2025_V2")
            .author("unsub@example.com")
            .description("Test after unsubscribe")
            .modelData(org.rowtown.rms.rrr.testutil.SampleData.raceResultsModel("1a"))
            .build();

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(docRequest2)))
            .andExpect(status().isCreated());

        Thread.sleep(1000);

        // Verify webhook was NOT called after unsubscribe (exactly 0 times)
        verify(0, postRequestedFor(urlEqualTo("/unsubscribe-test")));
    }

    /**
     * Helper method to setup MQTT client and subscription
     */
    private void setupMqttClient(String topic) throws Exception {
        mqttClient = new MqttClient("tcp://localhost:1883", "test-client-" + UUID.randomUUID());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);

        mqttClient.connect(options);

        mqttClient.subscribe(topic, 1, (t, message) -> {
            try {
                String payload = new String(message.getPayload());
                NotificationEvent event = objectMapper.readValue(payload, NotificationEvent.class);
                receivedMqttEvents.add(event);
                mqttLatch.countDown();
            } catch (Exception e) {
                fail("Failed to parse MQTT message: " + e.getMessage());
            }
        });

        // Wait a bit for subscription to be established
        Thread.sleep(500);
    }

    /**
     * Helper method to verify HMAC signature
     */
    private boolean verifyHmacSignature(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes());
            String expected = Base64.getEncoder().encodeToString(hash);
            return expected.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
