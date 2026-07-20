package de.uniwue.zpd.dachs.larex.backend.service.backup;

import tools.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.config.BackupProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.BackupJobDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ToolkitPackageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.toolkit.ToolkitPackageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BackupJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(BackupJobProcessor.class);

    private final BackupJobService backupJobService;
    private final ProjectRepository projectRepository;
    private final PersonalWorkspaceRepository personalWorkspaceRepository;
    private final TeamWorkspaceRepository teamWorkspaceRepository;
    private final ArchiveIoService archiveIoService;
    private final ProjectPackageService projectPackageService;
    private final ToolkitPackageService toolkitPackageService;
    private final ObjectMapper objectMapper;
    private final BackupProperties backupProperties;

    public BackupJobProcessor(BackupJobService backupJobService,
                              ProjectRepository projectRepository,
                              PersonalWorkspaceRepository personalWorkspaceRepository,
                              TeamWorkspaceRepository teamWorkspaceRepository,
                              ArchiveIoService archiveIoService,
                              ProjectPackageService projectPackageService,
                              ToolkitPackageService toolkitPackageService,
                              ObjectMapper objectMapper,
                              BackupProperties backupProperties) {
        this.backupJobService = backupJobService;
        this.projectRepository = projectRepository;
        this.personalWorkspaceRepository = personalWorkspaceRepository;
        this.teamWorkspaceRepository = teamWorkspaceRepository;
        this.archiveIoService = archiveIoService;
        this.projectPackageService = projectPackageService;
        this.toolkitPackageService = toolkitPackageService;
        this.objectMapper = objectMapper;
        this.backupProperties = backupProperties;
    }

    public void processJob(String jobId,
                           String userId,
                           BackupJobDto.CreateJobRequest request,
                           String normalizedSourcePath,
                           String normalizedOutputPath) {
        try {
            switch (request.type()) {
                case DUMP -> processDump(jobId, request, normalizedOutputPath);
                case VERIFY -> processVerify(jobId, normalizedSourcePath);
                case RESEED -> processReseed(
                        jobId,
                        userId,
                        request,
                        normalizedSourcePath,
                        normalizedOutputPath
                );
            }
        } catch (Exception e) {
            log.error("Backup job {} failed: {}", jobId, e.getMessage(), e);
            backupJobService.markFailed(jobId, e.getMessage());
        }
    }

    private void processDump(String jobId,
                             BackupJobDto.CreateJobRequest request,
                             String normalizedOutputPath) throws IOException {
        List<AbstractWorkspace> workspaces = loadWorkspaces(request.workspaceId());

        long totalProjects = workspaces.stream()
                .mapToLong(ws -> projectRepository.findByLibraryWorkspaceId(ws.getId()).size())
                .sum();
        long totalItems = workspaces.size() + totalProjects;

        backupJobService.markRunning(jobId, totalItems, "Preparing dump");
        if (backupJobService.isCancelled(jobId)) {
            return;
        }

        Path outputPath = resolveDumpOutputPath(normalizedOutputPath);
        List<DumpWorkspaceEntry> workspaceEntries = new ArrayList<>();
        final long[] processed = {0};

        archiveIoService.writeZip(outputPath, zipOut -> {
            for (AbstractWorkspace workspace : workspaces) {
                if (backupJobService.isCancelled(jobId)) {
                    return;
                }

                String workspaceId = workspace.getId();
                String toolkitEntry = "dump/toolkit/" + workspaceId + ".larex-toolkit.json";
                ToolkitPackageDto.ToolkitPackage toolkitPackage = toolkitPackageService.buildToolkitPackage(
                        workspaceId,
                        new ToolkitPackageDto.ExportRequest(null, true)
                );
                String toolkitSha256 = archiveIoService.writeJsonEntryWithSha256(
                        zipOut,
                        toolkitEntry,
                        toolkitPackage
                );

                processed[0]++;
                backupJobService.updateProgress(jobId, processed[0], totalItems, "Exported toolkit resources for " + workspaceId);

                List<Project> projects = projectRepository.findByLibraryWorkspaceId(workspaceId).stream()
                        .sorted(Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();

                List<DumpProjectEntry> projectEntries = new ArrayList<>();
                for (Project project : projects) {
                    if (backupJobService.isCancelled(jobId)) {
                        return;
                    }

                    String projectEntryPath = "dump/projects/" + workspaceId + "/" + project.getId() + ".larex-project.zip";
                    backupJobService.updateProgress(
                            jobId,
                            processed[0],
                            totalItems,
                            "Exporting project " + project.getName()
                    );
                    String projectSha256 = archiveIoService.writeStreamEntryWithSha256(
                            zipOut,
                            projectEntryPath,
                            Deflater.NO_COMPRESSION,
                            entryOutput -> projectPackageService.writeProjectPackageInternalUncompressed(
                                    workspaceId,
                                    project.getId(),
                                    new ProjectPackageDto.ExportRequest(null, null, null, true),
                                    entryOutput
                            )
                    );
                    projectEntries.add(new DumpProjectEntry(
                            project.getId(),
                            project.getName(),
                            projectEntryPath,
                            projectSha256
                    ));

                    processed[0]++;
                    backupJobService.updateProgress(jobId, processed[0], totalItems, "Exported project " + project.getName());
                }

                workspaceEntries.add(new DumpWorkspaceEntry(
                        workspaceId,
                        workspace.getName(),
                        toolkitEntry,
                        toolkitSha256,
                        projectEntries
                ));
            }

            DumpManifest manifest = new DumpManifest(
                    "1.0",
                    LocalDateTime.now(),
                    workspaceEntries
            );
            archiveIoService.writeJsonEntry(zipOut, "dump/manifest.json", manifest);
        });

        if (backupJobService.isCancelled(jobId)) {
            return;
        }

        backupJobService.markCompleted(jobId, outputPath.toString());
    }

    private void processVerify(String jobId, String normalizedSourcePath) throws IOException {
        Path source = requireSourcePath(normalizedSourcePath, "verify");
        backupJobService.markRunning(jobId, 1, "Verifying dump archive");
        if (backupJobService.isCancelled(jobId)) {
            return;
        }

        VerifiedDump verifiedDump = verifyDumpArchive(jobId, source);
        try {
            if (backupJobService.isCancelled(jobId)) {
                return;
            }
            backupJobService.updateProgress(
                    jobId,
                    verifiedDump.report().entryCount(),
                    verifiedDump.report().entryCount(),
                    "Verified dump archive"
            );
            backupJobService.markCompleted(jobId, source.toString());
        } finally {
            deleteDirectoryQuietly(verifiedDump.directory());
        }
    }

    private void processReseed(String jobId,
                               String userId,
                               BackupJobDto.CreateJobRequest request,
                               String normalizedSourcePath,
                               String normalizedOutputPath) throws IOException {
        Path source = requireSourcePath(normalizedSourcePath, "reseed");
        backupJobService.markRunning(jobId, 1, "Verifying dump archive");

        if (backupJobService.isCancelled(jobId)) {
            return;
        }

        VerifiedDump verifiedDump = verifyDumpArchive(jobId, source);
        Path tempDir = verifiedDump.directory();
        try {
            if (backupJobService.isCancelled(jobId)) {
                return;
            }
            DumpManifest manifest = verifiedDump.manifest();
            long totalItems = manifest.workspaces().stream()
                    .mapToLong(ws -> 1L + (ws.projects() == null ? 0 : ws.projects().size()))
                    .sum();
            backupJobService.updateProgress(jobId, 0, totalItems, "Starting reseed");

            long processed = 0;
            for (DumpWorkspaceEntry workspaceEntry : manifest.workspaces()) {
                if (backupJobService.isCancelled(jobId)) {
                    return;
                }

                String targetWorkspaceId = resolveTargetWorkspaceId(workspaceEntry.workspaceId(), request.workspaceMapping());
                if (!workspaceExists(targetWorkspaceId)) {
                    backupJobService.addWarning(jobId,
                            "Skipping workspace " + workspaceEntry.workspaceId() + " (target workspace not found: " + targetWorkspaceId + ")");
                    processed += 1L + (workspaceEntry.projects() == null ? 0 : workspaceEntry.projects().size());
                    backupJobService.updateProgress(jobId, processed, totalItems,
                            "Skipped workspace " + workspaceEntry.workspaceId());
                    continue;
                }

                if (workspaceEntry.toolkitPath() != null) {
                    Path toolkitPath = tempDir.resolve(archiveIoService.normalizeArchivePath(workspaceEntry.toolkitPath()));
                    if (Files.exists(toolkitPath)) {
                        String content = Files.readString(toolkitPath);
                        toolkitPackageService.importToolkitPackageFromContentInternal(targetWorkspaceId, userId, content);
                    } else {
                        backupJobService.addWarning(jobId,
                                "Toolkit package missing for workspace " + workspaceEntry.workspaceId());
                    }
                }

                processed++;
                backupJobService.updateProgress(jobId, processed, totalItems,
                        "Imported toolkit into " + targetWorkspaceId);

                if (workspaceEntry.projects() != null) {
                    for (DumpProjectEntry projectEntry : workspaceEntry.projects()) {
                        if (backupJobService.isCancelled(jobId)) {
                            return;
                        }

                        Path projectArchive = tempDir.resolve(archiveIoService.normalizeArchivePath(projectEntry.packagePath()));
                        if (!Files.exists(projectArchive)) {
                            backupJobService.addWarning(jobId,
                                    "Project package missing: " + projectEntry.packagePath());
                            processed++;
                            backupJobService.updateProgress(jobId, processed, totalItems,
                                    "Skipped missing project archive " + projectEntry.projectId());
                            continue;
                        }

                        projectPackageService.importProjectPackageFromPathInternal(targetWorkspaceId, userId, projectArchive);

                        processed++;
                        backupJobService.updateProgress(jobId, processed, totalItems,
                                "Imported project " + projectEntry.projectName());
                    }
                }
            }

            if (backupJobService.isCancelled(jobId)) {
                return;
            }

            Path resultPath = resolveReseedMarkerPath(normalizedOutputPath);
            Files.createDirectories(resultPath.getParent());
            Files.writeString(resultPath,
                    "Reseed completed at " + LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            backupJobService.markCompleted(jobId, resultPath.toString());
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private VerifiedDump verifyDumpArchive(String jobId, Path source) throws IOException {
        ArchiveIoService.ExtractionResult report = archiveIoService.extractZipToTempDirWithReport(
                Files.newInputStream(source),
                "larex-dump-verify-",
                extractionLimits()
        );
        Path tempDir = report.directory();
        try {
            Path manifestPath = tempDir.resolve("dump/manifest.json");
            if (!Files.isRegularFile(manifestPath)) {
                throw new IllegalArgumentException("Dump manifest missing: dump/manifest.json");
            }

            DumpManifest manifest = objectMapper.readValue(manifestPath.toFile(), DumpManifest.class);
            if (!"1.0".equals(manifest.schemaVersion())) {
                throw new IllegalArgumentException("Unsupported dump schema version: " + manifest.schemaVersion());
            }
            if (manifest.workspaces() == null) {
                throw new IllegalArgumentException("Dump manifest has no workspaces");
            }

            long referencedEntries = 1;
            for (DumpWorkspaceEntry workspace : manifest.workspaces()) {
                if (workspace.toolkitPath() != null) {
                    verifyManifestEntry(
                            tempDir,
                            workspace.toolkitPath(),
                            workspace.toolkitSha256()
                    );
                    referencedEntries++;
                }
                if (workspace.projects() == null) {
                    continue;
                }
                for (DumpProjectEntry project : workspace.projects()) {
                    verifyManifestEntry(
                            tempDir,
                            project.packagePath(),
                            project.sha256()
                    );
                    referencedEntries++;
                }
            }
            if (referencedEntries > backupJobService.getMaxFilesPerJob()) {
                throw new IllegalArgumentException(
                        "Dump references more than " + backupJobService.getMaxFilesPerJob() + " files"
                );
            }
            return new VerifiedDump(tempDir, manifest, report);
        } catch (IOException | RuntimeException exception) {
            deleteDirectoryQuietly(tempDir);
            throw exception;
        }
    }

    private void verifyManifestEntry(Path tempDir,
                                     String archivePath,
                                     String expectedSha256) throws IOException {
        Path entryPath = tempDir.resolve(archiveIoService.normalizeArchivePath(archivePath)).normalize();
        if (!entryPath.startsWith(tempDir) || !Files.isRegularFile(entryPath)) {
            throw new IllegalArgumentException("Dump entry missing: " + archivePath);
        }
        if (expectedSha256 == null || !expectedSha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Dump entry has no valid SHA-256 checksum: " + archivePath);
        }
        String actualSha256 = archiveIoService.sha256(entryPath);
        if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
            throw new IllegalArgumentException("Dump entry checksum mismatch: " + archivePath);
        }
    }

    private ArchiveIoService.ExtractionLimits extractionLimits() {
        return new ArchiveIoService.ExtractionLimits(
                backupProperties.getMaxArchiveBytes(),
                backupProperties.getMaxArchiveEntries(),
                backupProperties.getMaxArchiveEntryBytes(),
                backupProperties.getMaxArchiveTotalBytes(),
                backupProperties.getMaxArchiveCompressionRatio()
        );
    }

    private Path requireSourcePath(String normalizedSourcePath, String jobType) {
        if (normalizedSourcePath == null || normalizedSourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath is required for " + jobType + " jobs");
        }
        return Paths.get(normalizedSourcePath).toAbsolutePath().normalize();
    }

    List<AbstractWorkspace> loadWorkspaces(String workspaceId) {
        if (workspaceId != null && !workspaceId.isBlank()) {
            var personalWorkspace = personalWorkspaceRepository.findById(workspaceId);
            if (personalWorkspace.isPresent()) {
                return List.of(personalWorkspace.get());
            }
            return teamWorkspaceRepository.findById(workspaceId)
                    .<List<AbstractWorkspace>>map(List::of)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Workspace not found: " + workspaceId
                    ));
        }

        List<AbstractWorkspace> workspaces = new ArrayList<>();
        workspaces.addAll(personalWorkspaceRepository.findAll());
        workspaces.addAll(teamWorkspaceRepository.findAll());
        workspaces.sort(Comparator.comparing(AbstractWorkspace::getName, String.CASE_INSENSITIVE_ORDER));
        return workspaces;
    }

    private Path resolveDumpOutputPath(String outputDirectoryRaw) throws IOException {
        Path outputDirectory = requireOutputDirectory(outputDirectoryRaw);
        return outputDirectory.resolve(defaultDumpFileName());
    }

    private Path resolveReseedMarkerPath(String outputDirectoryRaw) throws IOException {
        Path outputDirectory = requireOutputDirectory(outputDirectoryRaw);
        return outputDirectory.resolve(
                "reseed-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt"
        );
    }

    private Path requireOutputDirectory(String outputDirectoryRaw) throws IOException {
        if (outputDirectoryRaw == null || outputDirectoryRaw.isBlank()) {
            throw new IllegalArgumentException("Backup output directory is not configured");
        }
        Path outputDirectory = Paths.get(outputDirectoryRaw).toAbsolutePath().normalize();
        Files.createDirectories(outputDirectory);
        return outputDirectory;
    }

    private String defaultDumpFileName() {
        return "larex-dump-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".larex-dump.zip";
    }

    private String resolveTargetWorkspaceId(String sourceWorkspaceId, Map<String, String> workspaceMapping) {
        if (workspaceMapping == null || workspaceMapping.isEmpty()) {
            return sourceWorkspaceId;
        }
        return workspaceMapping.getOrDefault(sourceWorkspaceId, sourceWorkspaceId);
    }

    private boolean workspaceExists(String workspaceId) {
        return personalWorkspaceRepository.findById(workspaceId).isPresent()
                || teamWorkspaceRepository.findById(workspaceId).isPresent();
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private record DumpManifest(
            String schemaVersion,
            LocalDateTime createdAt,
            List<DumpWorkspaceEntry> workspaces
    ) {
    }

    private record DumpWorkspaceEntry(
            String workspaceId,
            String workspaceName,
            String toolkitPath,
            String toolkitSha256,
            List<DumpProjectEntry> projects
    ) {
    }

    private record DumpProjectEntry(
            String projectId,
            String projectName,
            String packagePath,
            String sha256
    ) {
    }

    private record VerifiedDump(
            Path directory,
            DumpManifest manifest,
            ArchiveIoService.ExtractionResult report
    ) {
    }

}
