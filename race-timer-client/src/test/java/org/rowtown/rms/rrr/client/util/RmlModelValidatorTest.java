package org.rowtown.rms.rrr.client.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RmlModelValidator} (negative paths).
 *
 * <p>The positive round-trip (a real RML/1.4.0 document loading into the
 * generated classes) is covered by the server integration test with the FSRA
 * fixture.</p>
 */
class RmlModelValidatorTest {

    @Test
    void validateLoadable_Malformed_Throws() {
        assertThrows(IllegalArgumentException.class,
            () -> RmlModelValidator.validateLoadable("not an RML model".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validateLoadable_Empty_Throws() {
        assertThrows(IllegalArgumentException.class,
            () -> RmlModelValidator.validateLoadable(new byte[0]));
    }

    @Test
    void validateLoadable_ForeignNamespace_Throws() {
        // A TDI document is not RML; only RML/ReportDesign packages are registered.
        byte[] tdi = ("<?xml version=\"1.0\" encoding=\"ASCII\"?>\n"
            + "<tdi:TimingRegatta xmlns:tdi=\"http://www.rowtown.org/TDI/1.0.0\"/>\n")
            .getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
            () -> RmlModelValidator.validateLoadable(tdi));
    }
}
