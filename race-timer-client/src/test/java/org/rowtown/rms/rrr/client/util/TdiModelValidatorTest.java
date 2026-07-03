package org.rowtown.rms.rrr.client.util;

import org.junit.jupiter.api.Test;
import org.rowtown.rms.rrr.client.testutil.SampleModels;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TdiModelValidator}.
 */
class TdiModelValidatorTest {

    @Test
    void validateLoadable_LoadableModel_Passes() {
        assertDoesNotThrow(() -> TdiModelValidator.validateLoadable(SampleModels.raceResults("1a")));
    }

    @Test
    void validateLoadable_Malformed_Throws() {
        assertThrows(IllegalArgumentException.class,
            () -> TdiModelValidator.validateLoadable("not a TDI model".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validateLoadable_ForeignNamespace_Throws() {
        byte[] foreign = ("<?xml version=\"1.0\" encoding=\"ASCII\"?>\n"
            + "<other xmlns=\"http://example.org/other\"/>\n").getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
            () -> TdiModelValidator.validateLoadable(foreign));
    }

    @Test
    void validateLoadable_Empty_Throws() {
        assertThrows(IllegalArgumentException.class,
            () -> TdiModelValidator.validateLoadable(new byte[0]));
    }
}
