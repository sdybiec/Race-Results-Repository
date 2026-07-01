package org.rowtown.rms.rrr.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for search results with pagination info.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Paginated search results")
public class SearchResults {

    @Schema(description = "Total number of matching documents",
            example = "42")
    private Long total;

    @Schema(description = "Current page number (0-indexed)",
            example = "0")
    private Integer page;

    @Schema(description = "Number of results per page",
            example = "10")
    private Integer pageSize;

    @Schema(description = "List of matching documents")
    private List<DocumentSummary> results;
}
