package org.rowtown.rms.rrr.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.rowtown.rms.rrr.config.EmbeddedMqttBrokerConfig;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.rowtown.rms.rrr.domain.entity.Document;
import org.rowtown.rms.rrr.domain.entity.Version;
import org.rowtown.rms.rrr.dto.DocumentRequest;
import org.rowtown.rms.rrr.dto.SearchQuery;
import org.rowtown.rms.rrr.dto.SearchResults;
import org.rowtown.rms.rrr.service.DocumentManagerService;
import org.rowtown.rms.rrr.service.SearchService;
import org.rowtown.rms.rrr.service.VersionControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for the Race Results Repository.
 * Run with: mvn test -Dperformance.tests.enabled=true
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedMqttBrokerConfig.class)
@EnabledIfSystemProperty(named = "performance.tests.enabled", matches = "true")
class PerformanceTest {

    @Autowired
    private DocumentManagerService documentService;

    @Autowired
    private VersionControlService versionService;

    @Autowired
    private SearchService searchService;

    @Test
    @Transactional
    void testCreateDocumentPerformance() {
        int documentCount = 100;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < documentCount; i++) {
            DocumentRequest request = DocumentRequest.builder()
                .type(DocumentType.RACE_RESULTS)
                .regattaId("PERF_TEST")
                .timerId("timer" + String.format("%03d", i))
                .milestoneId("finish")
                .author("test@example.com")
                .description("Performance test document " + i)
                .modelData(("Test model data " + i).getBytes())
                .build();

            documentService.createDocument(request);
        }

        long duration = System.currentTimeMillis() - startTime;
        double avgTime = duration / (double) documentCount;

        System.out.printf("Created %d documents in %d ms (avg: %.2f ms/doc)%n",
            documentCount, duration, avgTime);

        // Assert performance targets
        assertTrue(avgTime < 500, "Average document creation should be < 500ms");
    }

    @Test
    @Transactional
    void testCreateVersionPerformance() {
        // Create a document first
        DocumentRequest request = DocumentRequest.builder()
            .type(DocumentType.RACE_RESULTS)
            .regattaId("PERF_TEST_VERSIONS")
            .timerId("timer001")
            .milestoneId("finish")
            .author("test@example.com")
            .modelData("Initial data".getBytes())
            .build();

        var doc = documentService.createDocument(request);
        Long documentId = doc.getDocumentId();

        int versionCount = 50;
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= versionCount; i++) {
            byte[] modelData = ("Version " + i + " data").getBytes();
            versionService.createVersion(
                documentId,
                modelData,
                "test@example.com",
                "Version " + i,
                SerializationFormat.XMI
            );
        }

        long duration = System.currentTimeMillis() - startTime;
        double avgTime = duration / (double) versionCount;

        System.out.printf("Created %d versions in %d ms (avg: %.2f ms/version)%n",
            versionCount, duration, avgTime);

        // Assert performance targets
        assertTrue(avgTime < 300, "Average version creation should be < 300ms");
    }

    @Test
    @Transactional
    void testSearchPerformance() {
        // Create test documents
        int documentCount = 200;
        for (int i = 0; i < documentCount; i++) {
            DocumentRequest request = DocumentRequest.builder()
                .type(DocumentType.RACE_RESULTS)
                .regattaId("SEARCH_PERF_TEST")
                .timerId("timer" + String.format("%03d", i))
                .milestoneId("finish")
                .author("test@example.com")
                .description("Search performance test document " + i)
                .tags(Set.of("test", "performance"))
                .metadata(Map.of("index", String.valueOf(i)))
                .modelData(("Data " + i).getBytes())
                .build();

            documentService.createDocument(request);
        }

        // Test search performance
        int searchIterations = 100;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < searchIterations; i++) {
            SearchQuery query = SearchQuery.builder()
                .regattaId("SEARCH_PERF_TEST")
                .documentType(DocumentType.RACE_RESULTS)
                .page(0)
                .size(10)
                .build();

            SearchResults results = searchService.search(query);
            assertNotNull(results);
        }

        long duration = System.currentTimeMillis() - startTime;
        double avgTime = duration / (double) searchIterations;

        System.out.printf("Executed %d searches in %d ms (avg: %.2f ms/search)%n",
            searchIterations, duration, avgTime);

        // Assert performance targets
        assertTrue(avgTime < 200, "Average search time should be < 200ms");
    }

    @Test
    @Transactional
    void testConcurrentDocumentCreation() throws InterruptedException, ExecutionException {
        int threadCount = 10;
        int documentsPerThread = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Long>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            Future<Long> future = executor.submit(() -> {
                long threadStart = System.currentTimeMillis();

                for (int i = 0; i < documentsPerThread; i++) {
                    DocumentRequest request = DocumentRequest.builder()
                        .type(DocumentType.RACE_RESULTS)
                        .regattaId("CONCURRENT_TEST")
                        .timerId(String.format("thread%02d_doc%02d", threadId, i))
                        .milestoneId("finish")
                        .author("test@example.com")
                        .modelData(("Thread " + threadId + " doc " + i).getBytes())
                        .build();

                    documentService.createDocument(request);
                }

                return System.currentTimeMillis() - threadStart;
            });

            futures.add(future);
        }

        // Wait for all threads to complete
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        long totalDuration = System.currentTimeMillis() - startTime;
        int totalDocuments = threadCount * documentsPerThread;

        // Get max thread time
        long maxThreadTime = futures.stream()
            .map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return 0L;
                }
            })
            .max(Long::compare)
            .orElse(0L);

        System.out.printf("Created %d documents concurrently (%d threads) in %d ms%n",
            totalDocuments, threadCount, totalDuration);
        System.out.printf("Max thread time: %d ms%n", maxThreadTime);
        System.out.printf("Throughput: %.2f docs/sec%n",
            (totalDocuments * 1000.0) / totalDuration);

        // Assert concurrent operations succeeded
        assertEquals(threadCount, futures.size());
        for (Future<Long> future : futures) {
            assertTrue(future.isDone());
        }
    }

    @Test
    @Transactional
    void testVersionComparisonPerformance() {
        // Create document with multiple versions
        DocumentRequest request = DocumentRequest.builder()
            .type(DocumentType.RACE_RESULTS)
            .regattaId("COMPARE_PERF_TEST")
            .timerId("timer001")
            .milestoneId("finish")
            .author("test@example.com")
            .modelData("Initial data".getBytes())
            .build();

        var doc = documentService.createDocument(request);
        Long documentId = doc.getDocumentId();

        // Create 10 versions
        for (int i = 1; i <= 10; i++) {
            byte[] modelData = ("Version " + i + " with more data").getBytes();
            versionService.createVersion(
                documentId,
                modelData,
                "test@example.com",
                "Version " + i,
                SerializationFormat.XMI
            );
        }

        // Test comparison performance
        int comparisonCount = 50;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < comparisonCount; i++) {
            Version v1 = versionService.getVersion(documentId, 1L);
            Version v2 = versionService.getVersion(documentId, 10L);
            assertNotNull(v1);
            assertNotNull(v2);
        }

        long duration = System.currentTimeMillis() - startTime;
        double avgTime = duration / (double) comparisonCount;

        System.out.printf("Executed %d version retrievals in %d ms (avg: %.2f ms)%n",
            comparisonCount, duration, avgTime);

        // Assert performance targets
        assertTrue(avgTime < 100, "Average version retrieval should be < 100ms");
    }
}
