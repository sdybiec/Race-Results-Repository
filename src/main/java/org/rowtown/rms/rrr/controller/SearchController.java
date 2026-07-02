package org.rowtown.rms.rrr.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.dto.SearchQuery;
import org.rowtown.rms.rrr.dto.SearchResults;
import org.rowtown.rms.rrr.service.SearchService;
import org.springframework.http.MediaType;
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
    @Operation(
        summary = "Search documents",
        description = "Search for documents using query parameters. Supports exact, partial, and wildcard matching."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Search completed successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SearchResults.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid search parameters"
        )
    })
    public ResponseEntity<SearchResults> search(
            @Parameter(description = "Document ID to search for", example = "12345")
            @RequestParam(required = false) String documentId,
            @Parameter(description = "Filter by regatta ID", example = "Stotesbury Cup Regatta")
            @RequestParam(required = false) String regattaId,
            @Parameter(description = "Search by regatta name", example = "Stotesbury")
            @RequestParam(required = false) String regattaName,
            @Parameter(description = "Filter by document type", example = "RACE_RESULTS")
            @RequestParam(required = false) DocumentType type,
            @Parameter(description = "Filter by timer device ID", example = "TIMER-001")
            @RequestParam(required = false) String timerId,
            @Parameter(description = "Filter by author", example = "john.doe@rowing.org")
            @RequestParam(required = false) String author,
            @Parameter(description = "Search in document descriptions", example = "heat 1")
            @RequestParam(required = false) String description,
            @Parameter(description = "Filter by tags (comma-separated)", example = "finals,mens-8")
            @RequestParam(required = false) String tags,
            @Parameter(description = "Search for specific metadata key")
            @RequestParam(required = false) String metadataKey,
            @Parameter(description = "Search for specific metadata value")
            @RequestParam(required = false) String metadataValue,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Match type: EXACT, PARTIAL, or WILDCARD", example = "PARTIAL")
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
    @Operation(
        summary = "Advanced search",
        description = "Perform complex searches using a JSON query body. Useful for searches with multiple tags, metadata filters, or complex criteria."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Search completed successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SearchResults.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid search query"
        )
    })
    public ResponseEntity<SearchResults> advancedSearch(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Advanced search query",
            required = true,
            content = @Content(
                schema = @Schema(implementation = SearchQuery.class),
                examples = @ExampleObject(
                    name = "Find a regatta's start list",
                    value = """
                    {
                      "regattaId": "Stotesbury Cup Regatta",
                      "documentType": "START_LIST",
                      "matchType": "PARTIAL",
                      "page": 0,
                      "size": 20
                    }
                    """
                )
            )
        )
        @RequestBody SearchQuery query) {
        SearchResults results = searchService.search(query);
        return ResponseEntity.ok(results);
    }
}
