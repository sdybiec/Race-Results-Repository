package org.rowtown.rms.rrr.repository;

import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.Operation;
import org.rowtown.rms.rrr.domain.UserRole;
import org.rowtown.rms.rrr.domain.entity.AclEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for AclEntry entity operations.
 */
@Repository
public interface AclEntryRepository extends JpaRepository<AclEntry, Long> {

    /**
     * Find all ACL entries for a regatta.
     */
    List<AclEntry> findByRegattaId(String regattaId);

    /**
     * Find specific ACL entry.
     */
    Optional<AclEntry> findByRegattaIdAndResourceTypeAndOperationAndRole(
        String regattaId, DocumentType resourceType, Operation operation, UserRole role);

    /**
     * Check if an operation is allowed for a role on a resource type in a regatta.
     */
    default boolean isAllowed(String regattaId, DocumentType resourceType,
                            Operation operation, UserRole role) {
        return findByRegattaIdAndResourceTypeAndOperationAndRole(
            regattaId, resourceType, operation, role)
            .map(AclEntry::getAllowed)
            .orElse(false);
    }

    /**
     * Find all ACL entries for a specific resource type in a regatta.
     */
    List<AclEntry> findByRegattaIdAndResourceType(String regattaId, DocumentType resourceType);
}
