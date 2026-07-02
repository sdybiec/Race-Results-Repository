package org.rowtown.rms.rrr.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.domain.DocumentType;

import java.util.Set;

/**
 * DTO for document search queries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Search query parameters for finding documents")
public class SearchQuery {

    @Schema(description = "Document ID to search for",
            example = "12345")
    private String documentId;

    @Schema(description = "Regatta ID to filter by",
            example = "Stotesbury Cup Regatta")
    private String regattaId;

    @Schema(description = "Regatta name to search for",
            example = "Stotesbury Cup Regatta")
    private String regattaName;

    @Schema(description = "Type of document to filter by",
            example = "RACE_RESULTS")
    private DocumentType documentType;

    @Schema(description = "Timer device ID to filter by",
            example = "TIMER-001")
    private String timerId;

    @Schema(description = "Author to filter by",
            example = "john.doe@rowing.org")
    private String author;

    @Schema(description = "Search in document descriptions",
            example = "heat 1")
    private String description;

    @Schema(description = "Tags to filter by",
            example = "[\"finals\", \"mens-8\"]")
    private Set<String> tags;

    @Schema(description = "Metadata key to search for")
    private String metadataKey;

    @Schema(description = "Metadata value to search for")
    private String metadataValue;

    @Schema(description = "Page number (0-indexed)",
            example = "0",
            defaultValue = "0")
    private Integer page;

    @Schema(description = "Number of results per page",
            example = "10",
            defaultValue = "10")
    private Integer size;

    @Schema(description = "Type of matching to perform",
            example = "PARTIAL",
            defaultValue = "EXACT")
    private MatchType matchType;

    @Schema(description = "Match type for search queries")
    public enum MatchType {
        @Schema(description = "Exact match only")
        EXACT,
        @Schema(description = "Partial string matching")
        PARTIAL,
        @Schema(description = "Wildcard pattern matching (*, ?)")
        WILDCARD
    }
}
