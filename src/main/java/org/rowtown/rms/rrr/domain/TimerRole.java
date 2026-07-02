package org.rowtown.rms.rrr.domain;

/**
 * The timing redundancy role that produced a Race Results document.
 *
 * <p>Each race is timed at each milestone by up to three independent timers so
 * results can be cross-checked. A Race Results document is identified by
 * (regatta, race, milestone, timer), where the timer is one of these roles.</p>
 */
public enum TimerRole {
    /** The primary (authoritative) timer. */
    PRIMARY,

    /** The first backup timer. */
    FIRST_BACKUP,

    /** The second backup timer. */
    SECOND_BACKUP
}
