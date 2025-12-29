package org.rowtown.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing key-value metadata associated with a document.
 */
@Entity
@Table(name = "metadata",
    indexes = @Index(name = "idx_key_value", columnList = "meta_key, meta_value")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metadata_id")
    private Long metadataId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "meta_key", nullable = false)
    private String key;

    @Column(name = "meta_value", columnDefinition = "TEXT")
    private String value;
}
