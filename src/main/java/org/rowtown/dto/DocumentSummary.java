package org.rowtown.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.domain.DocumentType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * DTO for document summary in search results (without model data).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSummary {
    private Long documentId;
    private DocumentType type;
    private String regattaId;
    private String timerId;
    private Long latestVersion;
    private LocalDateTime lastModified;
    private String author;
    private String description;
    private Set<String> tags;
    private Map<String, String> metadata;
}
