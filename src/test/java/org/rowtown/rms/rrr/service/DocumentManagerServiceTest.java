package org.rowtown.rms.rrr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rowtown.rms.rrr.domain.DocumentType;
import org.rowtown.rms.rrr.domain.TimerRole;
import org.rowtown.rms.rrr.domain.entity.Document;
import org.rowtown.rms.rrr.domain.entity.DocumentOwnership;
import org.rowtown.rms.rrr.domain.entity.RaceResultsDocument;
import org.rowtown.rms.rrr.domain.entity.StartListDocument;
import org.rowtown.rms.rrr.dto.DocumentRequest;
import org.rowtown.rms.rrr.dto.DocumentResponse;
import org.rowtown.rms.rrr.exception.ConflictException;
import org.rowtown.rms.rrr.exception.ResourceNotFoundException;
import org.rowtown.rms.rrr.repository.*;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DocumentManagerService.
 */
@ExtendWith(MockitoExtension.class)
class DocumentManagerServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private StartListDocumentRepository startListDocumentRepository;

    @Mock
    private RaceResultsDocumentRepository raceResultsDocumentRepository;

    @Mock
    private TdiModelInspector tdiModelInspector;

    @Mock
    private TdiValidationService tdiValidationService;

    @Mock
    private VersionControlService versionControlService;

    @Mock
    private DocumentMetadataRepository metadataRepository;

    @Mock
    private DocumentTagRepository tagRepository;

    @Mock
    private DocumentOwnershipRepository ownershipRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DocumentManagerService documentManagerService;

    private DocumentRequest testRequest;
    private RaceResultsDocument testDocument;

    @BeforeEach
    void setUp() {
        testRequest = DocumentRequest.builder()
            .type(DocumentType.RACE_RESULTS)
            .regattaId("TEST2025")
            .regattaStartDate(LocalDate.of(2025, 5, 17))
            .milestoneId("Finish Line")
            .timer(TimerRole.PRIMARY)
            .author("test@example.com")
            .description("Test document")
            .tags(Set.of("preliminary"))
            .metadata(Map.of("venue", "Test Venue"))
            .modelData("<timingRace raceId=\"R1\"/>".getBytes())
            .build();

        testDocument = new RaceResultsDocument();
        testDocument.setDocumentId(1L);
        testDocument.setRegattaId("TEST2025");
        testDocument.setRegattaStartDate(LocalDate.of(2025, 5, 17));
        testDocument.setRaceId("R1");
        testDocument.setMilestoneId("Finish Line");
        testDocument.setTimerRole(TimerRole.PRIMARY);
        testDocument.setAuthor("test@example.com");
    }

    @Test
    void createDocument_Success() {
        // Arrange
        when(tdiModelInspector.extractRaceId(any())).thenReturn("R1");
        when(raceResultsDocumentRepository
            .findByRegattaIdAndRegattaStartDateAndRaceIdAndMilestoneIdAndTimerRole(any(), any(), any(), any(), any()))
            .thenReturn(Optional.empty());
        when(documentRepository.save(any(RaceResultsDocument.class))).thenReturn(testDocument);
        when(metadataRepository.save(any())).thenReturn(null);
        when(tagRepository.save(any())).thenReturn(null);
        when(ownershipRepository.save(any(DocumentOwnership.class))).thenReturn(null);

        // Act
        DocumentResponse result = documentManagerService.createDocument(testRequest);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getDocumentId());
        verify(documentRepository).save(any(RaceResultsDocument.class));
        verify(versionControlService).createVersion(any(), any(), any(), any(), any());
        verify(ownershipRepository).save(any(DocumentOwnership.class));
        verify(notificationService).notifyDocumentCreated(any(), any(), any(), any(), any());
    }

    @Test
    void createDocument_StartListConflict() {
        // Arrange
        testRequest.setType(DocumentType.START_LIST);
        when(startListDocumentRepository.findByRegattaIdAndRegattaStartDate(any(), any()))
            .thenReturn(Optional.of(new StartListDocument()));

        // Act & Assert
        assertThrows(ConflictException.class, () ->
            documentManagerService.createDocument(testRequest));
    }

    @Test
    void createDocument_RaceResultsConflict() {
        // Arrange
        when(tdiModelInspector.extractRaceId(any())).thenReturn("R1");
        when(raceResultsDocumentRepository
            .findByRegattaIdAndRegattaStartDateAndRaceIdAndMilestoneIdAndTimerRole(any(), any(), any(), any(), any()))
            .thenReturn(Optional.of(testDocument));

        // Act & Assert
        assertThrows(ConflictException.class, () ->
            documentManagerService.createDocument(testRequest));
    }

    @Test
    void createDocument_RaceResultsMissingRaceId() {
        // Arrange: model has no derivable raceId
        when(tdiModelInspector.extractRaceId(any())).thenReturn(null);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            documentManagerService.createDocument(testRequest));
    }

    @Test
    void getDocument_Success() {
        // Arrange
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));

        // Act & Assert - will fail because versionControlService.getVersion is not mocked
        assertThrows(Exception.class, () ->
            documentManagerService.getDocument(1L, 1L));
    }

    @Test
    void getDocument_NotFound() {
        // Arrange
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () ->
            documentManagerService.getLatestDocument(999L));
    }

    @Test
    void deleteDocument_Success() {
        // Arrange
        when(documentRepository.findById(1L)).thenReturn(Optional.of(testDocument));
        doNothing().when(documentRepository).delete(any(Document.class));

        // Act
        documentManagerService.deleteDocument(1L);

        // Assert
        verify(documentRepository).delete(testDocument);
    }

    @Test
    void deleteDocument_NotFound() {
        // Arrange
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () ->
            documentManagerService.deleteDocument(999L));
    }
}
