package org.rowtown.rms.rrr.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.Operation;
import org.rowtown.rms.rrr.domain.UserRole;

/**
 * Entity representing an Access Control List entry for regatta-level permissions.
 */
@Entity
@Table(name = "acl_entries",
    indexes = @Index(name = "idx_acl_regatta", columnList = "regattaId")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AclEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "acl_id")
    private Long aclId;

    @Column(name = "regattaId", nullable = false)
    private String regattaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private DocumentType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false)
    private Operation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "allowed", nullable = false)
    @Builder.Default
    private Boolean allowed = true;
}
