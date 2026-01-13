package org.rowtown.rms.rrr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.entity.Document;
import org.rowtown.rms.rrr.dto.SearchQuery;
import org.rowtown.rms.rrr.dto.SearchResults;
import org.rowtown.rms.rrr.repository.DocumentMetadataRepository;
import org.rowtown.rms.rrr.repository.DocumentRepository;
import org.rowtown.rms.rrr.repository.DocumentTagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SearchService.
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentMetadataRepository metadataRepository;

    @Mock
    private DocumentTagRepository tagRepository;

    @InjectMocks
    private SearchService searchService;

    private Document testDocument;
    private SearchQuery testQuery;

    @BeforeEach
    void setUp() {
        testDocument = Document.builder()
            .documentId(1L)
            .documentType(DocumentType.START_LIST)
            .regattaId("HEAD2025")
            .author("admin@example.com")
            .description("Head of the Charles 2025")
            .createdAt(LocalDateTime.now())
            .latestVersion(1L)
            .build();

        testQuery = SearchQuery.builder()
            .regattaId("HEAD2025")
            .documentType(DocumentType.START_LIST)
            .page(0)
            .size(10)
            .matchType(SearchQuery.MatchType.EXACT)
            .build();
    }

    @Test
    void search_ExactMatch() {
        // Arrange
        List<Document> documents = Arrays.asList(testDocument);
        Page<Document> page = new PageImpl<>(documents);

        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(page);
        when(metadataRepository.findByDocument_DocumentId(1L))
            .thenReturn(Collections.emptyList());
        when(tagRepository.findByDocument_DocumentId(1L))
            .thenReturn(Collections.emptyList());

        // Act
        SearchResults results = searchService.search(testQuery);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.getTotal());
        assertEquals(1, results.getResults().size());
        assertEquals("HEAD2025", results.getResults().get(0).getRegattaId());
    }

    @Test
    void search_PartialMatch() {
        // Arrange
        testQuery.setMatchType(SearchQuery.MatchType.PARTIAL);
        testQuery.setRegattaId("HEAD");

        List<Document> documents = Arrays.asList(testDocument);
        Page<Document> page = new PageImpl<>(documents);

        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(page);
        when(metadataRepository.findByDocument_DocumentId(1L))
            .thenReturn(Collections.emptyList());
        when(tagRepository.findByDocument_DocumentId(1L))
            .thenReturn(Collections.emptyList());

        // Act
        SearchResults results = searchService.search(testQuery);

        // Assert
        assertNotNull(results);
        assertTrue(results.getTotal() > 0);
    }

    @Test
    void search_WildcardMatch() {
        // Arrange
        testQuery.setMatchType(SearchQuery.MatchType.WILDCARD);
        testQuery.setRegattaId("HEAD*");

        List<Document> documents = Arrays.asList(testDocument);
        Page<Document> page = new PageImpl<>(documents);

        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(page);
        when(metadataRepository.findByDocument_DocumentId(1L))
            .thenReturn(Collections.emptyList());
        when(tagRepository.findByDocument_DocumentId(1L))
            .thenReturn(Collections.emptyList());

        // Act
        SearchResults results = searchService.search(testQuery);

        // Assert
        assertNotNull(results);
        assertTrue(results.getTotal() > 0);
    }

    @Test
    void search_ByDescription() {
        // Arrange
        testQuery.setDescription("Charles");

        List<Document> documents = Arrays.asList(testDocument);
        Page<Document> page = new PageImpl<>(documents);

        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(page);
        when(metadataRepository.findByDocument_DocumentId(1L))
            .thenReturn(Collections.emptyList());
        when(tagRepository.findByDocument_DocumentId(1L))
            .thenReturn(Collections.emptyList());

        // Act
        SearchResults results = searchService.search(testQuery);

        // Assert
        assertNotNull(results);
        assertTrue(results.getTotal() > 0);
    }

    @Test
    void search_NoResults() {
        // Arrange
        testQuery.setRegattaId("NONEXISTENT");

        Page<Document> emptyPage = new PageImpl<>(Collections.emptyList());

        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(emptyPage);

        // Act
        SearchResults results = searchService.search(testQuery);

        // Assert
        assertNotNull(results);
        assertEquals(0, results.getTotal());
        assertTrue(results.getResults().isEmpty());
    }

    @Test
    void search_WithPagination() {
        // Arrange
        testQuery.setPage(1);
        testQuery.setSize(5);

        List<Document> documents = Arrays.asList(testDocument);
        Page<Document> page = new PageImpl<>(documents);

        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(page);
        when(metadataRepository.findByDocument_DocumentId(1L))
            .thenReturn(Collections.emptyList());
        when(tagRepository.findByDocument_DocumentId(1L))
            .thenReturn(Collections.emptyList());

        // Act
        SearchResults results = searchService.search(testQuery);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.getPage());
        assertEquals(5, results.getPageSize());
    }
}
