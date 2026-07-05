package org.rowtown.rms.rrr.service;

import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.rowtown.rms.rrr.domain.entity.DocumentMetadata;
import org.rowtown.rms.rrr.domain.entity.RegattaDefinitionDocument;
import org.rowtown.rms.rrr.domain.entity.Version;
import org.rowtown.rms.rrr.repository.DocumentMetadataRepository;
import org.rowtown.rms.rrr.repository.DocumentRepository;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ModelUpgradePersistenceService}.
 */
@ExtendWith(MockitoExtension.class)
class ModelUpgradePersistenceServiceTest {

    private static final String OLD_NS = "http://www.rowtown.org/RML/1.2.0";
    private static final String CURRENT_RML_NS = "http://www.rowtown.org/RML/1.4.0";

    @Mock private DocumentRepository documentRepository;
    @Mock private VersionControlService versionControlService;
    @Mock private ModelSerializationService serializationService;
    @Mock private DocumentMetadataRepository metadataRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private ModelUpgradePersistenceService service;

    @BeforeEach
    void setUp() {
        // Real namespaces: current RML is 1.4.0.
        service = new ModelUpgradePersistenceService(
            documentRepository, versionControlService, serializationService,
            metadataRepository, new CurrentModelNamespaces(), transactionManager);
    }

    private RegattaDefinitionDocument rmlDoc(String nsUri) {
        RegattaDefinitionDocument doc = new RegattaDefinitionDocument();
        doc.setDocumentId(1L);
        doc.setDocumentType(DocumentType.RML);
        doc.setModelNsUri(nsUri);
        return doc;
    }

    @Test
    void needsUpgrade_TrueForOlderNamespace() {
        assertTrue(service.needsUpgrade(rmlDoc(OLD_NS)));
    }

    @Test
    void needsUpgrade_FalseForCurrentNamespace() {
        assertFalse(service.needsUpgrade(rmlDoc(CURRENT_RML_NS)));
    }

    @Test
    void upgradeDocument_OlderVersion_WritesUpgradedVersionAndUpdatesNamespace() throws Exception {
        RegattaDefinitionDocument doc = rmlDoc(OLD_NS);
        byte[] oldBytes = "old".getBytes();
        byte[] upgradedBytes = "upgraded".getBytes();

        Version latest = mock(Version.class);
        when(latest.getModelSnapshot()).thenReturn(oldBytes);
        when(latest.getSnapshotFormat()).thenReturn(SerializationFormat.XMI);
        EObject loaded = mock(EObject.class);

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(versionControlService.getLatestVersion(1L)).thenReturn(latest);
        when(serializationService.deserialize(oldBytes, SerializationFormat.XMI)).thenReturn(loaded);
        when(serializationService.serialize(loaded, SerializationFormat.XMI)).thenReturn(upgradedBytes);
        when(metadataRepository.findByDocument_DocumentIdAndKey(eq(1L), any())).thenReturn(Optional.empty());

        boolean result = service.upgradeDocument(1L);

        assertTrue(result);
        // Upgraded bytes appended as a new, system-authored version.
        verify(versionControlService).createVersion(eq(1L), eq(upgradedBytes), eq("system"),
            any(), eq(SerializationFormat.XMI));
        // Namespace advanced to current and persisted.
        assertEquals(CURRENT_RML_NS, doc.getModelNsUri());
        verify(documentRepository).save(doc);
        // Provenance recorded.
        verify(metadataRepository, atLeastOnce()).save(any(DocumentMetadata.class));
    }

    @Test
    void requestUpgradeIfNeeded_CurrentDoc_DoesNothing() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(rmlDoc(CURRENT_RML_NS)));

        assertFalse(service.requestUpgradeIfNeeded(1L));
        verifyNoInteractions(versionControlService);
    }

    @Test
    void upgradeDocument_AlreadyCurrent_NoOp() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(rmlDoc(CURRENT_RML_NS)));

        boolean result = service.upgradeDocument(1L);

        assertFalse(result);
        verify(versionControlService, never()).createVersion(any(), any(), any(), any(), any());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void upgradeDocument_LoadFails_DoesNotPersist() throws Exception {
        RegattaDefinitionDocument doc = rmlDoc(OLD_NS);
        Version latest = mock(Version.class);
        when(latest.getModelSnapshot()).thenReturn("bad".getBytes());
        when(latest.getSnapshotFormat()).thenReturn(SerializationFormat.XMI);

        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(versionControlService.getLatestVersion(1L)).thenReturn(latest);
        when(serializationService.deserialize(any(), any()))
            .thenThrow(new RuntimeException("cannot load older version"));

        boolean result = service.upgradeDocument(1L);

        assertFalse(result);
        verify(versionControlService, never()).createVersion(any(), any(), any(), any(), any());
        verify(documentRepository, never()).save(any());
        assertEquals(OLD_NS, doc.getModelNsUri()); // unchanged
    }
}
