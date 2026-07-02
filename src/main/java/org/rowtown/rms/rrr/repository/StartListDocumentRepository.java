package org.rowtown.rms.rrr.repository;

import org.rowtown.rms.rrr.domain.entity.StartListDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository for Start List documents.
 */
@Repository
public interface StartListDocumentRepository extends JpaRepository<StartListDocument, Long> {

    /**
     * Find the Start List for a regatta edition (name + start date).
     * There is at most one Start List per regatta edition.
     */
    Optional<StartListDocument> findByRegattaIdAndRegattaStartDate(String regattaId, LocalDate regattaStartDate);
}
