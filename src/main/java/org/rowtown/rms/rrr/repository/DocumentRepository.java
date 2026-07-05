package org.rowtown.rms.rrr.repository;

import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for all documents (base type). Type-specific uniqueness lookups live
 * in {@link StartListDocumentRepository} and {@link RaceResultsDocumentRepository}.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    /**
     * Find all documents for a specific regatta (by name).
     */
    List<Document> findByRegattaId(String regattaId);

    /**
     * Search documents by description (case-insensitive substring).
     */
    @Query("SELECT d FROM Document d WHERE LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Document> searchByDescription(@Param("searchTerm") String searchTerm);

    /**
     * Search documents by author.
     */
    List<Document> findByAuthorContainingIgnoreCase(String author);

    /**
     * Ids of documents of the given type whose stored model namespace is not the
     * current one — i.e. candidates for upgrade-and-persist.
     */
    @Query("SELECT d.documentId FROM Document d "
        + "WHERE d.documentType = :type AND d.modelNsUri IS NOT NULL AND d.modelNsUri <> :currentNsUri")
    List<Long> findIdsNeedingUpgrade(@Param("type") DocumentType type,
                                     @Param("currentNsUri") String currentNsUri);
}
