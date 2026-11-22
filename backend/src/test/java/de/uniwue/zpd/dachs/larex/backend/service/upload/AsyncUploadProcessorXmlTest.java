package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.repository.page.*;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.*;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.LinkedDatasetItemSync;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.OcrFormatToPageXml;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlValidationService;
import de.uniwue.zpd.dachs.larex.backend.exception.InvalidPageXmlException;
import de.uniwue.zpd.dachs.larex.backend.dto.UploadConflictDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncUploadProcessorXmlTest {
    @TempDir Path tempDir;
    @Mock UploadSessionRepository sessions;
    @Mock UploadSessionFileRepository files;
    @Mock ProjectRepository projects;
    @Mock PageRepository pages;
    @Mock PageImageRepository images;
    @Mock PageXmlRepository xmls;
    @Mock HierarchicalFileStorageService storage;
    @Mock UploadRealtimePublisher realtime;
    @Mock PageXmlCanonicalizationService canonicalization;
    @Mock PageFilterIndexService filterIndex;
    @Mock LinkedDatasetItemSync linkedDatasetItemSync;
    @Spy PageXmlValidationService validation = new PageXmlValidationService();
    @InjectMocks AsyncUploadProcessor processor;

    @ParameterizedTest
    @ValueSource(strings = {
            "<PcGts xmlns=\"http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15\"><Page/></PcGts>",
            "<PcGts><Page></PcGts>",
            "<PcGts xmlns=\"http://schema.primaresearch.org/PAGE/gts/pagecontent/2099-01-01\"/>"
    })
    void invalidReplacementFailsBeforeExistingXmlOrStorageIsTouched(String xml) throws Exception {
        Project project = new Project();
        project.setId("project");
        Page page = new Page();
        page.setId("page");
        page.setName("page");
        page.setProject(project);
        UploadSession session = new UploadSession("project", "workspace", "user", 1, 1);
        session.setStatus(UploadSession.UploadSessionStatus.UPLOADING);
        Path path = tempDir.resolve("page.xml");
        Files.writeString(path, xml);
        UploadSessionFile file = new UploadSessionFile("page.xml", Files.size(path), "application/xml", "page", "xml", 1);
        file.setTempFilePath(path.toString());
        file.setConflictResolution("replace");
        when(sessions.findById("session")).thenReturn(Optional.of(session));
        when(projects.findById("project")).thenReturn(Optional.of(project));
        when(files.findBySessionIdAndStatus("session", UploadSessionFile.UploadFileStatus.UPLOADED)).thenReturn(List.of(file));
        when(pages.findByProjectIdAndNameIn(eq("project"), any())).thenReturn(List.of(page));

        processor.doProcessUploadSession("session");

        assertThat(file.getStatus()).isEqualTo(UploadSessionFile.UploadFileStatus.FAILED);
        verifyNoInteractions(storage, xmls);
        assertThat(file.getErrorMessage()).contains("Invalid PAGE XML", "line", "column");
    }

    @Test
    void invalidXmlDoesNotCreateAnEmptyPage() throws Exception {
        Project project = new Project();
        project.setId("project");
        UploadSession session = new UploadSession("project", "workspace", "user", 1, 1);
        session.setStatus(UploadSession.UploadSessionStatus.UPLOADING);
        Path path = tempDir.resolve("page.xml");
        Files.writeString(path, "<invalid/>");
        UploadSessionFile file = new UploadSessionFile("page.xml", Files.size(path), "application/xml", "page", "xml", 1);
        file.setTempFilePath(path.toString());
        when(sessions.findById("session")).thenReturn(Optional.of(session));
        when(projects.findById("project")).thenReturn(Optional.of(project));
        when(files.findBySessionIdAndStatus("session", UploadSessionFile.UploadFileStatus.UPLOADED)).thenReturn(List.of(file));

        processor.doProcessUploadSession("session");

        assertThat(file.getStatus()).isEqualTo(UploadSessionFile.UploadFileStatus.FAILED);
        verify(pages, never()).save(any());
        verifyNoInteractions(storage, xmls);
    }

    @Test
    void altoInPhysicalUnitsWithoutAnImageFailsWithTheReason() throws Exception {
        Project project = new Project();
        project.setId("project");
        UploadSession session = new UploadSession("project", "workspace", "user", 1, 1);
        session.setStatus(UploadSession.UploadSessionStatus.UPLOADING);
        Path path = tempDir.resolve("page.xml");
        Files.write(path, new org.springframework.core.io.ClassPathResource("alto/mm10.xml").getContentAsByteArray());
        UploadSessionFile file = new UploadSessionFile("page.xml", Files.size(path), "application/xml", "page", "xml", 1);
        file.setTempFilePath(path.toString());
        when(sessions.findById("session")).thenReturn(Optional.of(session));
        when(projects.findById("project")).thenReturn(Optional.of(project));
        when(files.findBySessionIdAndStatus("session", UploadSessionFile.UploadFileStatus.UPLOADED)).thenReturn(List.of(file));

        processor.doProcessUploadSession("session");

        assertThat(file.getStatus()).isEqualTo(UploadSessionFile.UploadFileStatus.FAILED);
        assertThat(file.getErrorMessage())
                .isEqualTo("Invalid ALTO XML: Coordinates in mm10 can only be converted to pixels with the page image. "
                        + "Upload the image together with the ALTO file.");
        verify(pages, never()).save(any());
        verifyNoInteractions(storage, xmls);
    }

    @Test
    void hocrIsStoredAsConvertedPageXml() throws Exception {
        Project project = new Project();
        project.setId("project");
        Page page = new Page();
        page.setId("page");
        page.setName("page");
        UploadSession session = new UploadSession("project", "workspace", "user", 1, 1);
        session.setStatus(UploadSession.UploadSessionStatus.UPLOADING);
        Path path = tempDir.resolve("page.hocr");
        Files.write(path, new org.springframework.core.io.ClassPathResource("hocr/0001.hocr").getContentAsByteArray());
        UploadSessionFile file = new UploadSessionFile("page.hocr", Files.size(path), "text/html", "page", "hocr", 1);
        file.setTempFilePath(path.toString());
        when(sessions.findById("session")).thenReturn(Optional.of(session));
        when(projects.findById("project")).thenReturn(Optional.of(project));
        when(files.findBySessionIdAndStatus("session", UploadSessionFile.UploadFileStatus.UPLOADED)).thenReturn(List.of(file));
        when(pages.findByProjectIdAndNameIn(eq("project"), any())).thenReturn(List.of(page));
        byte[][] stored = new byte[1][];
        when(storage.storeFromPath(eq(path), eq("page.xml"), eq("text/html"), eq("workspace"), eq("project"),
                eq(StoredFile.StoredFileType.XML), eq("user"), eq(true)))
                .thenAnswer(invocation -> {
                    stored[0] = Files.readAllBytes(path);
                    return new HierarchicalFileStorageService.StoredFileDescriptor("xml", "page.xml", "page.xml",
                            "application/xml", "xml", stored[0].length, "checksum", StoredFile.StoredFileType.XML);
                });
        when(xmls.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        processor.doProcessUploadSession("session");

        assertThat(file.getStatus()).isEqualTo(UploadSessionFile.UploadFileStatus.COMPLETED);
        assertThat(OcrFormatToPageXml.isConvertible(stored[0])).isFalse();
        assertThat(validation.validatePageXml(new org.springframework.core.io.ByteArrayResource(stored[0])).valid()).isTrue();
        verify(xmls).save(argThat(xml -> "page.xml".equals(xml.getFileName()) && "page".equals(xml.getBaseName())));
    }

    @Test
    void invalidXmlDoesNotPreventItsPairedImageFromBeingUploaded() throws Exception {
        Project project = new Project();
        project.setId("project");
        Page page = new Page();
        page.setId("page");
        page.setName("page");
        UploadSession session = new UploadSession("project", "workspace", "user", 2, 2);
        session.setStatus(UploadSession.UploadSessionStatus.UPLOADING);
        Path xmlPath = tempDir.resolve("page.xml");
        Path imagePath = tempDir.resolve("page.png");
        Files.writeString(xmlPath, "<invalid/>");
        Files.writeString(imagePath, "image bytes");
        UploadSessionFile xml = new UploadSessionFile("page.xml", Files.size(xmlPath), "application/xml", "page", "xml", 1);
        xml.setTempFilePath(xmlPath.toString());
        UploadSessionFile image = new UploadSessionFile("page.png", Files.size(imagePath), "image/png", "page", "png", 1);
        image.setTempFilePath(imagePath.toString());
        when(sessions.findById("session")).thenReturn(Optional.of(session));
        when(projects.findById("project")).thenReturn(Optional.of(project));
        when(files.findBySessionIdAndStatus("session", UploadSessionFile.UploadFileStatus.UPLOADED)).thenReturn(List.of(xml, image));
        when(pages.findByProjectIdAndNameIn(eq("project"), any())).thenReturn(List.of(page));
        when(storage.storeFromPath(imagePath, "page.png", "image/png", "workspace", "project", StoredFile.StoredFileType.IMG, "user", true))
                .thenReturn(new HierarchicalFileStorageService.StoredFileDescriptor("image", "page.png", "page.png", "image/png", "png", Files.size(imagePath), "checksum", StoredFile.StoredFileType.IMG));
        when(images.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        processor.doProcessUploadSession("session");

        assertThat(xml.getStatus()).isEqualTo(UploadSessionFile.UploadFileStatus.FAILED);
        assertThat(image.getStatus()).isEqualTo(UploadSessionFile.UploadFileStatus.COMPLETED);
        assertThat(session.getFailedFiles()).isEqualTo(1);
        assertThat(session.getProcessedFiles()).isEqualTo(1);
        verifyNoInteractions(xmls);
    }

    @Test
    void legacyUploadConflictCannotReplaceExistingXmlWithInvalidXml() throws Exception {
        Path xmlPath = tempDir.resolve("page.xml");
        Files.writeString(xmlPath, "<invalid/>");
        Page page = new Page();
        UploadSessionFile file = new UploadSessionFile("page.xml", Files.size(xmlPath), "application/xml", "page", "xml", 1);
        file.setTempFilePath(xmlPath.toString());
        file.setStatus(UploadSessionFile.UploadFileStatus.CONFLICT);
        file.setConflictType("XML_FILE_EXISTS");
        when(files.findById("conflict")).thenReturn(Optional.of(file));
        when(pages.findByProjectIdAndName("project", "page")).thenReturn(Optional.of(page));
        UploadConflictService conflicts = new UploadConflictService(pages, images, xmls, files, null, storage, null, validation, null);

        assertThrows(InvalidPageXmlException.class, () -> conflicts.resolveConflicts("project", "user",
                new UploadConflictDto.BatchConflictResolutionRequest(List.of(new UploadConflictDto.ConflictResolution("conflict", "replace_with_new")))));

        verifyNoInteractions(storage, xmls);
        assertThat(file.getStatus()).isEqualTo(UploadSessionFile.UploadFileStatus.CONFLICT);
    }
}
