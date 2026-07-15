package de.uniwue.zpd.dachs.larex.backend.controller.project;

import de.uniwue.zpd.dachs.larex.backend.dto.BulkDeleteDto;
import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.IiifImportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectBatchExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectTransferDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UploadConflictDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectTransferRequest;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.service.project.LegacyOcr4allImportService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectTransferService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectReadService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectBatchExportService;
import de.uniwue.zpd.dachs.larex.backend.service.export.DocumentExportService;
import de.uniwue.zpd.dachs.larex.backend.service.importer.IiifImportService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadConflictService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/workspaces/{workspaceId}/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectTransferService projectTransferService;
    private final ProjectReadService projectReadService;
    private final ProjectPackageService projectPackageService;
    private final ProjectBatchExportService projectBatchExportService;
    private final LegacyOcr4allImportService legacyOcr4allImportService;
    private final DocumentExportService documentExportService;
    private final IiifImportService iiifImportService;
    private final UploadConflictService uploadConflictService;

    public ProjectController(ProjectService projectService, ProjectTransferService projectTransferService,
                           ProjectReadService projectReadService,
                           ProjectPackageService projectPackageService,
                           ProjectBatchExportService projectBatchExportService,
                           LegacyOcr4allImportService legacyOcr4allImportService,
                           DocumentExportService documentExportService,
                           IiifImportService iiifImportService,
                           UploadConflictService uploadConflictService) {
        this.projectService = projectService;
        this.projectTransferService = projectTransferService;
        this.projectReadService = projectReadService;
        this.projectPackageService = projectPackageService;
        this.projectBatchExportService = projectBatchExportService;
        this.legacyOcr4allImportService = legacyOcr4allImportService;
        this.documentExportService = documentExportService;
        this.iiifImportService = iiifImportService;
        this.uploadConflictService = uploadConflictService;
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
                request.virtualKeyboardId(),
                request.allowCodecOverride(),
                request.allowDictionaryOverride(),
                request.allowVirtualKeyboardOverride(),
                request.allowLabelSetOverride(),
                request.allowTagSetOverride(),
                request.allowNormalizationProfileOverride(),
                request.allowValidationRulesetOverride(),
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
                request.virtualKeyboardId(),
                request.allowCodecOverride(),
                request.allowDictionaryOverride(),
                request.allowVirtualKeyboardOverride(),
                request.allowLabelSetOverride(),
                request.allowTagSetOverride(),
                request.allowNormalizationProfileOverride(),
                request.allowValidationRulesetOverride(),
                request.defaultGtIndex(),
                request.defaultRecognitionIndices(),
                userId
        );

        return projectOpt.map(project -> projectReadService.toResponse(project, userId))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    @PatchMapping("/{projectId}/toolkit-presets")
    public ResponseEntity<ProjectDto.Response> updateToolkitPresets(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @Valid @RequestBody ProjectDto.ToolkitPresetsRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<Project> projectOpt = projectService.updateToolkitPresets(
                workspaceId,
                projectId,
                request.codecId(),
                request.labelSetId(),
                request.dictionaryId(),
                request.tagSetId(),
                request.normalizationProfileId(),
                request.validationRulesetId(),
                request.virtualKeyboardId(),
                request.allowCodecOverride(),
                request.allowDictionaryOverride(),
                request.allowVirtualKeyboardOverride(),
                request.allowLabelSetOverride(),
                request.allowTagSetOverride(),
                request.allowNormalizationProfileOverride(),
                request.allowValidationRulesetOverride(),
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

    @DeleteMapping("/bulk")
    public ResponseEntity<BulkDeleteDto.BulkDeleteResponse> bulkDeleteProjects(
            @PathVariable String workspaceId,
            @Valid @RequestBody BulkDeleteDto.BulkDeleteRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(projectService.bulkDeleteProjects(workspaceId, request.ids(), userId));
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
    public ResponseEntity<StreamingResponseBody> exportProjectPackage(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestBody(required = false) ProjectPackageDto.ExportRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        String projectName = projectService.getProjectById(projectId, userId)
                .map(Project::getName)
                .orElse(projectId);
        String filename = sanitizeFileName(projectName, "project") + ".larex-project.zip";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename)
                .build());

        StreamingResponseBody body = outputStream ->
                projectPackageService.writeProjectPackage(workspaceId, projectId, userId, request, outputStream);

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    @PostMapping("/{projectId}/export-basic")
    public ResponseEntity<StreamingResponseBody> exportBasicProject(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestBody(required = false) ProjectPackageDto.ExportRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        String projectName = projectService.getProjectById(projectId, userId)
                .map(Project::getName)
                .orElse(projectId);
        String filename = sanitizeFileName(projectName, "project") + ".zip";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename)
                .build());

        StreamingResponseBody body = outputStream ->
                projectPackageService.writeBasicProjectExport(workspaceId, projectId, userId, request, outputStream);

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    @PostMapping("/batch-export")
    public ResponseEntity<StreamingResponseBody> exportProjects(
            @PathVariable String workspaceId,
            @Valid @RequestBody ProjectBatchExportDto.ExportRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        ProjectBatchExportService.PreparedBatchExport batch =
                projectBatchExportService.prepareBatchExport(workspaceId, userId, request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("larex-projects-export.zip")
                .build());

        StreamingResponseBody body = outputStream ->
                projectBatchExportService.writeBatchExport(batch, outputStream);

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    @GetMapping("/{projectId}/releases")
    public ResponseEntity<List<ProjectPackageDto.ReleaseSummaryResponse>> listReleases(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(projectPackageService.listReleases(workspaceId, projectId, userId));
    }

    @PostMapping("/{projectId}/releases")
    public ResponseEntity<ProjectPackageDto.ReleaseSummaryResponse> createRelease(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @Valid @RequestBody(required = false) ProjectPackageDto.CreateReleaseRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectPackageService.createRelease(workspaceId, projectId, request, userId));
    }

    @PostMapping("/{projectId}/releases/{releaseId}/share")
    public ResponseEntity<ProjectPackageDto.ReleaseShareResponse> createOrRotateReleaseShare(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String releaseId,
            @Valid @RequestBody ProjectPackageDto.UpsertReleaseShareRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(projectPackageService.createOrRotateReleaseShare(workspaceId, projectId, releaseId, request, userId));
    }

    @PatchMapping("/{projectId}/releases/{releaseId}/share")
    public ResponseEntity<ProjectPackageDto.ReleaseSummaryResponse> updateReleaseShare(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String releaseId,
            @Valid @RequestBody ProjectPackageDto.UpdateReleaseShareRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(projectPackageService.updateReleaseShare(workspaceId, projectId, releaseId, request, userId));
    }

    @DeleteMapping("/{projectId}/releases/{releaseId}/share")
    public ResponseEntity<ProjectPackageDto.ReleaseSummaryResponse> revokeReleaseShare(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String releaseId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(projectPackageService.revokeReleaseShare(workspaceId, projectId, releaseId, userId));
    }

    @GetMapping("/{projectId}/releases/{releaseId}/download")
    public ResponseEntity<Resource> downloadRelease(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String releaseId,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        ProjectPackageService.ReleaseFileDownload releaseDownload = projectPackageService.downloadReleasePackage(workspaceId, projectId, releaseId, userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(releaseDownload.contentLength());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(releaseDownload.fileName())
                .build());
        headers.set("X-Checksum-Sha256", releaseDownload.checksumSha256());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(releaseDownload.absolutePath()));
    }

    @PostMapping("/{projectId}/export")
    public ResponseEntity<StreamingResponseBody> exportProjectOutput(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestBody DocumentExportDto.ProjectExportRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        DocumentExportService.StreamingDocumentExportResult exportResult =
                documentExportService.exportProjectStream(workspaceId, projectId, userId, request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(exportResult.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(exportResult.fileName())
                .build());
        StreamingResponseBody body = exportResult.writer()::write;

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
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

    @PostMapping(value = "/import-legacy-ocr4all", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectPackageDto.ImportResult> importLegacyOcr4allProject(
            @PathVariable String workspaceId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("paths") List<String> paths,
            @RequestParam(value = "projectName", required = false) String projectName,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {

        ProjectPackageDto.ImportResult result = legacyOcr4allImportService.importProject(
                workspaceId,
                userId,
                files,
                paths,
                projectName
        );
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
