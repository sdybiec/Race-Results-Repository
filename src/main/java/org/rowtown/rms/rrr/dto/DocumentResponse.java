package org.rowtown.rms.rrr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.domain.DocumentType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * DTO for document response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {
    private Long documentId;
    private DocumentType type;
    private String regattaId;
    private String timerId;
    private String milestoneId;
    private String versionType;
    private String author;
    private LocalDateTime createdAt;
    private Long latestVersion;
    private String description;
    private Set<String> tags;
    private Map<String, String> metadata;
    private byte[] modelData; // EMF model serialized bytes
}
