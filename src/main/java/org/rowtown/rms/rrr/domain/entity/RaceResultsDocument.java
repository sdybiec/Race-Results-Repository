package org.rowtown.rms.rrr.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.rowtown.rms.rrr.domain.TimerRole;

/**
 * Race Results for a single (race, milestone, timer) within a regatta edition.
 *
 * <p>A regatta has many Race Results documents. The key is
 * (regatta name + start date, raceId, milestoneId, timerRole). {@code raceId} is
 * derived from the submitted model rather than supplied by the client.</p>
 */
@Entity
@DiscriminatorValue("RACE_RESULTS")
@Getter
@Setter
@NoArgsConstructor
public class RaceResultsDocument extends Document {

    /** Race identifier, derived from the model (e.g. the tdi:timingRace raceId). */
    @Column(name = "race_id")
    private String raceId;

    /** Timing milestone (e.g. "Start Line", "500m", "Finish Line"). */
    @Column(name = "milestoneId")
    private String milestoneId;

    /** Which redundant timer produced these results. */
    @Enumerated(EnumType.STRING)
    @Column(name = "timer_role")
    private TimerRole timerRole;
}
