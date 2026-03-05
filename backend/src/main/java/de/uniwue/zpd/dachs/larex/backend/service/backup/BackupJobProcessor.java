package de.uniwue.zpd.dachs.larex.backend.service.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.BackupJobDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UtilityPackageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.utility.UtilityPackageService;
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
    private final UtilityPackageService utilityPackageService;
    private final ObjectMapper objectMapper;

    public BackupJobProcessor(BackupJobService backupJobService,
                              ProjectRepository projectRepository,
                              PersonalWorkspaceRepository personalWorkspaceRepository,
                              TeamWorkspaceRepository teamWorkspaceRepository,
                              ArchiveIoService archiveIoService,
                              ProjectPackageService projectPackageService,
                              UtilityPackageService utilityPackageService,
                              ObjectMapper objectMapper) {
        this.backupJobService = backupJobService;
        this.projectRepository = projectRepository;
        this.personalWorkspaceRepository = personalWorkspaceRepository;
        this.teamWorkspaceRepository = teamWorkspaceRepository;
        this.archiveIoService = archiveIoService;
        this.projectPackageService = projectPackageService;
        this.utilityPackageService = utilityPackageService;
        this.objectMapper = objectMapper;
    }

    public void processJob(String jobId,
                           String userId,
                           BackupJobDto.CreateJobRequest request,
                           String normalizedSourcePath) {
        try {
            if (request.type() == BackupJobDto.JobType.DUMP) {
                processDump(jobId, userId, request);
            } else {
                processReseed(jobId, userId, request, normalizedSourcePath);
            }
        } catch (Exception e) {
            log.error("Backup job {} failed: {}", jobId, e.getMessage(), e);
            backupJobService.markFailed(jobId, e.getMessage());
        }
    }

    private void processDump(String jobId,
                             String userId,
                             BackupJobDto.CreateJobRequest request) throws IOException {
        List<AbstractWorkspace> workspaces = loadAllWorkspaces();

        long totalProjects = workspaces.stream()
                .mapToLong(ws -> projectRepository.findByLibraryWorkspaceId(ws.getId()).size())
                .sum();
        long totalItems = workspaces.size() + totalProjects;

        backupJobService.markRunning(jobId, totalItems, "Preparing dump");
        if (backupJobService.isCancelled(jobId)) {
            return;
        }

        Path outputPath = resolveDumpOutputPath(request.outputPath());
        List<DumpWorkspaceEntry> workspaceEntries = new ArrayList<>();
        final long[] processed = {0};

        archiveIoService.writeZip(outputPath, zipOut -> {
            for (AbstractWorkspace workspace : workspaces) {
                if (backupJobService.isCancelled(jobId)) {
                    return;
                }

                String workspaceId = workspace.getId();
                String utilitiesEntry = "dump/utilities/" + workspaceId + ".larex-utilities.json";
                UtilityPackageDto.UtilityPackage utilityPackage = utilityPackageService.buildUtilityPackage(
                        workspaceId,
                        new UtilityPackageDto.ExportRequest(null, true)
                );
                archiveIoService.writeJsonEntry(zipOut, utilitiesEntry, utilityPackage);

                processed[0]++;
                backupJobService.updateProgress(jobId, processed[0], totalItems, "Exported utilities for " + workspaceId);

                List<Project> projects = projectRepository.findByLibraryWorkspaceId(workspaceId).stream()
                        .sorted(Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();

                List<DumpProjectEntry> projectEntries = new ArrayList<>();
                for (Project project : projects) {
                    if (backupJobService.isCancelled(jobId)) {
                        return;
                    }

                    byte[] projectPackage = projectPackageService.exportProjectPackageInternal(
                            workspaceId,
                            project.getId(),
                            new ProjectPackageDto.ExportRequest(null)
                    );

                    String projectEntryPath = "dump/projects/" + workspaceId + "/" + project.getId() + ".larex-project.zip";
                    archiveIoService.writeBytesEntry(zipOut, projectEntryPath, projectPackage);
                    projectEntries.add(new DumpProjectEntry(project.getId(), project.getName(), projectEntryPath));

                    processed[0]++;
                    backupJobService.updateProgress(jobId, processed[0], totalItems, "Exported project " + project.getName());
                }

                workspaceEntries.add(new DumpWorkspaceEntry(
                        workspaceId,
                        workspace.getName(),
                        utilitiesEntry,
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

    private void processReseed(String jobId,
                               String userId,
                               BackupJobDto.CreateJobRequest request,
                               String normalizedSourcePath) throws IOException {
        if (normalizedSourcePath == null || normalizedSourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath is required for reseed jobs");
        }

        Path source = Paths.get(normalizedSourcePath).toAbsolutePath().normalize();
        backupJobService.markRunning(jobId, 1, "Extracting dump archive");

        if (backupJobService.isCancelled(jobId)) {
            return;
        }

        Path tempDir = archiveIoService.extractZipToTempDir(Files.newInputStream(source), "larex-reseed-");
        try {
            Path manifestPath = tempDir.resolve("dump/manifest.json");
            if (!Files.exists(manifestPath)) {
                throw new IllegalArgumentException("Dump manifest missing: dump/manifest.json");
            }

            DumpManifest manifest = objectMapper.readValue(manifestPath.toFile(), DumpManifest.class);
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

                if (workspaceEntry.utilitiesPath() != null) {
                    Path utilitiesPath = tempDir.resolve(archiveIoService.normalizeArchivePath(workspaceEntry.utilitiesPath()));
                    if (Files.exists(utilitiesPath)) {
                        String content = Files.readString(utilitiesPath);
                        utilityPackageService.importUtilityPackageFromContentInternal(targetWorkspaceId, userId, content);
                    } else {
                        backupJobService.addWarning(jobId,
                                "Utilities package missing for workspace " + workspaceEntry.workspaceId());
                    }
                }

                processed++;
                backupJobService.updateProgress(jobId, processed, totalItems,
                        "Imported utilities into " + targetWorkspaceId);

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

            Path resultPath = resolveReseedMarkerPath(request.outputPath());
            Files.createDirectories(resultPath.getParent());
            Files.writeString(resultPath,
                    "Reseed completed at " + LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            backupJobService.markCompleted(jobId, resultPath.toString());
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private List<AbstractWorkspace> loadAllWorkspaces() {
        List<AbstractWorkspace> workspaces = new ArrayList<>();
        workspaces.addAll(personalWorkspaceRepository.findAll());
        workspaces.addAll(teamWorkspaceRepository.findAll());
        workspaces.sort(Comparator.comparing(AbstractWorkspace::getName, String.CASE_INSENSITIVE_ORDER));
        return workspaces;
    }

    private Path resolveDumpOutputPath(String outputPathRaw) {
        Path outputPath = Paths.get(outputPathRaw).toAbsolutePath().normalize();
        if (Files.exists(outputPath) && Files.isDirectory(outputPath)) {
            return outputPath.resolve(defaultDumpFileName());
        }
        if (!Files.exists(outputPath) && !outputPath.toString().endsWith(".zip")) {
            return outputPath.resolve(defaultDumpFileName());
        }
        return outputPath;
    }

    private Path resolveReseedMarkerPath(String outputPathRaw) {
        Path outputPath = Paths.get(outputPathRaw).toAbsolutePath().normalize();
        if (Files.exists(outputPath) && Files.isDirectory(outputPath)) {
            return outputPath.resolve("reseed-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        }
        if (!outputPath.toString().endsWith(".txt")) {
            if (outputPath.getParent() != null) {
                return outputPath.resolve("reseed-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
            }
        }
        return outputPath;
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
            String utilitiesPath,
            List<DumpProjectEntry> projects
    ) {
    }

    private record DumpProjectEntry(
            String projectId,
            String projectName,
            String packagePath
    ) {
    }
}
