package org.rowtown.rms.rrr.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.rowtown.rms.rrr.config.EmbeddedMqttBrokerConfig;
import org.rowtown.rms.rrr.config.TestSecurityConfig;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.rowtown.rms.rrr.domain.TimerRole;
import org.rowtown.rms.rrr.dto.DocumentRequest;
import org.rowtown.rms.rrr.dto.DocumentResponse;
import org.rowtown.rms.rrr.testutil.SampleData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration test for complete document lifecycle.
 * Tests the full workflow: create -> update -> version -> compare -> rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({EmbeddedMqttBrokerConfig.class, TestSecurityConfig.class})
@Transactional
class DocumentLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void completeDocumentLifecycle() throws Exception {
        // Step 1: Create a new document
        DocumentRequest createRequest = DocumentRequest.builder()
            .type(DocumentType.RACE_RESULTS)
            .regattaId("INTEGRATION_TEST_2025")
            .regattaStartDate(java.time.LocalDate.of(2025, 5, 17))
            .milestoneId("Finish Line")
            .timer(TimerRole.PRIMARY)
            .author("test@example.com")
            .description("Integration test document")
            .tags(Set.of("test", "integration"))
            .metadata(Map.of("testKey", "testValue"))
            .modelData(SampleData.raceResultsPreliminary())
            .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        DocumentResponse createdDoc = objectMapper.readValue(
            createResult.getResponse().getContentAsString(),
            DocumentResponse.class);

        assertNotNull(createdDoc.getDocumentId());
        assertEquals(1L, createdDoc.getLatestVersion());

        Long documentId = createdDoc.getDocumentId();

