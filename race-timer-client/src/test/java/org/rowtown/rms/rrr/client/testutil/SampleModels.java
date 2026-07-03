package org.rowtown.rms.rrr.client.testutil;

import java.nio.charset.StandardCharsets;

/**
 * Builds small, loadable TDI models for client tests.
 *
 * <p>The client validates that captured models load into the generated TDI
 * classes before saving them, so tests must use real TDI models rather than
 * placeholder bytes.</p>
 */
public final class SampleModels {

    private SampleModels() {
    }

    /**
     * @param raceId the race identifier to embed
     * @return a minimal loadable XMI TDI model carrying the given race id
     */
    public static byte[] raceResults(String raceId) {
        String xmi = "<?xml version=\"1.0\" encoding=\"ASCII\"?>\n"
            + "<tdi:TimingRegatta xmlns:tdi=\"http://www.rowtown.org/TDI/1.0.0\">\n"
            + "  <timingRace raceId=\"" + raceId + "\"/>\n"
            + "</tdi:TimingRegatta>\n";
        return xmi.getBytes(StandardCharsets.UTF_8);
    }
}
