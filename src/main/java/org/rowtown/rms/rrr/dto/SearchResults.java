package org.rowtown.rms.rrr.dto;

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
public class SearchResults {
    private Long total;
    private Integer page;
    private Integer pageSize;
    private List<DocumentSummary> results;
}
