package org.rowtown.rms.rrr.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.domain.DocumentType;

import java.util.Map;
import java.util.Set;

/**
 * DTO for creating a new document.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for creating a new race timing document")
public class DocumentRequest {

    @Schema(description = "Type of document",
            example = "RACE_RESULTS",
            required = true)
    private DocumentType type;

    @Schema(description = "Unique identifier for the regatta",
            example = "Stotesbury Cup Regatta",
            required = true)
    private String regattaId;

    @Schema(description = "Identifier for the timing device that created this document",
            example = "TIMER-001")
    private String timerId;

    @Schema(description = "Milestone identifier (e.g., heat number, race number)",
            example = "HEAT-1")
    private String milestoneId;

    @Schema(description = "Version type or category",
            example = "OFFICIAL")
    private String versionType;

    @Schema(description = "Author or creator of the document",
            example = "john.doe@rowing.org",
            required = true)
    private String author;

    @Schema(description = "Human-readable description of the document",
            example = "Final race results for Heat 1")
    private String description;

    @Schema(description = "Set of tags for categorization",
            example = "[\"finals\", \"mens-8\"]")
    private Set<String> tags;

    @Schema(description = "Custom metadata as key-value pairs")
    private Map<String, String> metadata;

    @Schema(description = "Binary EMF model data (XMI or binary format)",
            type = "string",
            format = "binary")
    private byte[] modelData;
}
