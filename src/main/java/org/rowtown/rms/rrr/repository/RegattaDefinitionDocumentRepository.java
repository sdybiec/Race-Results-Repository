package org.rowtown.rms.rrr.repository;

import org.rowtown.rms.rrr.domain.entity.RegattaDefinitionDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository for RML Regatta Definition documents.
 */
@Repository
public interface RegattaDefinitionDocumentRepository extends JpaRepository<RegattaDefinitionDocument, Long> {

    /**
     * Find the Regatta Definition for a regatta edition (name + start date).
     * There is at most one RML document per regatta edition.
     */
    Optional<RegattaDefinitionDocument> findByRegattaIdAndRegattaStartDate(String regattaId, LocalDate regattaStartDate);
}
