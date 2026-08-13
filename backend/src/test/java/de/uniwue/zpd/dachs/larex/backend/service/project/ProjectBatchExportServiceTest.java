package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectBatchExportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import de.uniwue.zpd.dachs.larex.backend.service.export.DocumentExportService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectBatchExportServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private WorkspaceAccessService workspaceAccessService;
    @Mock
    private ProjectPackageService projectPackageService;
    @Mock
    private DocumentExportService documentExportService;

    private ProjectBatchExportService service;
    private ArchiveIoService archiveIoService;

    @BeforeEach
    void setUp() {
        archiveIoService = new ArchiveIoService(new ObjectMapper());
        service = new ProjectBatchExportService(
                projectRepository,
                workspaceAccessService,
                projectPackageService,
                documentExportService,
                archiveIoService
        );
    }

    @Test
    void basicExportWritesProjectDirectoriesWithoutNestedArchivesAndDeduplicatesNames() throws Exception {
        Project first = project("project-1", "Shared name");
        Project second = project("project-2", "Shared name");
        when(projectRepository.findByIdAndLibraryWorkspaceId("project-1", "workspace-1")).thenReturn(Optional.of(first));
        when(projectRepository.findByIdAndLibraryWorkspaceId("project-2", "workspace-1")).thenReturn(Optional.of(second));
        doAnswer(invocation -> {
            String projectId = invocation.getArgument(1);
            java.util.zip.ZipOutputStream zip = invocation.getArgument(4);
            String entryPrefix = invocation.getArgument(5);
            archiveIoService.writeBytesEntry(
                    zip,
                    entryPrefix + "/payload.txt",
                    ("archive-" + projectId).getBytes(StandardCharsets.UTF_8)
            );
            return null;
        }).when(projectPackageService).writeBasicProjectExportEntries(
                eq("workspace-1"), any(), eq("user-1"), any(), any(), any());

        ProjectBatchExportDto.ExportRequest request = request(
                List.of("project-1", "project-2", "project-1"),
                ProjectBatchExportDto.ExportMode.BASIC,
                null
        );
        ProjectBatchExportService.PreparedBatchExport prepared =
                service.prepareBatchExport("workspace-1", "user-1", request);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeBatchExport(prepared, output);

        Map<String, byte[]> outerEntries = unzipEntries(output.toByteArray());
        assertEquals(List.of(
                "Shared name/",
                "Shared name/payload.txt",
                "2-Shared name/",
                "2-Shared name/payload.txt"
        ), outerEntries.keySet().stream().toList());
        assertEquals("archive-project-1", new String(outerEntries.get("Shared name/payload.txt"), StandardCharsets.UTF_8));
        assertEquals("archive-project-2", new String(outerEntries.get("2-Shared name/payload.txt"), StandardCharsets.UTF_8));
        verify(workspaceAccessService).requireWorkspaceAccess("workspace-1", "user-1");
    }

    @Test
    void convertedExportUsesRenderedFileNamesAndContent() throws Exception {
        Project project = project("project-1", "Project");
        when(projectRepository.findByIdAndLibraryWorkspaceId("project-1", "workspace-1")).thenReturn(Optional.of(project));
        when(documentExportService.exportProjectStream(eq("workspace-1"), eq("project-1"), eq("user-1"), any()))
                .thenReturn(new DocumentExportService.StreamingDocumentExportResult(
                        "Project.txt",
                        "text/plain",
                        output -> output.write("converted".getBytes(StandardCharsets.UTF_8))
                ));

        ProjectBatchExportService.PreparedBatchExport prepared = service.prepareBatchExport(
                "workspace-1",
                "user-1",
                request(List.of("project-1"), ProjectBatchExportDto.ExportMode.CONVERTED, DocumentExportDto.ExportFormat.TXT)
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeBatchExport(prepared, output);

        Map<String, String> entries = unzipTextEntries(output.toByteArray());
        assertEquals("converted", entries.get("Project/Project.txt"));
        assertEquals(List.of("Project/", "Project/Project.txt"), entries.keySet().stream().toList());
    }

    @Test
    void packageExportWritesPackageEntriesDirectlyIntoTheProjectDirectory() throws Exception {
        Project project = project("project-1", "Project");
        when(projectRepository.findByIdAndLibraryWorkspaceId("project-1", "workspace-1")).thenReturn(Optional.of(project));
        doAnswer(invocation -> {
            java.util.zip.ZipOutputStream zip = invocation.getArgument(4);
            String entryPrefix = invocation.getArgument(5);
            archiveIoService.writeBytesEntry(zip, entryPrefix + "/manifest.json", "{}".getBytes(StandardCharsets.UTF_8));
            archiveIoService.writeBytesEntry(zip, entryPrefix + "/pages/page/page.json", "{}".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(projectPackageService).writeProjectPackageEntries(
                eq("workspace-1"), eq("project-1"), eq("user-1"), any(), any(), any());

        ProjectBatchExportService.PreparedBatchExport prepared = service.prepareBatchExport(
                "workspace-1",
                "user-1",
                request(List.of("project-1"), ProjectBatchExportDto.ExportMode.PACKAGE, null)
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeBatchExport(prepared, output);

        Map<String, String> entries = unzipTextEntries(output.toByteArray());
        assertEquals(
                List.of("Project/", "Project/manifest.json", "Project/pages/page/page.json"),
                entries.keySet().stream().toList()
        );
        assertEquals("{}", entries.get("Project/manifest.json"));
        assertEquals("{}", entries.get("Project/pages/page/page.json"));
    }

    @Test
    void packageHistoryDefaultsOffAndCanBeEnabled() {
        ProjectBatchExportDto.ExportRequest workingRequest =
                request(List.of("project-1"), ProjectBatchExportDto.ExportMode.PACKAGE, null);
        assertFalse(workingRequest.toPackageExportRequest().includeXmlHistoryResolved());

        ProjectBatchExportDto.ExportRequest archivalRequest = new ProjectBatchExportDto.ExportRequest(
                workingRequest.projectIds(),
                workingRequest.mode(),
                workingRequest.targetPageXmlVersion(),
                workingRequest.embeddedOutputs(),
                workingRequest.format(),
                workingRequest.includePageDelimiters(),
                workingRequest.textLevel(),
                workingRequest.textVariantIndex(),
                workingRequest.pdfProfile(),
                workingRequest.teiProfile(),
                workingRequest.spreadsheetProfiles(),
                workingRequest.docxOptions(),
                workingRequest.imageVariantSelection(),
                true
        );
        assertTrue(archivalRequest.toPackageExportRequest().includeXmlHistoryResolved());
    }

    @Test
    void convertedZipExportIsFlattenedIntoTheProjectDirectory() throws Exception {
        Project project = project("project-1", "Project");
        when(projectRepository.findByIdAndLibraryWorkspaceId("project-1", "workspace-1")).thenReturn(Optional.of(project));
        when(documentExportService.exportProjectStream(eq("workspace-1"), eq("project-1"), eq("user-1"), any()))
                .thenReturn(new DocumentExportService.StreamingDocumentExportResult(
                        "Project.alto.zip",
                        "application/zip",
                        output -> archiveIoService.writeZip(output, zip -> {
                            archiveIoService.writeBytesEntry(zip, "page-1.xml", "first".getBytes(StandardCharsets.UTF_8));
                            archiveIoService.writeBytesEntry(zip, "page-2.xml", "second".getBytes(StandardCharsets.UTF_8));
                        })
                ));

        ProjectBatchExportService.PreparedBatchExport prepared = service.prepareBatchExport(
                "workspace-1",
                "user-1",
                request(List.of("project-1"), ProjectBatchExportDto.ExportMode.CONVERTED, DocumentExportDto.ExportFormat.ALTO_XML)
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeBatchExport(prepared, output);

        Map<String, String> entries = unzipTextEntries(output.toByteArray());
        assertEquals(List.of("Project/", "Project/page-1.xml", "Project/page-2.xml"), entries.keySet().stream().toList());
        assertEquals("first", entries.get("Project/page-1.xml"));
        assertEquals("second", entries.get("Project/page-2.xml"));
    }

    @Test
    void prepareRejectsProjectsOutsideTheWorkspace() {
        when(projectRepository.findByIdAndLibraryWorkspaceId("project-1", "workspace-1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.prepareBatchExport(
                "workspace-1",
                "user-1",
                request(List.of("project-1"), ProjectBatchExportDto.ExportMode.PACKAGE, null)
        ));
    }

    private Project project(String id, String name) {
        Project project = org.mockito.Mockito.mock(Project.class);
        when(project.getId()).thenReturn(id);
        when(project.getName()).thenReturn(name);
        return project;
    }

    private ProjectBatchExportDto.ExportRequest request(List<String> ids,
                                                        ProjectBatchExportDto.ExportMode mode,
                                                        DocumentExportDto.ExportFormat format) {
        return new ProjectBatchExportDto.ExportRequest(
                ids,
                mode,
                "2019-07-15",
                List.of(),
                format,
                false,
                DocumentExportDto.TextLevel.PAGE,
                0,
                DocumentExportDto.PdfProfile.SEARCHABLE,
                DocumentExportDto.TeiProfile.STANDARD,
                List.of(DocumentExportDto.SpreadsheetProfile.PAGE_METADATA),
                new DocumentExportDto.DocxOptions(true, true, false, false, 0.75d)
        );
    }

    private Map<String, String> unzipTextEntries(byte[] archive) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : unzipEntries(archive).entrySet()) {
            entries.put(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));
        }
        return entries;
    }

    private Map<String, byte[]> unzipEntries(byte[] archive) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
