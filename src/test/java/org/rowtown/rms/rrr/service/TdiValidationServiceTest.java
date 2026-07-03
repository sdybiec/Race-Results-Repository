package org.rowtown.rms.rrr.service;

import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.Test;
import org.rowtown.rms.rrr.config.TdiModelConfig;
import org.rowtown.rms.rrr.domain.SerializationFormat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TdiValidationService} against the generated TDI model.
 *
 * <p>The generated {@link EPackage} is obtained through {@link TdiModelConfig}
 * so this test does not reference generated classes directly.</p>
 */
class TdiValidationServiceTest {

    private static final Path SAMPLE_START_LIST = Path.of(
        "src/test/resources/Stotesbury-2024-05-17-race-start-master-updated-startlist.tdi");

    private TdiValidationService validationService(boolean enabled, boolean strict) {
        EPackage tdiPackage = new TdiModelConfig().tdiPackage();
        ModelSerializationService serialization = new ModelSerializationService(List.of(tdiPackage));
        return new TdiValidationService(serialization, new TdiModelInspector(), enabled, strict);
    }

    @Test
    void validate_RealStartList_LenientMode_Accepts() throws Exception {
        TdiValidationService validation = validationService(true, false);
        byte[] data = Files.readAllBytes(SAMPLE_START_LIST);

        // A real start list may omit result-only fields, and the generated model's
        // datatype converters may reject otherwise-valid values (version skew).
        // Lenient mode must still accept a well-formed TDI document.
        assertDoesNotThrow(() -> validation.validate(data, SerializationFormat.XMI));
    }

    @Test
    void validate_MalformedData_Rejected() {
        TdiValidationService validation = validationService(true, false);
        byte[] garbage = "this is not an XMI document".getBytes();

        assertThrows(IllegalArgumentException.class,
            () -> validation.validate(garbage, SerializationFormat.XMI));
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
