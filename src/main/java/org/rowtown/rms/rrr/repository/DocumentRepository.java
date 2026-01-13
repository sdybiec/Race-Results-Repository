package org.rowtown.rms.rrr.repository;

import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Document entity operations.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    /**
     * Find all documents for a specific regatta.
     */
    List<Document> findByRegattaId(String regattaId);

    /**
     * Find all documents of a specific type for a regatta.
     */
    List<Document> findByRegattaIdAndDocumentType(String regattaId, DocumentType documentType);

    /**
     * Find documents by timer ID.
     */
    List<Document> findByTimerId(String timerId);

    /**
     * Find a specific Race Results document by regatta, timer, and milestone.
     */
    Optional<Document> findByRegattaIdAndTimerIdAndMilestoneId(
        String regattaId, String timerId, String milestoneId);

    /**
     * Find Start List for a regatta.
     */
    Optional<Document> findByRegattaIdAndDocumentType(String regattaId, DocumentType documentType);

    /**
     * Search documents by description (full-text search).
     */
    @Query("SELECT d FROM Document d WHERE LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Document> searchByDescription(@Param("searchTerm") String searchTerm);

    /**
     * Search documents by author.
     */
    List<Document> findByAuthorContainingIgnoreCase(String author);
}
