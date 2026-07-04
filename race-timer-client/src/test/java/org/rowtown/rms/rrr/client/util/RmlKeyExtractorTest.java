package org.rowtown.rms.rrr.client.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RmlKeyExtractor}.
 */
class RmlKeyExtractorTest {

    private static final byte[] RML = (
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<rml:Regatta xmlns:rml=\"http://www.rowtown.org/RML/1.4.0\" "
        + "name=\"FSRA Sculling Championships\" startDate=\"2024-04-13\"/>\n")
        .getBytes(StandardCharsets.UTF_8);

    @Test
    void extractsRegattaName() {
        assertEquals("FSRA Sculling Championships", RmlKeyExtractor.extractRegattaName(RML));
    }

    @Test
    void extractsRegattaStartDate() {
        assertEquals("2024-04-13", RmlKeyExtractor.extractRegattaStartDate(RML));
    }

    @Test
    void malformedData_ReturnsNull() {
        assertNull(RmlKeyExtractor.extractRegattaName("not xml".getBytes(StandardCharsets.UTF_8)));
        assertNull(RmlKeyExtractor.extractRegattaStartDate(new byte[0]));
    }
}
