package org.rowtown.rms.rrr.domain;

/**
 * Enumeration of document types supported by the repository.
 */
public enum DocumentType {
    /**
     * Start List - chronologically ordered schedule of races and crew assignments.
     * Read-only for timers, modifiable by regatta admin.
     */
    START_LIST,

    /**
     * Race Results - timing data captured by timers including crossing times
     * and crew identifications.
     */
    RACE_RESULTS
}
