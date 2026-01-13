package org.rowtown.rms.rrr.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a tag associated with a document.
 */
@Entity
@Table(name = "tags",
    indexes = @Index(name = "idx_tag", columnList = "tag_name")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long tagId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "tag_name", nullable = false, length = 100)
    private String tagName;
}
