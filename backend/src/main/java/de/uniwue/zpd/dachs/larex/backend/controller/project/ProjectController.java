package de.uniwue.zpd.dachs.larex.backend.controller.project;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.IiifImportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectTransferDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UploadConflictDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectTransferRequest;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.exception.StorageQuotaExceededException;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectTransferService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectReadService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.export.DocumentExportService;
import de.uniwue.zpd.dachs.larex.backend.service.importer.IiifImportService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UnifiedUploadService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadConflictService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/workspaces/{workspaceId}/projects")
public class ProjectController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;
    private final ProjectTransferService projectTransferService;
    private final ProjectReadService projectReadService;
    private final ProjectPackageService projectPackageService;
    private final DocumentExportService documentExportService;
    private final IiifImportService iiifImportService;
    private final UnifiedUploadService unifiedUploadService;
    private final UploadConflictService uploadConflictService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;

    public ProjectController(ProjectService projectService, ProjectTransferService projectTransferService,
                           ProjectReadService projectReadService,
                           ProjectPackageService projectPackageService,
                           DocumentExportService documentExportService,
                           IiifImportService iiifImportService,
                           UnifiedUploadService unifiedUploadService,
                           UploadConflictService uploadConflictService,
                           WorkspaceQuotaGuardService workspaceQuotaGuardService) {
        this.projectService = projectService;
        this.projectTransferService = projectTransferService;
        this.projectReadService = projectReadService;
        this.projectPackageService = projectPackageService;
        this.documentExportService = documentExportService;
        this.iiifImportService = iiifImportService;
        this.unifiedUploadService = unifiedUploadService;
        this.uploadConflictService = uploadConflictService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
    }

    @GetMapping
    public ResponseEntity<List<ProjectDto.Response>> getWorkspaceProjects(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> tags,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<Project> projects;

        if (search != null && !search.trim().isEmpty()) {
            projects = projectService.searchProjects(workspaceId, search, userId);
        } else if (tags != null && !tags.isEmpty()) {
            projects = projectService.getProjectsByTags(workspaceId, tags, userId);
        } else {
            projects = projectService.getWorkspaceProjects(workspaceId, userId);
        }

        List<ProjectDto.Response> response = projectReadService.toResponses(projects, userId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectDto.Response> getProject(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<Project> projectOpt = projectService.getProjectById(projectId, userId);

        return projectOpt.map(project -> projectReadService.toResponse(project, userId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProjectDto.Response> createProject(
            @PathVariable String workspaceId,
            @Valid @RequestBody ProjectDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        Optional<Project> projectOpt = projectService.createProject(
                workspaceId,
                request.name(),
                request.description(),
                request.tags(),
                request.codecId(),
                request.labelSetId(),
                request.dictionaryId(),
                request.tagSetId(),
                request.normalizationProfileId(),
                request.validationRulesetId(),
                request.defaultGtIndex(),
                request.defaultRecognitionIndices(),
                userId
        );

        return projectOpt.map(project -> projectReadService.toResponse(project, userId))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .orElseThrow(() -> new SecurityException("Cannot create project in this workspace. You may not have access to the workspace"));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectDto.Response> updateProject(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @Valid @RequestBody ProjectDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<Project> projectOpt = projectService.updateProject(
                projectId,
                request.name(),
                request.description(),
                request.tags(),
                request.codecId(),
                request.labelSetId(),
                request.dictionaryId(),
                request.tagSetId(),
                request.normalizationProfileId(),
                request.validationRulesetId(),
                request.defaultGtIndex(),
                request.defaultRecognitionIndices(),
                userId
        );

        return projectOpt.map(project -> projectReadService.toResponse(project, userId))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean deleted = projectService.deleteProject(projectId, userId);

        if (deleted) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/{projectId}/bulk-upload")
    public ResponseEntity<Map<String, Object>> bulkUploadImages(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        long reservedBytes = 0L;
        try {
            reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    workspaceId,
                    workspaceQuotaGuardService.totalMultipartBytes(files),
                    "bulk-upload"
            );
            Map<String, Object> result = projectService.bulkUploadImages(projectId, files, userId);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
        }
    }

    @PostMapping("/{projectId}/dataset-import")
    public ResponseEntity<Map<String, Object>> importDataset(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        logger.debug("Dataset import endpoint called - workspaceId={}, projectId={}, userId={}, fileCount={}", workspaceId, projectId, userId, files.size());

        long reservedBytes = 0L;
        try {
            reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    workspaceId,
                    workspaceQuotaGuardService.totalMultipartBytes(files),
                    "dataset-import"
            );
            logger.debug("Storage quota check passed, calling service");
            Map<String, Object> result = projectService.importDataset(projectId, files, userId);

            logger.debug("Dataset import completed successfully for project {}", projectId);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            logger.error("IOException in dataset import for project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File processing error: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("IllegalArgumentException in dataset import: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (StorageQuotaExceededException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected exception in dataset import for project {}: {} - {}", projectId, e.getClass().getSimpleName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected error: " + e.getMessage()));
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
        }
    }

    @PostMapping("/{projectId}/unified-upload")
    public ResponseEntity<UploadConflictDto.UploadResponse> unifiedUpload(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        long reservedBytes = 0L;
        try {
            reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    workspaceId,
                    workspaceQuotaGuardService.totalMultipartBytes(files),
                    "unified-upload"
            );
            UploadConflictDto.UploadResponse result = unifiedUploadService.processUpload(projectId, files, userId);
            return ResponseEntity.ok(result);
        } catch (StorageQuotaExceededException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
        }
    }

    @GetMapping("/{projectId}/conflicts")
    public ResponseEntity<List<UploadConflictDto.ConflictResponse>> getUploadConflicts(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<UploadConflictDto.ConflictResponse> conflicts = uploadConflictService.getProjectConflicts(projectId, userId);
        return ResponseEntity.ok(conflicts);
    }

    @PostMapping("/{projectId}/conflicts/resolve")
    public ResponseEntity<Void> resolveUploadConflicts(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @Valid @RequestBody UploadConflictDto.BatchConflictResolutionRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        try {
            uploadConflictService.resolveConflicts(projectId, userId, request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{projectId}/status")
    public ResponseEntity<Map<String, Object>> getProjectStatus(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean hasConflicts = uploadConflictService.hasUnresolvedConflicts(projectId);

        Map<String, Object> status = new HashMap<>();
        status.put("hasUnresolvedConflicts", hasConflicts);
        status.put("isBlocked", hasConflicts);

        return ResponseEntity.ok(status);
    }

    @PostMapping("/{projectId}/export-package")
    public ResponseEntity<byte[]> exportProjectPackage(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestBody(required = false) ProjectPackageDto.ExportRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        byte[] packageBytes = projectPackageService.exportProjectPackage(workspaceId, projectId, userId, request);
        String projectName = projectService.getProjectById(projectId, userId)
                .map(Project::getName)
                .orElse(projectId);
        String filename = sanitizeFileName(projectName, "project") + ".zip";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(packageBytes);
    }

    @PostMapping("/{projectId}/export")
    public ResponseEntity<byte[]> exportProjectOutput(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestBody DocumentExportDto.ProjectExportRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        DocumentExportService.DocumentExportResult exportResult =
                documentExportService.exportProject(workspaceId, projectId, userId, request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(exportResult.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(exportResult.fileName())
                .build());
        headers.setContentLength(exportResult.bytes().length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(exportResult.bytes());
    }

    private String sanitizeFileName(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String sanitized = value.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() ? fallback : sanitized;
    }

    @PostMapping(value = "/import-package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectPackageDto.ImportResult> importProjectPackage(
            @PathVariable String workspaceId,
            @RequestParam("file") MultipartFile packageFile,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        ProjectPackageDto.ImportResult result = projectPackageService.importProjectPackage(workspaceId, userId, packageFile);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{projectId}/iiif-import/preview")
    public ResponseEntity<IiifImportDto.PreviewResponse> previewIiifImportFromUrl(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @Valid @RequestBody IiifImportDto.PreviewRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        return ResponseEntity.ok(iiifImportService.previewFromManifestUrl(workspaceId, projectId, userId, request));
    }

    @PostMapping(value = "/{projectId}/iiif-import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IiifImportDto.PreviewResponse> previewIiifImportFromFile(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        return ResponseEntity.ok(iiifImportService.previewFromManifestFile(workspaceId, projectId, userId, file));
    }

    @PostMapping("/{projectId}/iiif-import/preview-jobs")
    public ResponseEntity<IiifImportDto.PreviewJobResponse> createIiifPreviewJobFromUrl(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @Valid @RequestBody IiifImportDto.PreviewRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        IiifImportDto.PreviewJobResponse response = iiifImportService.startPreviewJobFromManifestUrl(workspaceId, projectId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/{projectId}/iiif-import/preview-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IiifImportDto.PreviewJobResponse> createIiifPreviewJobFromFile(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        IiifImportDto.PreviewJobResponse response = iiifImportService.startPreviewJobFromManifestFile(workspaceId, projectId, userId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{projectId}/iiif-import/preview-jobs/{previewJobId}")
    public ResponseEntity<IiifImportDto.PreviewJobResponse> getIiifPreviewJob(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String previewJobId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        return ResponseEntity.ok(iiifImportService.getPreviewJob(workspaceId, projectId, userId, previewJobId));
    }

    @PostMapping("/{projectId}/iiif-import/jobs")
    public ResponseEntity<IiifImportDto.JobResponse> createIiifImportJob(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @Valid @RequestBody IiifImportDto.StartJobRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        IiifImportDto.JobResponse response = iiifImportService.startImportJob(workspaceId, projectId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{projectId}/iiif-import/jobs/{jobId}")
    public ResponseEntity<IiifImportDto.JobResponse> getIiifImportJob(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        return ResponseEntity.ok(iiifImportService.getImportJob(workspaceId, projectId, userId, jobId));
    }

    @DeleteMapping("/{projectId}/iiif-import/jobs/{jobId}")
    public ResponseEntity<IiifImportDto.JobResponse> cancelIiifImportJob(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        return ResponseEntity.ok(iiifImportService.cancelImportJob(workspaceId, projectId, userId, jobId));
    }

    @PostMapping("/{projectId}/iiif-import/jobs/{jobId}/retry-failed")
    public ResponseEntity<IiifImportDto.JobResponse> retryFailedIiifImportJob(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String jobId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        IiifImportDto.JobResponse response = iiifImportService.retryFailedImportJob(workspaceId, projectId, userId, jobId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{projectId}/transfer")
    public ResponseEntity<ProjectTransferDto.Response> requestProjectTransfer(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @Valid @RequestBody ProjectTransferDto.CreateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        // Validate that the provided projectId matches the request
        if (!projectId.equals(request.projectId())) {
            return ResponseEntity.badRequest().build();
        }

        Optional<ProjectTransferRequest> transferOpt = projectTransferService.requestProjectTransfer(
                request.projectId(),
                request.targetWorkspaceId(),
                userId,
                request.message(),
                request.transferType() != null ? request.transferType() : ProjectTransferRequest.TransferType.MOVE
        );

        return transferOpt.map(projectTransferService::toResponse)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .orElse(ResponseEntity.badRequest().build());
    }
}
