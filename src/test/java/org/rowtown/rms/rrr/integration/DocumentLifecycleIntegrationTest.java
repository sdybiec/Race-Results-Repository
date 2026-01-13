package org.rowtown.rms.rrr.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.rowtown.rms.rrr.dto.DocumentRequest;
import org.rowtown.rms.rrr.dto.DocumentResponse;
import org.rowtown.rms.rrr.dto.VersionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for complete document lifecycle.
 * Tests the full workflow: create -> update -> version -> compare -> rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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
            .timerId("timer001")
            .milestoneId("finish")
            .versionType("primary")
            .author("test@example.com")
            .description("Integration test document")
            .tags(Set.of("test", "integration"))
            .metadata(Map.of("testKey", "testValue"))
            .modelData("Initial model data".getBytes())
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

        // Step 3: Update the document (creates version 2)
        mockMvc.perform(put("/api/v1/documents/" + documentId)
                .param("changeDescription", "Second version")
                .param("format", SerializationFormat.XMI.name())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content("Updated model data".getBytes()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latestVersion").value(2L));

        // Step 4: Update again (creates version 3)
        mockMvc.perform(put("/api/v1/documents/" + documentId)
                .param("changeDescription", "Third version")
                .param("format", SerializationFormat.XMI.name())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content("Third version model data".getBytes()))
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

        // Step 7: Compare versions
        mockMvc.perform(get("/api/v1/documents/" + documentId + "/versions/1/compare/3"))
            .andExpect(status().isOk());

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
        // Create a Start List (only one allowed per regatta)
        DocumentRequest startListRequest = DocumentRequest.builder()
            .type(DocumentType.START_LIST)
            .regattaId("STARTLIST_TEST_2025")
            .author("admin@example.com")
            .description("Test start list")
            .tags(Set.of("official"))
            .modelData("Start list data".getBytes())
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
            .timerId("timer001")
            .milestoneId("finish")
            .versionType("primary")
            .author("timer1@example.com")
            .description("Timer 1 results")
            .modelData("Timer 1 data".getBytes())
            .build();

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(timer1Request)))
            .andExpect(status().isCreated());

        // Create Race Results for timer002 (same milestone, different timer)
        DocumentRequest timer2Request = DocumentRequest.builder()
            .type(DocumentType.RACE_RESULTS)
            .regattaId("MULTI_TIMER_2025")
            .timerId("timer002")
            .milestoneId("finish")
            .versionType("firstBackup")
            .author("timer2@example.com")
            .description("Timer 2 results")
            .modelData("Timer 2 data".getBytes())
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
