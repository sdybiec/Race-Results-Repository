package org.rowtown.rms.rrr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing the differences between two model versions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelDiff {
    private Integer totalChanges;
    private List<String> changes;
}
