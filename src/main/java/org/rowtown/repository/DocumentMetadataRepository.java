package org.rowtown.repository;

import org.rowtown.domain.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DocumentMetadata entity operations.
 */
@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {

    /**
     * Find all metadata for a document.
     */
    List<DocumentMetadata> findByDocument_DocumentId(Long documentId);

    /**
     * Find metadata by key for a document.
     */
    Optional<DocumentMetadata> findByDocument_DocumentIdAndKey(Long documentId, String key);

    /**
     * Search documents by metadata key-value pair.
     */
    List<DocumentMetadata> findByKeyAndValueContainingIgnoreCase(String key, String value);

    /**
     * Delete all metadata for a document.
     */
    void deleteByDocument_DocumentId(Long documentId);
}
