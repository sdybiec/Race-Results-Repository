package org.rowtown.rms.rrr.service;

import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.Test;
import org.rowtown.rms.rrr.config.TdiModelConfig;
import org.rowtown.rms.rrr.domain.SerializationFormat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TdiValidationService} against the generated TDI model.
 *
 * <p>The generated {@link EPackage} is obtained through {@link TdiModelConfig}
 * so this test does not reference generated classes directly.</p>
 */
class TdiValidationServiceTest {

    /** A minimal TDI model that loads cleanly into the generated classes. */
    private static final byte[] LOADABLE_MODEL = (
        "<?xml version=\"1.0\" encoding=\"ASCII\"?>\n"
        + "<tdi:TimingRegatta xmlns:tdi=\"" + TdiModelConfig.TDI_NS_URI + "\" regattaId=\"Test\">\n"
        + "  <timingRace raceId=\"1a\"/>\n"
        + "</tdi:TimingRegatta>\n").getBytes(StandardCharsets.UTF_8);

    private TdiValidationService validationService(boolean enabled, boolean strict) {
        EPackage tdiPackage = new TdiModelConfig().tdiPackage();
        ModelSerializationService serialization = new ModelSerializationService(List.of(tdiPackage));
        return new TdiValidationService(serialization, enabled, strict);
    }

    @Test
    void validate_LoadableModel_Accepts() {
        TdiValidationService validation = validationService(true, false);

        assertDoesNotThrow(() -> validation.validate(LOADABLE_MODEL, SerializationFormat.XMI));
    }

    @Test
    void validate_MalformedData_Rejected() {
        TdiValidationService validation = validationService(true, false);
        byte[] garbage = "this is not an XMI document".getBytes();

        // Fail-fast: anything the generated classes cannot load is rejected.
        assertThrows(IllegalArgumentException.class,
            () -> validation.validate(garbage, SerializationFormat.XMI));
    }

    @Test
    void validate_ForeignNamespace_Rejected() {
        TdiValidationService validation = validationService(true, false);
        byte[] foreign = (
            "<?xml version=\"1.0\" encoding=\"ASCII\"?>\n"
            + "<other xmlns=\"http://example.org/other\"/>\n").getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
            () -> validation.validate(foreign, SerializationFormat.XMI));
    }

    @Test
    void validate_EmptyData_Rejected() {
        TdiValidationService validation = validationService(true, false);

        assertThrows(IllegalArgumentException.class,
            () -> validation.validate(new byte[0], SerializationFormat.XMI));
    }

    @Test
    void validate_Disabled_NoOp() {
        TdiValidationService validation = validationService(false, false);

        // Even garbage passes when validation is disabled.
        assertDoesNotThrow(() -> validation.validate("garbage".getBytes(), SerializationFormat.XMI));
    }
}
