package org.rowtown.rms.rrr.repository;

import org.rowtown.rms.rrr.domain.entity.Version;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Version entity operations.
 */
@Repository
public interface VersionRepository extends JpaRepository<Version, Long> {

    /**
     * Find a specific version of a document.
     */
    Optional<Version> findByDocument_DocumentIdAndVersionNumber(Long documentId, Long versionNumber);

    /**
     * Find all versions for a document, ordered by version number descending.
     */
    List<Version> findByDocument_DocumentIdOrderByVersionNumberDesc(Long documentId);

    /**
     * Find all versions for a document with pagination.
     */
    Page<Version> findByDocument_DocumentIdOrderByVersionNumberDesc(Long documentId, Pageable pageable);

    /**
     * Get the latest version number for a document.
     */
    @Query("SELECT MAX(v.versionNumber) FROM Version v WHERE v.document.documentId = :documentId")
    Optional<Long> findLatestVersionNumber(@Param("documentId") Long documentId);

    /**
     * Get the latest version for a document.
     */
    Optional<Version> findFirstByDocument_DocumentIdOrderByVersionNumberDesc(Long documentId);

    /**
     * Count versions for a document.
     */
    long countByDocument_DocumentId(Long documentId);
}
