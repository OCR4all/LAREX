package de.uniwue.zpd.dachs.larex.backend.controller.annotation;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.exception.AnnotationAlreadyExistsException;
import de.uniwue.zpd.dachs.larex.backend.exception.AnnotationLeaseLockedException;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnotationEditorControllerTest {

    @Mock
    private AnnotationProcessingService annotationProcessingService;

    @Mock
    private AnnotationLeaseService annotationLeaseService;

    private AnnotationEditorController controller;

    @BeforeEach
    void setUp() {
        controller = new AnnotationEditorController(annotationProcessingService, annotationLeaseService);
    }

    @Test
    void loadAnnotation_validatesRouteAccessBeforeReadingXml() throws Exception {
        PageDto pageDto = org.mockito.Mockito.mock(PageDto.class);
        when(annotationProcessingService.parseXmlToAnnotation("xml-1")).thenReturn(pageDto);

        ResponseEntity<PageDto> response = controller.loadAnnotation(
                "project-1", "page-1", "xml-1", "user-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        InOrder calls = inOrder(annotationLeaseService, annotationProcessingService);
        calls.verify(annotationLeaseService).resolveRoomAccess("project-1", "page-1", "xml-1", "user-1");
        calls.verify(annotationProcessingService).parseXmlToAnnotation("xml-1");
    }

    @Test
    void loadAnnotation_doesNotReadXmlWhenRouteAccessIsRejected() throws Exception {
        when(annotationLeaseService.resolveRoomAccess("project-1", "page-1", "xml-other", "user-1"))
                .thenThrow(new IllegalArgumentException("Annotation XML mismatch"));

        ResponseEntity<PageDto> response = controller.loadAnnotation(
                "project-1", "page-1", "xml-other", "user-1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(annotationProcessingService, never()).parseXmlToAnnotation("xml-other");
    }

    @Test
    void createAnnotation_doesNotCreateWhenPageWriteAccessIsRejected() throws Exception {
        doThrow(new IllegalArgumentException("Annotation project mismatch"))
                .when(annotationLeaseService)
                .assertPageWriteAccess("project-1", "page-other", "user-1");

        ResponseEntity<?> response = controller.createAnnotation(
                "project-1", "page-other", org.mockito.Mockito.mock(PageDto.class), "user-1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(annotationProcessingService, never())
                .createInitialAnnotationXml(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createAnnotation_propagatesReadOnlyPageError() {
        doThrow(new AnnotationLeaseLockedException("This page is currently read-only.", null, "editing-disabled"))
                .when(annotationLeaseService)
                .assertPageWriteAccess("project-1", "page-1", "user-1");

        assertThrows(AnnotationLeaseLockedException.class, () -> controller.createAnnotation(
                "project-1", "page-1", org.mockito.Mockito.mock(PageDto.class), "user-1"));
    }

    @Test
    void createAnnotation_returnsConflictInsteadOfOverwritingExistingXml() throws Exception {
        PageDto pageDto = org.mockito.Mockito.mock(PageDto.class);
        when(annotationProcessingService.createInitialAnnotationXml(
                "project-1", "page-1", pageDto, "user-1"))
                .thenThrow(new AnnotationAlreadyExistsException("xml-existing"));

        ResponseEntity<?> response = controller.createAnnotation(
                "project-1", "page-1", pageDto, "user-1");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(annotationProcessingService).createInitialAnnotationXml(
                "project-1", "page-1", pageDto, "user-1");
    }

    @Test
    void saveAnnotation_propagatesLeaseConflictWithoutWriting() throws Exception {
        PageDto pageDto = org.mockito.Mockito.mock(PageDto.class);
        doThrow(new AnnotationLeaseLockedException("Another editor holds the lease.", null,
                "lease-held-by-other-user"))
                .when(annotationLeaseService)
                .assertWriteAccess("project-1", "page-1", "xml-1", "user-1");

        assertThrows(AnnotationLeaseLockedException.class, () -> controller.saveAnnotation(
                "project-1", "page-1", "xml-1", pageDto, "user-1"));

        verify(annotationProcessingService, never()).saveAnnotationToXml("xml-1", pageDto, "user-1");
    }

    @Test
    void exportAnnotation_doesNotExportWhenXmlRouteAccessIsRejected() throws Exception {
        when(annotationLeaseService.resolveRoomAccess("project-1", "page-1", "xml-other", "user-1"))
                .thenThrow(new IllegalArgumentException("Annotation XML mismatch"));

        ResponseEntity<String> response = controller.exportAnnotation(
                "project-1", "page-1", "xml-other", XmlSchema.PAGE_XML,
                org.mockito.Mockito.mock(PageDto.class), "user-1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(annotationProcessingService, never())
                .exportAnnotationToXml(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }
}
