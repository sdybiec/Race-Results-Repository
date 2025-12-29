package org.rowtown.repository;

import org.rowtown.domain.entity.DocumentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for DocumentTag entity operations.
 */
@Repository
public interface DocumentTagRepository extends JpaRepository<DocumentTag, Long> {

    /**
     * Find all tags for a document.
     */
    List<DocumentTag> findByDocument_DocumentId(Long documentId);

    /**
     * Find documents with a specific tag.
     */
    @Query("SELECT DISTINCT dt.document.documentId FROM DocumentTag dt WHERE dt.tagName = :tagName")
    List<Long> findDocumentIdsByTagName(@Param("tagName") String tagName);

    /**
     * Check if a document has a specific tag.
     */
    boolean existsByDocument_DocumentIdAndTagName(Long documentId, String tagName);

    /**
     * Delete all tags for a document.
     */
    void deleteByDocument_DocumentId(Long documentId);
}
