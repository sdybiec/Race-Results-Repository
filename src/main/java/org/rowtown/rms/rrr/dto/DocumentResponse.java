package org.rowtown.rms.rrr.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Complete document information with version control metadata")
public class DocumentResponse {

    @Schema(description = "Unique document identifier",
            example = "12345")
    private Long documentId;

    @Schema(description = "Type of document",
            example = "RACE_RESULTS")
    private DocumentType type;

    @Schema(description = "Regatta identifier",
            example = "Stotesbury Cup Regatta")
    private String regattaId;

    @Schema(description = "Timer device identifier",
            example = "TIMER-001")
    private String timerId;

    @Schema(description = "Milestone identifier",
            example = "HEAT-1")
    private String milestoneId;

    @Schema(description = "Version type",
            example = "OFFICIAL")
    private String versionType;

    @Schema(description = "Document author",
            example = "john.doe@rowing.org")
    private String author;

    @Schema(description = "Timestamp when document was created",
            example = "2025-01-18T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Latest version number",
            example = "5")
    private Long latestVersion;

    @Schema(description = "Document description",
            example = "Final race results for Heat 1")
    private String description;

    @Schema(description = "Document tags",
            example = "[\"finals\", \"mens-8\"]")
    private Set<String> tags;

    @Schema(description = "Custom metadata")
    private Map<String, String> metadata;

    @Schema(description = "Binary EMF model data",
            type = "string",
            format = "binary")
    private byte[] modelData;
}
