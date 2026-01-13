package org.rowtown.rms.rrr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for version information (without the full model data).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VersionInfo {
    private Long versionId;
    private Long documentId;
    private Long versionNumber;
    private LocalDateTime timestamp;
    private String author;
    private String changeDescription;
    private String checksum;
    private Long size;
}