        // Step 2: Retrieve the document
        mockMvc.perform(get("/api/v1/documents/" + documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentId").value(documentId))
            .andExpect(jsonPath("$.regattaId").value("INTEGRATION_TEST_2025"));

        // Step 3: Update the document to final results (creates version 2)
        // Ingest validation rejects models the generated TDI classes cannot load,
        // so updates must carry loadable TDI models.
        mockMvc.perform(put("/api/v1/documents/" + documentId)
                .param("changeDescription", "Final results")
                .param("format", SerializationFormat.XMI.name())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(SampleData.raceResultsFinal()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latestVersion").value(2L));

        // Step 4: Update again with a correction (creates version 3)
        mockMvc.perform(put("/api/v1/documents/" + documentId)
                .param("changeDescription", "Correction: bow 3 disqualified")
                .param("format", SerializationFormat.XMI.name())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(SampleData.raceResultsCorrected()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latestVersion").value(3L));

        // Step 5: List version history
        MvcResult versionsResult = mockMvc.perform(
                get("/api/v1/documents/" + documentId + "/versions")
                    .param("page", "0")
                    .param("size", "10"))
            .andExpect(status().isOk())
            .andReturn();

        String versionsJson = versionsResult.getResponse().getContentAsString();
        assertTrue(versionsJson.contains("\"versionNumber\":3"));
        assertTrue(versionsJson.contains("\"versionNumber\":2"));
        assertTrue(versionsJson.contains("\"versionNumber\":1"));

        // Step 6: Get specific version
        mockMvc.perform(get("/api/v1/documents/" + documentId + "/versions/2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versionNumber").value(2L));

        // Step 7: Compare version 1 (preliminary) with version 3 (corrected).
        // Both are real, loadable TDI models, so EMF Compare reports the actual
        // differences (race status, crew timing/eligibility, added penalty).
        MvcResult compareResult = mockMvc.perform(
                get("/api/v1/documents/" + documentId + "/versions/1/compare/3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalChanges").isNumber())
            .andReturn();

        org.rowtown.rms.rrr.dto.ModelDiff diff = objectMapper.readValue(
            compareResult.getResponse().getContentAsString(),
            org.rowtown.rms.rrr.dto.ModelDiff.class);
        assertTrue(diff.getTotalChanges() > 0,
            "preliminary and corrected results should differ");
        assertNotNull(diff.getChanges());
        assertFalse(diff.getChanges().isEmpty(), "diff should list the changes");

        // Step 8: Rollback to version 2
        mockMvc.perform(post("/api/v1/documents/" + documentId + "/versions/rollback")
                .param("targetVersion", "2")
                .param("description", "Rolling back to version 2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versionNumber").value(4L)); // New version created from rollback

        // Step 9: Verify document now has 4 versions
        mockMvc.perform(get("/api/v1/documents/" + documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latestVersion").value(4L));

        // Step 10: Search for the document
        mockMvc.perform(get("/api/v1/documents/search")
                .param("regattaId", "INTEGRATION_TEST_2025")
                .param("type", "RACE_RESULTS")
                .param("matchType", "EXACT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.results[0].documentId").value(documentId));

        // Step 11: Delete the document
        mockMvc.perform(delete("/api/v1/documents/" + documentId))
            .andExpect(status().isNoContent());

        // Step 12: Verify document is deleted
        mockMvc.perform(get("/api/v1/documents/" + documentId))
            .andExpect(status().isNotFound());
    }

    @Test
    void startListWorkflow() throws Exception {
        // Use a real regatta start list (XMI-serialized tdi:TimingRegatta, ~150 KB)
        // as the model payload instead of placeholder bytes.
        byte[] startListModel = SampleData.stotesburyStartList();
        assertTrue(startListModel.length > 1000, "sample start list should be loaded");

        // Create a Start List (only one allowed per regatta)
        DocumentRequest startListRequest = DocumentRequest.builder()
            .type(DocumentType.START_LIST)
            .regattaId("Stotesbury Cup Regatta")
            .regattaStartDate(java.time.LocalDate.of(2024, 5, 17))
            .author("regatta.admin@stotesburycup.org")
            .description("Stotesbury Cup Regatta 2024 - master start list")
            .tags(Set.of("official", "start-list"))
            .metadata(Map.of("raceCourse", "1500 Meter Head Course", "date", "2024-05-17"))
            .modelData(startListModel)
            .build();

        MvcResult result = mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(startListRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        DocumentResponse startList = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            DocumentResponse.class);

        assertNotNull(startList.getDocumentId());
        assertEquals(DocumentType.START_LIST, startList.getType());

        // Retrieve it and verify the full model payload round-trips through storage
        MvcResult fetched = mockMvc.perform(get("/api/v1/documents/" + startList.getDocumentId()))
            .andExpect(status().isOk())
            .andReturn();
        DocumentResponse retrieved = objectMapper.readValue(
            fetched.getResponse().getContentAsString(),
            DocumentResponse.class);
        assertArrayEquals(startListModel, retrieved.getModelData(),
            "stored start list model should round-trip unchanged");

        // Attempt to create another Start List for same regatta (should fail)
        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(startListRequest)))
            .andExpect(status().isConflict());
    }

    @Test
    void multipleTimersWorkflow() throws Exception {
        // Create Race Results for timer001
        DocumentRequest timer1Request = DocumentRequest.builder()
            .type(DocumentType.RACE_RESULTS)
            .regattaId("MULTI_TIMER_2025")
            .regattaStartDate(java.time.LocalDate.of(2025, 5, 17))
            .milestoneId("Finish Line")
            .timer(TimerRole.PRIMARY)
            .author("timer1@example.com")
            .description("Timer 1 results")
            .modelData(SampleData.raceResultsModel("1a"))
            .build();

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(timer1Request)))
            .andExpect(status().isCreated());

        // Create Race Results for timer002 (same milestone, different timer)
        DocumentRequest timer2Request = DocumentRequest.builder()
            .type(DocumentType.RACE_RESULTS)
            .regattaId("MULTI_TIMER_2025")
            .regattaStartDate(java.time.LocalDate.of(2025, 5, 17))
            .milestoneId("Finish Line")
            .timer(TimerRole.FIRST_BACKUP)
            .author("timer2@example.com")
            .description("Timer 2 results")
            .modelData(SampleData.raceResultsModel("1a"))
            .build();

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(timer2Request)))
            .andExpect(status().isCreated());

        // Search should find both documents
        mockMvc.perform(get("/api/v1/documents/search")
                .param("regattaId", "MULTI_TIMER_2025")
                .param("type", "RACE_RESULTS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2));
    }
}
