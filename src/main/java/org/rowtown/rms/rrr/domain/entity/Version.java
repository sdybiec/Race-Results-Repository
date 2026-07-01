package org.rowtown.rms.rrr.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.domain.SerializationFormat;

import java.time.LocalDateTime;

/**
 * Entity representing a specific version of a document.
 * Each version contains a complete snapshot of the EMF model at that point in time.
 */
@Entity
@Table(name = "versions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "version_number"}),
    indexes = @Index(name = "idx_timestamp", columnList = "timestamp")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Version {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id")
    private Long versionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "version_number", nullable = false)
    private Long versionNumber;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "author", nullable = false)
    private String author;

    @Column(name = "change_description", length = 2000)
    private String changeDescription;

    // Portable LOB mapping: Hibernate emits the dialect's large-binary type
    // (BLOB on H2, longblob on MariaDB) instead of a vendor-specific columnDefinition.
    @Lob
    @Column(name = "model_snapshot", nullable = false)
    private byte[] modelSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_format", nullable = false)
    private SerializationFormat snapshotFormat;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
