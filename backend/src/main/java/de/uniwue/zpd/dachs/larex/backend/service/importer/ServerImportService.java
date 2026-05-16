package de.uniwue.zpd.dachs.larex.backend.service.importer;

import de.uniwue.zpd.dachs.larex.backend.config.ImportProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.ImportJobDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ImportJob.ImportJobStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.ImportJob;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.ImportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceStorageQuotaService;
import de.uniwue.zpd.dachs.larex.backend.util.ImageFileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServerImportService {

    private static final Logger log = LoggerFactory.getLogger(ServerImportService.class);

    private final ImportJobRepository importJobRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final WorkspaceStorageQuotaService quotaService;
    private final AsyncImportProcessor asyncImportProcessor;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final ImportProperties properties;

    private List<Path> allowedPaths;

    public ServerImportService(ImportJobRepository importJobRepository,
                               ProjectRepository projectRepository,
                               PageRepository pageRepository,
                               WorkspaceStorageQuotaService quotaService,
                               AsyncImportProcessor asyncImportProcessor,
                               WorkspaceQuotaGuardService workspaceQuotaGuardService,
                               ImportProperties properties) {
        this.importJobRepository = importJobRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.quotaService = quotaService;
        this.asyncImportProcessor = asyncImportProcessor;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.properties = properties;
    }

    @jakarta.annotation.PostConstruct
    private void initAllowedPaths() {
        allowedPaths = new ArrayList<>();
        if (properties.getAllowedPaths() != null) {
            for (String pathStr : properties.getAllowedPaths()) {
                if (pathStr == null || pathStr.isBlank()) {
                    continue;
                }
                try {
                    Path path = Paths.get(pathStr.trim()).toAbsolutePath().normalize();
                    allowedPaths.add(path);
                    log.info("Allowed import path configured: {}", path);
                } catch (Exception e) {
                    log.warn("Invalid allowed path: {}", pathStr, e);
                }
            }
        }
        if (allowedPaths.isEmpty()) {
            log.warn("No allowed import paths configured. Server-side import will be unavailable.");
        }
    }

    public ImportJobDto.ValidatePathResponse validatePath(String pathStr) {
        if (!properties.isEnabled()) {
            return new ImportJobDto.ValidatePathResponse(false, null, "Server-side import is disabled");
        }

        if (pathStr == null || pathStr.isBlank()) {
            return new ImportJobDto.ValidatePathResponse(false, null, "Path is required");
        }

        try {
            Path path = Paths.get(pathStr).toAbsolutePath().normalize();

            // Security: Check path is within allowed directories
            boolean isAllowed = false;
            for (Path allowedPath : allowedPaths) {
                if (path.startsWith(allowedPath)) {
                    isAllowed = true;
                    break;
                }
            }

            if (!isAllowed) {
                return new ImportJobDto.ValidatePathResponse(false, null,
                        "Path is not within allowed directories. Allowed: " + allowedPaths);
            }

            // Check path exists and is a directory
            if (!Files.exists(path)) {
                return new ImportJobDto.ValidatePathResponse(false, null, "Path does not exist");
            }

            if (!Files.isDirectory(path)) {
                return new ImportJobDto.ValidatePathResponse(false, null, "Path is not a directory");
            }

            if (!Files.isReadable(path)) {
                return new ImportJobDto.ValidatePathResponse(false, null, "Path is not readable");
            }

            return new ImportJobDto.ValidatePathResponse(true, path.toString(), null);

        } catch (Exception e) {
            log.error("Error validating path: {}", pathStr, e);
            return new ImportJobDto.ValidatePathResponse(false, null, "Invalid path: " + e.getMessage());
        }
    }

    public ImportJobDto.ScanResponse scanDirectory(String pathStr, String projectId, String workspaceId) throws IOException {
        ImportJobDto.ValidatePathResponse validation = validatePath(pathStr);
        if (!validation.valid()) {
            throw new IllegalArgumentException(validation.errorMessage());
        }

        Path path = Paths.get(validation.normalizedPath());

        ImportScanSummary scanSummary = scanImportSource(path, projectId);

        // Check quota
        boolean quotaExceeded = false;
        long availableQuotaBytes = 0;
        if (workspaceId != null) {
            try {
                var quotaInfo = quotaService.getQuotaInfo(workspaceId);
                availableQuotaBytes = quotaInfo.remainingBytes();
                quotaExceeded = scanSummary.totalSizeBytes() > availableQuotaBytes;
                if (quotaExceeded) {
                    scanSummary.warnings().add("Import size (" + formatBytes(scanSummary.totalSizeBytes()) +
                            ") exceeds available quota (" + formatBytes(availableQuotaBytes) + ")");
                }
            } catch (Exception e) {
                log.warn("Could not check quota for workspace: {}", workspaceId, e);
            }
        }

        return new ImportJobDto.ScanResponse(
                path.toString(),
                scanSummary.imageCount(),
                scanSummary.xmlCount(),
                scanSummary.totalSizeBytes(),
                scanSummary.files(),
                scanSummary.conflicts(),
                scanSummary.warnings(),
                quotaExceeded,
                availableQuotaBytes
        );
    }

    public ImportJobDto.JobResponse createImportJob(String userId, String workspaceId,
                                                     ImportJobDto.CreateJobRequest request) {
        ImportJobDto.ValidatePathResponse validation = validatePath(request.sourcePath());
        if (!validation.valid()) {
            throw new IllegalArgumentException(validation.errorMessage());
        }

        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + request.projectId()));

        if (!project.getLibrary().getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("Project does not belong to workspace");
        }

        // Check for existing active imports for this project
        List<ImportJobStatus> activeStatuses = List.of(
                ImportJobStatus.PENDING,
                ImportJobStatus.SCANNING,
                ImportJobStatus.VALIDATING,
                ImportJobStatus.IMPORTING
        );

        List<ImportJob> activeJobs = importJobRepository.findActiveJobs(activeStatuses);
        boolean hasActiveJob = activeJobs.stream()
                .anyMatch(j -> j.getProjectId().equals(request.projectId()));

        if (hasActiveJob) {
            throw new IllegalArgumentException("An import is already in progress for this project");
        }

        Path normalizedPath = Paths.get(validation.normalizedPath());
        ImportScanSummary scanSummary;
        try {
            scanSummary = scanImportSource(normalizedPath, request.projectId());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to scan import source: " + e.getMessage(), e);
        }

        long reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                workspaceId,
                scanSummary.totalSizeBytes(),
                "server-import-job"
        );

        try {
            ImportJob job = new ImportJob(
                    request.projectId(),
                    workspaceId,
                    userId,
                    validation.normalizedPath()
            );

            job.setOverwriteExisting(request.overwriteExisting());
            if (request.copyMode() != null) {
                job.setCopyMode(request.copyMode());
            }
            job.setTotalFiles(scanSummary.files().size());
            job.setTotalBytes(scanSummary.totalSizeBytes());
            job.setReservedBytes(reservedBytes);
            job.setQuotaReservationReleased(false);

            job = importJobRepository.save(job);

            log.info("Created import job {} for project {} from path: {}",
                    job.getId(), request.projectId(), validation.normalizedPath());

            asyncImportProcessor.processImportJob(job.getId());

            return convertToJobResponse(job);
        } catch (RuntimeException e) {
            workspaceQuotaGuardService.releaseReservation(workspaceId, reservedBytes);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<ImportJobDto.JobSummaryResponse> getAllJobs() {
        return importJobRepository.findAllOrderByCreatedDesc().stream()
                .map(this::convertToJobSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ImportJobDto.JobResponse getJob(String jobId) {
        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found: " + jobId));
        return convertToJobResponse(job);
    }

    public void cancelJob(String jobId) {
        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found: " + jobId));

        if (job.getStatus() == ImportJobStatus.COMPLETED ||
            job.getStatus() == ImportJobStatus.FAILED ||
            job.getStatus() == ImportJobStatus.CANCELLED) {
            throw new IllegalArgumentException("Job cannot be cancelled in current state: " + job.getStatus());
        }

        job.setStatus(ImportJobStatus.CANCELLED);
        settleCancelledJobReservation(job);
        importJobRepository.save(job);

        log.info("Cancelled import job: {}", jobId);
    }

    private boolean isImageFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".png") || fileName.endsWith(".jpg") ||
               fileName.endsWith(".jpeg") || fileName.endsWith(".gif") ||
               fileName.endsWith(".bmp") || fileName.endsWith(".tiff") ||
               fileName.endsWith(".webp");
    }

    private boolean isXmlFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".xml");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private ImportJobDto.JobResponse convertToJobResponse(ImportJob job) {
        return new ImportJobDto.JobResponse(
                job.getId(),
                job.getProjectId(),
                job.getWorkspaceId(),
                job.getCreatedByUserId(),
                job.getSourcePath(),
                job.getStatus(),
                job.getTotalFiles(),
                job.getProcessedFiles(),
                job.getSkippedFiles(),
                job.getFailedFiles(),
                job.getTotalBytes(),
                job.getProgressPercent(),
                job.getCopyMode(),
                job.isOverwriteExisting(),
                job.getErrorMessage(),
                job.getCreated(),
                job.getUpdated(),
                job.getCompletedAt()
        );
    }

    private ImportJobDto.JobSummaryResponse convertToJobSummaryResponse(ImportJob job) {
        return new ImportJobDto.JobSummaryResponse(
                job.getId(),
                job.getProjectId(),
                job.getSourcePath(),
                job.getStatus(),
                job.getTotalFiles(),
                job.getProcessedFiles(),
                job.getFailedFiles(),
                job.getProgressPercent(),
                job.getCreated(),
                job.getCompletedAt()
        );
    }

    private ImportScanSummary scanImportSource(Path path, String projectId) throws IOException {
        List<ImportJobDto.ScanFileInfo> files = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        long totalSizeBytes = 0;
        int imageCount = 0;
        int xmlCount = 0;

        try (Stream<Path> stream = Files.walk(path, properties.getMaxScanDepth())) {
            List<Path> filePaths = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> isImageFile(p) || isXmlFile(p))
                    .limit(properties.getMaxFilesPerJob() + 1L)
                    .toList();

            if (filePaths.size() > properties.getMaxFilesPerJob()) {
                warnings.add("Directory contains more than " + properties.getMaxFilesPerJob() + " files. Only the first " + properties.getMaxFilesPerJob() + " will be imported.");
                filePaths = filePaths.subList(0, properties.getMaxFilesPerJob());
            }

            Set<String> existingPageNames = new HashSet<>();
            if (projectId != null) {
                pageRepository.findByProjectId(projectId).forEach(p -> existingPageNames.add(p.getName()));
            }

            for (Path filePath : filePaths) {
                String fileName = filePath.getFileName().toString();
                String relativePath = path.relativize(filePath).toString();
                long fileSize = Files.size(filePath);
                String fileType = isImageFile(filePath) ? "image" : "xml";

                ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(fileName);

                boolean hasConflict = existingPageNames.contains(nameInfo.baseName());
                String conflictType = hasConflict ? "PAGE_EXISTS" : null;

                if (hasConflict) {
                    conflicts.add("Page already exists: " + nameInfo.baseName());
                }

                files.add(new ImportJobDto.ScanFileInfo(
                        fileName,
                        relativePath,
                        fileSize,
                        fileType,
                        nameInfo.baseName(),
                        nameInfo.variant(),
                        hasConflict,
                        conflictType
                ));

                totalSizeBytes += fileSize;
                if ("image".equals(fileType)) {
                    imageCount++;
                } else {
                    xmlCount++;
                }
            }
        }

        return new ImportScanSummary(files, conflicts, warnings, totalSizeBytes, imageCount, xmlCount);
    }

    private void settleCancelledJobReservation(ImportJob job) {
        if (job == null || job.isQuotaReservationReleased() || job.getReservedBytes() <= 0) {
            return;
        }

        workspaceQuotaGuardService.syncUsageAndReleaseReservation(job.getWorkspaceId(), job.getReservedBytes());
        job.setQuotaReservationReleased(true);
        job.setReservedBytes(0L);
    }

    private record ImportScanSummary(
            List<ImportJobDto.ScanFileInfo> files,
            List<String> conflicts,
            List<String> warnings,
            long totalSizeBytes,
            int imageCount,
            int xmlCount
    ) {}
}
