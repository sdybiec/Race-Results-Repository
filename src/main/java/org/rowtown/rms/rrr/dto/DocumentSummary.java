package org.rowtown.rms.rrr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.TimerRole;

import java.time.LocalDate;
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
    private LocalDate regattaStartDate;
    private String raceId;
    private String milestoneId;
    private TimerRole timer;
    private Long latestVersion;
    private LocalDateTime lastModified;
    private String author;
    private String description;
    private Set<String> tags;
    private Map<String, String> metadata;
}
