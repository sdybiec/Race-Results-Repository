package org.rowtown.rms.rrr.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.TimerRole;

import java.time.LocalDate;
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

    @Schema(description = "Regatta name. Combined with regattaStartDate this identifies the regatta.",
            example = "Stotesbury Cup Regatta",
            required = true)
    private String regattaId;

    @Schema(description = "Start date of the regatta (ISO-8601). Part of the regatta key, since "
            + "regattas are periodic (typically annual) events that reuse the same name each year.",
            example = "2024-05-17",
            required = true)
    private LocalDate regattaStartDate;

    @Schema(description = "Timing milestone (Race Results only), e.g. \"Start Line\", \"500m\", \"Finish Line\". "
            + "Required for RACE_RESULTS; part of its key.",
            example = "Finish Line")
    private String milestoneId;

    @Schema(description = "Which redundant timer produced these results (Race Results only). "
            + "Required for RACE_RESULTS; part of its key.",
            example = "PRIMARY")
    private TimerRole timer;

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
