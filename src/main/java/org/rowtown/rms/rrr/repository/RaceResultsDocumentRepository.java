package org.rowtown.rms.rrr.repository;

import org.rowtown.rms.rrr.domain.TimerRole;
import org.rowtown.rms.rrr.domain.entity.RaceResultsDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository for Race Results documents.
 */
@Repository
public interface RaceResultsDocumentRepository extends JpaRepository<RaceResultsDocument, Long> {

    /**
     * Find the Race Results document for a specific (regatta edition, race,
     * milestone, timer) — the full Race Results key.
     */
    Optional<RaceResultsDocument> findByRegattaIdAndRegattaStartDateAndRaceIdAndMilestoneIdAndTimerRole(
        String regattaId, LocalDate regattaStartDate, String raceId, String milestoneId, TimerRole timerRole);
}
