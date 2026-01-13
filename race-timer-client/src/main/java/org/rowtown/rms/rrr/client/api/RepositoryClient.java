package org.rowtown.rms.rrr.client.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for communicating with the Race Results Repository.
 */
@Slf4j
public class RepositoryClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String jwtToken;

    public RepositoryClient(String baseUrl, String jwtToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.jwtToken = jwtToken;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .build();
    }

    /**
     * Set JWT token for authentication.
     */
    public void setJwtToken(String jwtToken) {
        this.jwtToken = jwtToken;
    }

    /**
     * Get Start List for a regatta.
     */
    public DocumentResponse getStartList(String regattaId) throws IOException {
        String url = baseUrl + "/api/v1/documents/search?regattaId=" + regattaId +
                     "&type=START_LIST&matchType=EXACT";

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + jwtToken)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get start list: " + response);
            }

            SearchResults results = objectMapper.readValue(
                response.body().string(), SearchResults.class);

            if (results.getResults() != null && !results.getResults().isEmpty()) {
                // Get full document with model data
                Long docId = results.getResults().get(0).getDocumentId();
                return getDocument(docId);
            }

            return null;
        }
    }

    /**
     * Get a document by ID.
     */
    public DocumentResponse getDocument(Long documentId) throws IOException {
        String url = baseUrl + "/api/v1/documents/" + documentId;

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + jwtToken)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get document: " + response);
            }

            return objectMapper.readValue(response.body().string(), DocumentResponse.class);
        }
    }

    /**
     * Create a new document.
     */
    public DocumentResponse createDocument(DocumentRequest documentRequest) throws IOException {
        String url = baseUrl + "/api/v1/documents";
        String json = objectMapper.writeValueAsString(documentRequest);

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + jwtToken)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create document: " + response.code() + " " + response.message());
            }

            return objectMapper.readValue(response.body().string(), DocumentResponse.class);
        }
    }

    /**
     * Update a document (creates new version).
     */
    public DocumentResponse updateDocument(Long documentId, byte[] modelData, String changeDescription) throws IOException {
        String url = baseUrl + "/api/v1/documents/" + documentId +
                     "?changeDescription=" + changeDescription +
                     "&format=JSON";

        RequestBody body = RequestBody.create(modelData, MediaType.get("application/octet-stream"));
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + jwtToken)
            .put(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to update document: " + response.code() + " " + response.message());
            }

            return objectMapper.readValue(response.body().string(), DocumentResponse.class);
        }
    }

    /**
     * Check if server is reachable.
     */
    public boolean isServerReachable() {
        try {
            String url = baseUrl + "/api/v1/documents/search?page=0&size=1";
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            log.debug("Server not reachable: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Simple DTOs for API communication.
     */
    public static class DocumentResponse {
        public Long documentId;
        public String type;
        public String regattaId;
        public String timerId;
        public String milestoneId;
        public String versionType;
        public String author;
        public String description;
        public Long latestVersion;
        public byte[] modelData;
    }

    public static class DocumentRequest {
        public String type;
        public String regattaId;
        public String timerId;
        public String milestoneId;
        public String versionType;
        public String author;
        public String description;
        public java.util.Set<String> tags;
        public Map<String, String> metadata;
        public byte[] modelData;
    }

    public static class SearchResults {
        public Long total;
        public Integer page;
        public Integer pageSize;
        public List<DocumentSummary> results;
    }

    public static class DocumentSummary {
        public Long documentId;
        public String type;
        public String regattaId;
        public String timerId;
        public Long latestVersion;
    }
}
