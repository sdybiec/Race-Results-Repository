package org.rowtown.rms.rrr.domain;

/**
 * Enumeration of operations that can be controlled via ACLs.
 */
public enum Operation {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    ROLLBACK,
    SUBSCRIBE
}
