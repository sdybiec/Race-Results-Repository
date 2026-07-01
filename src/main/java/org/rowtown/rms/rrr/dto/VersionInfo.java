package org.rowtown.rms.rrr.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Version metadata without the full document data")
public class VersionInfo {

    @Schema(description = "Unique version identifier",
            example = "67890")
    private Long versionId;

    @Schema(description = "Document this version belongs to",
            example = "12345")
    private Long documentId;

    @Schema(description = "Version number (sequential)",
            example = "5")
    private Long versionNumber;

    @Schema(description = "When this version was created",
            example = "2025-01-18T10:30:00")
    private LocalDateTime timestamp;

    @Schema(description = "Who created this version",
            example = "john.doe@rowing.org")
    private String author;

    @Schema(description = "Description of changes in this version",
            example = "Updated race times after timing correction")
    private String changeDescription;

    @Schema(description = "SHA-256 checksum of model data",
            example = "a3b5c7d9e1f2a4b6c8d0e2f4a6b8c0d2e4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4")
    private String checksum;

    @Schema(description = "Size of model data in bytes",
            example = "2048")
    private Long size;
}
