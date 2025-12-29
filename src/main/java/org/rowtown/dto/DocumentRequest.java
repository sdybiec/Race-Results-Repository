package org.rowtown.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.domain.DocumentType;

import java.util.Map;
import java.util.Set;

/**
 * DTO for creating a new document.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentRequest {
    private DocumentType type;
    private String regattaId;
    private String timerId;
    private String milestoneId;
    private String versionType;
    private String author;
    private String description;
    private Set<String> tags;
    private Map<String, String> metadata;
    private byte[] modelData; // EMF model serialized bytes
}
