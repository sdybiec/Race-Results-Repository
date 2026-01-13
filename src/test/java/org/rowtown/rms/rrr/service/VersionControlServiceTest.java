package org.rowtown.rms.rrr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rowtown.rms.rrr.domain.SerializationFormat;
import org.rowtown.rms.rrr.domain.entity.Document;
import org.rowtown.rms.rrr.domain.entity.Version;
import org.rowtown.rms.rrr.dto.VersionInfo;
import org.rowtown.rms.rrr.exception.ResourceNotFoundException;
import org.rowtown.rms.rrr.repository.DocumentRepository;
import org.rowtown.rms.rrr.repository.VersionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VersionControlService.
 */
@ExtendWith(MockitoExtension.class)
class VersionControlServiceTest {

    @Mock
    private VersionRepository versionRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ModelSerializationService serializationService;

    @InjectMocks
    private VersionControlService versionControlService;

    private Document testDocument;
    private Version testVersion;
    private byte[] testModelData;

    @BeforeEach
    void setUp() {
        testDocument = Document.builder()
            .documentId(1L)
            .latestVersion(0L)
            .build();

        testModelData = "test model data".getBytes();

        testVersion = Version.builder()
            .versionId(1L)
            .document(testDocument)
            .versionNumber(1L)
            .timestamp(LocalDateTime.now())
            .author("test@example.com")
            .modelSnapshot(testModelData)
            .snapshotFormat(SerializationFormat.XMI)
            .checksum("abc123")
            .build();
    }

    @Test
    void createVersion_Success() {
        // Arrange
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));
        when(versionRepository.findLatestVersionNumber(1L)).thenReturn(Optional.empty());
        when(serializationService.calculateChecksum(any())).thenReturn("checksum123");
        when(versionRepository.save(any(Version.class))).thenReturn(testVersion);
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

        // Act
        Version result = versionControlService.createVersion(
            1L, testModelData, "test@example.com", "Initial version", SerializationFormat.XMI);

        // Assert
        assertNotNull(result);
        verify(versionRepository).save(any(Version.class));
        verify(documentRepository).save(testDocument);
    }

    @Test
    void createVersion_DocumentNotFound() {
        // Arrange
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () ->
            versionControlService.createVersion(
                999L, testModelData, "test@example.com", "Test", SerializationFormat.XMI));
    }

    @Test
    void getVersion_Success() {
        // Arrange
        when(versionRepository.findByDocument_DocumentIdAndVersionNumber(1L, 1L))
            .thenReturn(Optional.of(testVersion));

        // Act
        Version result = versionControlService.getVersion(1L, 1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getVersionNumber());
        verify(versionRepository).findByDocument_DocumentIdAndVersionNumber(1L, 1L);
    }

    @Test
    void getVersion_NotFound() {
        // Arrange
        when(versionRepository.findByDocument_DocumentIdAndVersionNumber(1L, 999L))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () ->
            versionControlService.getVersion(1L, 999L));
    }

    @Test
    void getLatestVersion_Success() {
        // Arrange
        when(versionRepository.findFirstByDocument_DocumentIdOrderByVersionNumberDesc(1L))
            .thenReturn(Optional.of(testVersion));

        // Act
        Version result = versionControlService.getLatestVersion(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getVersionNumber());
    }

    @Test
    void listVersions_WithPagination() {
        // Arrange
        List<Version> versions = Arrays.asList(testVersion);
        Page<Version> page = new PageImpl<>(versions);

        when(versionRepository.findByDocument_DocumentIdOrderByVersionNumberDesc(eq(1L), any(Pageable.class)))
            .thenReturn(page);

        // Act
        Page<VersionInfo> result = versionControlService.listVersions(1L, 0, 10);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getContent().get(0).getVersionNumber());
    }

    @Test
    void rollback_Success() {
        // Arrange
        when(versionRepository.findByDocument_DocumentIdAndVersionNumber(1L, 3L))
            .thenReturn(Optional.of(testVersion));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));
        when(versionRepository.findLatestVersionNumber(1L)).thenReturn(Optional.of(5L));
        when(serializationService.calculateChecksum(any())).thenReturn("checksum123");
        when(versionRepository.save(any(Version.class))).thenReturn(testVersion);
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

        // Act
        Version result = versionControlService.rollback(1L, 3L, "admin@example.com", "Rollback");

        // Assert
        assertNotNull(result);
        verify(versionRepository, times(2)).save(any(Version.class)); // Once for rollback, once in createVersion
    }

    @Test
    void countVersions_ReturnsCorrectCount() {
        // Arrange
        when(versionRepository.countByDocument_DocumentId(1L)).thenReturn(5L);

        // Act
        long count = versionControlService.countVersions(1L);

        // Assert
        assertEquals(5L, count);
        verify(versionRepository).countByDocument_DocumentId(1L);
    }
}
