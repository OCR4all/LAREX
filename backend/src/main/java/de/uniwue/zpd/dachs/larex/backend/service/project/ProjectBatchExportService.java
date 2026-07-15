package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.dto.ProjectBatchExportDto;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import de.uniwue.zpd.dachs.larex.backend.service.export.DocumentExportService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
@Transactional(readOnly = true)
public class ProjectBatchExportService {

    private final ProjectRepository projectRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ProjectPackageService projectPackageService;
    private final DocumentExportService documentExportService;
    private final ArchiveIoService archiveIoService;

    public ProjectBatchExportService(ProjectRepository projectRepository,
                                     WorkspaceAccessService workspaceAccessService,
                                     ProjectPackageService projectPackageService,
                                     DocumentExportService documentExportService,
                                     ArchiveIoService archiveIoService) {
        this.projectRepository = projectRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.projectPackageService = projectPackageService;
        this.documentExportService = documentExportService;
        this.archiveIoService = archiveIoService;
    }

    public PreparedBatchExport prepareBatchExport(String workspaceId,
                                                  String userId,
                                                  ProjectBatchExportDto.ExportRequest request) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        if (request.mode() == ProjectBatchExportDto.ExportMode.CONVERTED
                && (request.format() == null || !request.format().supportsProjectExportEndpoint())) {
            throw new IllegalArgumentException("A supported converted-output format is required");
        }

        List<String> projectIds = new ArrayList<>(new LinkedHashSet<>(request.projectIds()));
        List<ProjectTarget> projects = projectIds.stream()
                .map(projectId -> projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)
                        .map(project -> new ProjectTarget(project.getId(), project.getName()))
                        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId)))
                .toList();

        return new PreparedBatchExport(workspaceId, userId, projects, request);
    }

    public void writeBatchExport(PreparedBatchExport batch, OutputStream outputStream) throws IOException {
        Set<String> usedProjectDirectories = new HashSet<>();
        archiveIoService.writeZip(outputStream, zipOut -> {
            for (ProjectTarget project : batch.projects()) {
                String projectDirectory = uniqueEntryName(
                        sanitizeFileName(project.name(), "project"),
                        usedProjectDirectories
                );
                archiveIoService.writeDirectoryEntry(zipOut, projectDirectory);

                switch (batch.request().mode()) {
                    case BASIC -> projectPackageService.writeBasicProjectExportEntries(
                            batch.workspaceId(),
                            project.id(),
                            batch.userId(),
                            batch.request().toPackageExportRequest(),
                            zipOut,
                            projectDirectory
                    );
                    case PACKAGE -> projectPackageService.writeProjectPackageEntries(
                            batch.workspaceId(),
                            project.id(),
                            batch.userId(),
                            batch.request().toPackageExportRequest(),
                            zipOut,
                            projectDirectory
                    );
                    case CONVERTED -> {
                        DocumentExportService.StreamingDocumentExportResult export =
                                documentExportService.exportProjectStream(
                                        batch.workspaceId(),
                                        project.id(),
                                        batch.userId(),
                                        batch.request().toDocumentExportRequest()
                                );
                        writeConvertedExport(zipOut, projectDirectory, export);
                    }
                }
            }
        });
    }

    private void writeConvertedExport(ZipOutputStream zipOut,
                                      String projectDirectory,
                                      DocumentExportService.StreamingDocumentExportResult export) throws IOException {
        if (!isZipExport(export)) {
            archiveIoService.writeStreamEntry(
                    zipOut,
                    projectDirectory + "/" + archiveIoService.normalizeArchivePath(export.fileName()),
                    export.writer()::write
            );
            return;
        }

        Path tempArchive = Files.createTempFile("larex-batch-converted-export-", ".zip");
        try {
            try (OutputStream tempOut = Files.newOutputStream(tempArchive)) {
                export.writer().write(tempOut);
            }
            try (ZipInputStream nestedZip = new ZipInputStream(Files.newInputStream(tempArchive))) {
                ZipEntry nestedEntry;
                while ((nestedEntry = nestedZip.getNextEntry()) != null) {
                    if (!nestedEntry.isDirectory()) {
                        String nestedEntryName = archiveIoService.normalizeArchivePath(nestedEntry.getName());
                        archiveIoService.writeStreamEntry(
                                zipOut,
                                projectDirectory + "/" + nestedEntryName,
                                nestedZip::transferTo
                        );
                    }
                    nestedZip.closeEntry();
                }
            }
        } finally {
            Files.deleteIfExists(tempArchive);
        }
    }

    private boolean isZipExport(DocumentExportService.StreamingDocumentExportResult export) {
        return "application/zip".equalsIgnoreCase(export.contentType())
                || export.fileName().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private String uniqueEntryName(String candidate, Set<String> usedEntryNames) {
        String normalized = candidate == null || candidate.isBlank() ? "project-export" : candidate;
        if (usedEntryNames.add(normalized)) {
            return normalized;
        }

        int index = 2;
        while (!usedEntryNames.add(index + "-" + normalized)) {
            index++;
        }
        return index + "-" + normalized;
    }

    private String sanitizeFileName(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String sanitized = value.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized) ? fallback : sanitized;
    }

    public record PreparedBatchExport(
            String workspaceId,
            String userId,
            List<ProjectTarget> projects,
            ProjectBatchExportDto.ExportRequest request
    ) {
    }

    public record ProjectTarget(String id, String name) {
    }
}
