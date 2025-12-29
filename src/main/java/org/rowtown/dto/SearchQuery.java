package org.rowtown.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.domain.DocumentType;

import java.util.Set;

/**
 * DTO for document search queries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchQuery {
    private String documentId;
    private String regattaId;
    private String regattaName;
    private DocumentType documentType;
    private String timerId;
    private String author;
    private String description;
    private Set<String> tags;
    private String metadataKey;
    private String metadataValue;

    // Pagination
    private Integer page;
    private Integer size;

    // Match type
    private MatchType matchType;

    public enum MatchType {
        EXACT,
        PARTIAL,
        WILDCARD
    }
}
