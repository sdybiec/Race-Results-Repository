package org.rowtown.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing document ownership for authorization purposes.
 */
@Entity
@Table(name = "document_ownership",
    indexes = @Index(name = "idx_owner", columnList = "owner_user_id")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentOwnership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ownership_id")
    private Long ownershipId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "owner_user_id", nullable = false)
    private String ownerUserId;
}
