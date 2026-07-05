package org.rowtown.rms.rrr.service;

import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.Test;
import org.rowtown.rms.rrr.config.TdiModelConfig;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.SerializationFormat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ModelValidationService}, focusing on the per-type namespace
 * enforcement. Uses a TDI model (obtained via {@link TdiModelConfig}) so the
 * test does not reference generated classes directly.
 */
class ModelValidationServiceTest {

    private static final byte[] TDI_MODEL = (
        "<?xml version=\"1.0\" encoding=\"ASCII\"?>\n"
        + "<tdi:TimingRegatta xmlns:tdi=\"" + TdiModelConfig.TDI_NS_URI + "\" regattaId=\"Test\">\n"
        + "  <timingRace raceId=\"1a\"/>\n"
        + "</tdi:TimingRegatta>\n").getBytes(StandardCharsets.UTF_8);

    private ModelValidationService validationService(boolean enabled, boolean strict) {
        EPackage tdiPackage = new TdiModelConfig().tdiPackage();
        ModelSerializationService serialization = new ModelSerializationService(
            new DefaultModelResourceSetFactory(List.of(tdiPackage)));
        return new ModelValidationService(serialization, enabled, strict);
    }

    @Test
    void validate_TdiModel_AsRaceResults_Accepts() {
        ModelValidationService validation = validationService(true, false);
        assertDoesNotThrow(() ->
            validation.validate(TDI_MODEL, SerializationFormat.XMI, DocumentType.RACE_RESULTS));
    }

    @Test
    void validate_TdiModel_AsStartList_Accepts() {
        ModelValidationService validation = validationService(true, false);
        assertDoesNotThrow(() ->
            validation.validate(TDI_MODEL, SerializationFormat.XMI, DocumentType.START_LIST));
    }

    @Test
    void validate_TdiModel_DeclaredAsRml_Rejected() {
        // A TDI model submitted as an RML document must be rejected on namespace.
        ModelValidationService validation = validationService(true, false);
        assertThrows(IllegalArgumentException.class,
            () -> validation.validate(TDI_MODEL, SerializationFormat.XMI, DocumentType.RML));
    }

    @Test
    void validate_MalformedData_Rejected() {
        ModelValidationService validation = validationService(true, false);
        assertThrows(IllegalArgumentException.class, () ->
            validation.validate("not xml".getBytes(), SerializationFormat.XMI, DocumentType.RACE_RESULTS));
    }

    @Test
    void validate_EmptyData_Rejected() {
        ModelValidationService validation = validationService(true, false);
        assertThrows(IllegalArgumentException.class, () ->
            validation.validate(new byte[0], SerializationFormat.XMI, DocumentType.START_LIST));
    }

    @Test
    void validate_Disabled_NoOp() {
        ModelValidationService validation = validationService(false, false);
        assertDoesNotThrow(() ->
            validation.validate("garbage".getBytes(), SerializationFormat.XMI, DocumentType.RML));
    }
}
