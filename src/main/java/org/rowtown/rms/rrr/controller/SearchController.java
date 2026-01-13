package org.rowtown.rms.rrr.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.dto.SearchQuery;
import org.rowtown.rms.rrr.dto.SearchResults;
import org.rowtown.rms.rrr.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * REST controller for document search operations.
 */
@RestController
@RequestMapping("/api/v1/documents/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Document search API")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(summary = "Search documents", description = "Search documents based on various criteria")
    public ResponseEntity<SearchResults> search(
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String regattaId,
            @RequestParam(required = false) String regattaName,
            @RequestParam(required = false) DocumentType type,
            @RequestParam(required = false) String timerId,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String metadataKey,
            @RequestParam(required = false) String metadataValue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "EXACT") SearchQuery.MatchType matchType) {

        SearchQuery query = SearchQuery.builder()
            .documentId(documentId)
            .regattaId(regattaId)
            .regattaName(regattaName)
            .documentType(type)
            .timerId(timerId)
            .author(author)
            .description(description)
            .tags(tags != null ? Set.of(tags.split(",")) : null)
            .metadataKey(metadataKey)
            .metadataValue(metadataValue)
            .page(page)
            .size(size)
            .matchType(matchType)
            .build();

        SearchResults results = searchService.search(query);
        return ResponseEntity.ok(results);
    }

    @PostMapping
    @Operation(summary = "Advanced search", description = "Advanced search with POST body for complex queries")
    public ResponseEntity<SearchResults> advancedSearch(@RequestBody SearchQuery query) {
        SearchResults results = searchService.search(query);
        return ResponseEntity.ok(results);
    }
}
