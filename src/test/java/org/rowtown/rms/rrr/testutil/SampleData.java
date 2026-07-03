package org.rowtown.rms.rrr.testutil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Loads sample Timing Data Interchange (TDI) fixtures from the test classpath.
 *
 * <p>These are real, anonymized-enough regatta documents used to exercise the
 * repository with realistic {@code modelData} payloads instead of placeholder
 * bytes.</p>
 */
public final class SampleData {

    /**
     * Real Stotesbury Cup Regatta (2024-05-17) start list — an XMI-serialized
     * {@code tdi:TimingRegatta} model (~150 KB).
     */
    public static final String STOTESBURY_2024_START_LIST =
        "Stotesbury-2024-05-17-race-start-master-updated-startlist.tdi";

    /**
     * Small, realistic Race Results fixtures for race {@code 1a}, forming a
     * natural version progression: {@code preliminary} (crews entered, race
     * scheduled) &rarr; {@code final} (crews finished, race official) &rarr;
     * {@code corrected} (one crew disqualified with a penalty). Comparing any two
     * exercises EMF Compare with meaningful, matchable differences.
     */
    public static final String RACE_RESULTS_1A_PRELIMINARY = "race-results-1a-preliminary.tdi";
    public static final String RACE_RESULTS_1A_FINAL = "race-results-1a-final.tdi";
    public static final String RACE_RESULTS_1A_CORRECTED = "race-results-1a-corrected.tdi";

    private SampleData() {
    }

    /**
     * Reads a classpath resource as raw bytes.
     *
     * @param resourceName the resource name relative to the classpath root
     * @return the resource contents
     * @throws IllegalStateException if the resource cannot be found
     */
    public static byte[] bytes(String resourceName) {
        try (InputStream in = SampleData.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Sample resource not found on classpath: " + resourceName);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read sample resource: " + resourceName, e);
        }
    }

    /**
     * @return the bytes of the Stotesbury 2024 start list TDI model
     */
    public static byte[] stotesburyStartList() {
        return bytes(STOTESBURY_2024_START_LIST);
    }

    /**
     * @return preliminary Race Results for race 1a (crews entered, not yet raced)
     */
    public static byte[] raceResultsPreliminary() {
        return bytes(RACE_RESULTS_1A_PRELIMINARY);
    }

    /**
     * @return final Race Results for race 1a (crews finished, race official)
     */
    public static byte[] raceResultsFinal() {
        return bytes(RACE_RESULTS_1A_FINAL);
    }

    /**
     * @return corrected Race Results for race 1a (one crew disqualified w/ penalty)
     */
    public static byte[] raceResultsCorrected() {
        return bytes(RACE_RESULTS_1A_CORRECTED);
    }

    /**
     * Builds a minimal XMI Race Results model that carries the given race id, so
     * the server can derive {@code raceId} from it (see TdiModelInspector).
     *
     * @param raceId the race identifier to embed
     * @return XMI model bytes
     */
    public static byte[] raceResultsModel(String raceId) {
        String xmi = "<?xml version=\"1.0\" encoding=\"ASCII\"?>\n"
            + "<tdi:TimingRegatta xmlns:tdi=\"http://www.rowtown.org/TDI/1.0.0\">\n"
            + "  <timingRace raceId=\"" + raceId + "\"/>\n"
            + "</tdi:TimingRegatta>\n";
        return xmi.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

}
