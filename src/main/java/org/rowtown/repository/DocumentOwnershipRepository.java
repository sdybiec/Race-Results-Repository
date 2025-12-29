package org.rowtown.repository;

import org.rowtown.domain.entity.DocumentOwnership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DocumentOwnership entity operations.
 */
@Repository
public interface DocumentOwnershipRepository extends JpaRepository<DocumentOwnership, Long> {

    /**
     * Find ownership record for a document.
     */
    Optional<DocumentOwnership> findByDocument_DocumentId(Long documentId);

    /**
     * Find all documents owned by a user.
     */
    List<DocumentOwnership> findByOwnerUserId(String ownerUserId);

    /**
     * Check if a user owns a document.
     */
    boolean existsByDocument_DocumentIdAndOwnerUserId(Long documentId, String ownerUserId);
}
