package de.uniwue.zpd.dachs.larex.backend.service.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorAssignment;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ExecuteRole;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.LockMode;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Status;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorAssignmentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.ThumbnailService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlValidationService;
import de.uniwue.zpd.dachs.larex.backend.util.ImageFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ActionRunService {

    private static final Logger log = LoggerFactory.getLogger(ActionRunService.class);
    private static final int ACTION_PROTOCOL_VERSION = 1;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ActionProcessorDefinitionRepository definitionRepository;
    private final ActionProcessorAssignmentRepository assignmentRepository;
    private final ActionRunRepository runRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ActionDefinitionService definitionService;
    private final ActionEndpointAuthService endpointAuthService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor importTaskExecutor;
    private final HierarchicalFileStorageService fileStorageService;
    private final ThumbnailService thumbnailService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final WorkspaceQuotaRefreshService workspaceQuotaRefreshService;
    private final PageXmlValidationService pageXmlValidationService;
    private final PageXmlCanonicalizationService pageXmlCanonicalizationService;
    private final PageXmlVersionService pageXmlVersionService;
    private final PageFilterIndexService pageFilterIndexService;
    private final AnnotationReadCache annotationReadCache;
    private final HttpClient httpClient;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public ActionRunService(ActionProcessorDefinitionRepository definitionRepository,
                            ActionProcessorAssignmentRepository assignmentRepository,
                            ActionRunRepository runRepository,
                            ProjectRepository projectRepository,
                            PageRepository pageRepository,
                            PageImageRepository pageImageRepository,
                            PageXmlRepository pageXmlRepository,
                            WorkspaceAccessService workspaceAccessService,
                            ActionDefinitionService definitionService,
                            ActionEndpointAuthService endpointAuthService,
                            ObjectMapper objectMapper,
                            @org.springframework.beans.factory.annotation.Qualifier("importTaskExecutor") TaskExecutor importTaskExecutor,
                            HierarchicalFileStorageService fileStorageService,
                            ThumbnailService thumbnailService,
                            WorkspaceQuotaGuardService workspaceQuotaGuardService,
                            WorkspaceQuotaRefreshService workspaceQuotaRefreshService,
                            PageXmlValidationService pageXmlValidationService,
                            PageXmlCanonicalizationService pageXmlCanonicalizationService,
                            PageXmlVersionService pageXmlVersionService,
                            PageFilterIndexService pageFilterIndexService,
                            AnnotationReadCache annotationReadCache) {
        this.definitionRepository = definitionRepository;
        this.assignmentRepository = assignmentRepository;
        this.runRepository = runRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.definitionService = definitionService;
        this.endpointAuthService = endpointAuthService;
        this.objectMapper = objectMapper;
        this.importTaskExecutor = importTaskExecutor;
        this.fileStorageService = fileStorageService;
        this.thumbnailService = thumbnailService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
        this.pageXmlValidationService = pageXmlValidationService;
        this.pageXmlCanonicalizationService = pageXmlCanonicalizationService;
        this.pageXmlVersionService = pageXmlVersionService;
        this.pageFilterIndexService = pageFilterIndexService;
        this.annotationReadCache = annotationReadCache;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ActionDto.DefinitionResponse> listAvailableDefinitions(String workspaceId, String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        return definitionRepository.findByEnabledTrueOrderByNameAsc().stream()
                .map(definitionService::toDefinitionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActionDto.AssignmentResponse> listAssignments(String workspaceId, String projectId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        List<ActionProcessorAssignment> assignments = projectId == null || projectId.isBlank()
                ? assignmentRepository.findByWorkspaceIdAndProjectIdIsNullOrderByCreatedAsc(workspaceId)
                : assignmentRepository.findByWorkspaceIdAndProjectIdOrderByCreatedAsc(workspaceId, projectId);
        return assignments.stream().map(this::toAssignmentResponse).toList();
    }

    public ActionDto.AssignmentResponse assignProcessor(String workspaceId, ActionDto.AssignmentRequest request, String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        String projectId = normalizeOptional(request.projectId());
        if (projectId != null) {
            requireProject(workspaceId, projectId);
        }

        ActionProcessorDefinition definition = definitionRepository.findById(request.processorDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found"));
        ActionProcessorAssignment assignment = projectId == null
                ? assignmentRepository.findWorkspaceAssignment(definition.getId(), workspaceId).orElseGet(ActionProcessorAssignment::new)
                : assignmentRepository.findByProcessorDefinitionIdAndWorkspaceIdAndProjectId(definition.getId(), workspaceId, projectId)
                .orElseGet(ActionProcessorAssignment::new);
        assignment.setProcessorDefinition(definition);
        assignment.setWorkspaceId(workspaceId);
        assignment.setProjectId(projectId);
        assignment.setEnabled(request.enabled() == null || request.enabled());
        if (assignment.getCreatedByUserId() == null) {
            assignment.setCreatedByUserId(userId);
        }
        return toAssignmentResponse(assignmentRepository.save(assignment));
    }

    public void unassignProcessor(String workspaceId, String assignmentId, String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        ActionProcessorAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Action processor assignment not found"));
        if (!workspaceId.equals(assignment.getWorkspaceId())) {
            throw new IllegalArgumentException("Action processor assignment not found");
        }
        assignmentRepository.delete(assignment);
    }

    @Transactional(readOnly = true)
    public List<ActionDto.ExecutableProcessorResponse> listExecutableProcessors(String workspaceId, String projectId, String userId) {
        Project project = requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        return assignmentRepository.findExecutableAssignments(workspaceId, projectId).stream()
                .collect(Collectors.toMap(
                        assignment -> assignment.getProcessorDefinition().getId(),
                        assignment -> assignment,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(assignment -> toExecutableResponse(assignment, project, userId))
                .toList();
    }

    public ActionDto.StartRunResponse startRun(String workspaceId,
                                               String projectId,
                                               ActionDto.StartRunRequest request,
                                               String userId,
                                               String publicApiBaseUrl) {
        Project project = requireProject(workspaceId, projectId);
        ActionProcessorDefinition definition = definitionRepository.findById(request.processorDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found"));
        if (!definition.isEnabled()) {
            throw new IllegalArgumentException("Action processor is disabled");
        }
        requireAssigned(workspaceId, projectId, definition.getId());
        requireExecuteAccess(definition, workspaceId, userId);

        List<Page> pages = resolveRunPages(projectId, request.pageIds());
        validateLocks(project, pages);

        String rawSecret = "lrx_act_" + generateOpaqueToken(32);
        ActionRun run = new ActionRun();
        run.setProcessorDefinition(definition);
        run.setWorkspaceId(workspaceId);
        run.setProjectId(projectId);
        run.setCreatedByUserId(userId);
        run.setStatus(Status.PENDING);
        run.setLockMode(definition.getLockMode());
        run.setPageIdsJson(writeJson(pages.stream().map(Page::getId).toList()));
        run.setParametersJson(writeJson(request.parameters() == null ? Map.of() : request.parameters()));
        run.setSecretHash(sha256(rawSecret));
        run.setSecretPrefix(rawSecret.substring(0, Math.min(rawSecret.length(), 12)));
        run.setSecretExpiresAt(LocalDateTime.now().plusMinutes(definitionService.defaultTokenTtlMinutes()));
        run.setStatusMessage("Created");
        run = runRepository.save(run);

        applyLocks(project, pages, run);
        ActionRun savedRun = runRepository.save(run);
        dispatchAfterCommit(savedRun.getId(), rawSecret, publicApiBaseUrl);
        return new ActionDto.StartRunResponse(toRunResponse(savedRun));
    }

    @Transactional(readOnly = true)
    public List<ActionDto.RunResponse> listRuns(String workspaceId, String projectId, String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        return runRepository.findByWorkspaceIdAndProjectIdOrderByCreatedDesc(workspaceId, projectId)
                .stream()
                .map(this::toRunResponse)
                .toList();
    }

    public ActionDto.RunResponse cancelRun(String workspaceId, String projectId, String runId, String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        ActionRun run = requireRun(workspaceId, projectId, runId);
        run.setCancelRequested(true);
        if (run.getStatus() == Status.PENDING
                || run.getStatus() == Status.DISPATCHING
                || run.getStatus() == Status.RUNNING
                || run.getStatus() == Status.IMPORTING_RESULTS
                || run.getStatus() == Status.CANCEL_REQUESTED) {
            run.setStatus(Status.CANCELLED);
            run.setStatusMessage("Cancelled");
            run.setCompletedAt(LocalDateTime.now());
        }
        releaseLocks(run);
        return toRunResponse(runRepository.save(run));
    }

    @Transactional(readOnly = true)
    public ActionDto.MachineInputResponse buildMachineInput(String runId, String authorizationHeader, String publicApiBaseUrl) {
        ActionRun run = authenticateRun(runId, authorizationHeader);
        ActionProcessorDefinition definition = run.getProcessorDefinition();
        List<String> pageIds = readPageIds(run);
        Map<String, Object> parameters = readObjectMap(run.getParametersJson());

        Map<String, List<PageImage>> imagesByPage = definition.isAcceptsImages()
                ? pageImageRepository.findByPageIdIn(pageIds).stream().collect(Collectors.groupingBy(image -> image.getPage().getId()))
                : Map.of();
        Map<String, List<PageXml>> xmlByPage = definition.isAcceptsXml()
                ? pageXmlRepository.findByPage_IdIn(pageIds).stream().collect(Collectors.groupingBy(xml -> xml.getPage().getId()))
                : Map.of();

        List<ActionDto.MachinePageInput> pages = pageRepository.findByIdInAndProjectId(pageIds, run.getProjectId()).stream()
                .sorted(Comparator.comparing(Page::getName))
                .map(page -> new ActionDto.MachinePageInput(
                        page.getId(),
                        page.getName(),
                        imagesByPage.getOrDefault(page.getId(), List.of()).stream()
                                .map(image -> toMachineImageFile(publicApiBaseUrl, runId, image))
                                .toList(),
                        xmlByPage.getOrDefault(page.getId(), List.of()).stream()
                                .map(xml -> toMachineXmlFile(publicApiBaseUrl, runId, xml))
                                .toList()
                ))
                .toList();

        return new ActionDto.MachineInputResponse(
                ACTION_PROTOCOL_VERSION,
                run.getId(),
                definition.getProcessorKey(),
                run.getProjectId(),
                parameters,
                pages,
                run.isCancelRequested()
        );
    }

    @Transactional(readOnly = true)
    public MachineFile resolveMachineFile(String runId, String authorizationHeader, String type, String fileId) {
        ActionRun run = authenticateRun(runId, authorizationHeader);
        List<String> pageIds = readPageIds(run);
        if ("images".equals(type)) {
            if (!run.getProcessorDefinition().isAcceptsImages()) {
                throw new SecurityException("Image inputs are not allowed for this run");
            }
            PageImage image = pageImageRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("Image not found"));
            if (image.getPage() == null || !pageIds.contains(image.getPage().getId())) {
                throw new SecurityException("Image is outside this run scope");
            }
            return new MachineFile(image.getFilePath(), image.getFileName(), image.getMimeType());
        }
        if ("xml".equals(type)) {
            if (!run.getProcessorDefinition().isAcceptsXml()) {
                throw new SecurityException("XML inputs are not allowed for this run");
            }
            PageXml xml = pageXmlRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("XML not found"));
            if (xml.getPage() == null || !pageIds.contains(xml.getPage().getId())) {
                throw new SecurityException("XML is outside this run scope");
            }
            return new MachineFile(xml.getFilePath(), xml.getFileName(), xml.getMimeType());
        }
        throw new IllegalArgumentException("Unsupported file type");
    }

    public ActionDto.HeartbeatResponse heartbeat(String runId, String authorizationHeader, ActionDto.HeartbeatRequest request) {
        ActionRun run = authenticateRun(runId, authorizationHeader);
        if (run.getStatus() == Status.COMPLETED || run.getStatus() == Status.FAILED || run.getStatus() == Status.CANCELLED) {
            return new ActionDto.HeartbeatResponse(run.isCancelRequested());
        }
        if (run.getStatus() != Status.CANCEL_REQUESTED
                && run.getStatus() != Status.CANCELLED
                && run.getStatus() != Status.IMPORTING_RESULTS) {
            run.setStatus(Status.RUNNING);
        }
        run.setLastHeartbeatAt(LocalDateTime.now());
        if (request.progressPercent() != null) {
            run.setProgressPercent(Math.max(0, Math.min(100, request.progressPercent())));
        }
        if (request.statusMessage() != null) {
            run.setStatusMessage(limit(request.statusMessage(), 2000));
        }
        appendLog(run, request.log());
        if ("failed".equalsIgnoreCase(request.status())) {
            run.setStatus(Status.FAILED);
            run.setErrorMessage(limit(request.errorMessage(), 4000));
            run.setCompletedAt(LocalDateTime.now());
            releaseLocks(run);
        }
        runRepository.save(run);
        return new ActionDto.HeartbeatResponse(run.isCancelRequested());
    }

    public ActionDto.RunResponse receiveResults(String runId,
                                                String authorizationHeader,
                                                ActionDto.ResultManifest manifest,
                                                MultiValueMap<String, MultipartFile> files) throws IOException {
        ActionRun run = authenticateRunForUpdate(runId, authorizationHeader);
        if (run.getStatus() == Status.COMPLETED || run.getStatus() == Status.FAILED) {
            return toRunResponse(run);
        }
        if (run.isCancelRequested() || run.getStatus() == Status.CANCELLED || run.getStatus() == Status.CANCEL_REQUESTED) {
            throw new SecurityException("This run has been cancelled");
        }
        if (manifest.protocolVersion() != null && manifest.protocolVersion() != ACTION_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported Action result protocol version");
        }

        List<String> pageIds = readPageIds(run);
        ActionProcessorDefinition definition = run.getProcessorDefinition();
        List<ActionDto.ResultFile> resultFiles = manifest.files() == null ? List.of() : manifest.files();
        run.setStatus(Status.IMPORTING_RESULTS);
        run.setStatusMessage("Importing results");
        runRepository.saveAndFlush(run);
        long reservedBytes = 0L;
        try {
            reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    run.getWorkspaceId(),
                    resultFiles.stream().mapToLong(file -> resolveMultipart(files, file.fieldName()).getSize()).sum(),
                    "larex-action-result"
            );
            List<Map<String, Object>> stored = new ArrayList<>();
            Set<String> xmlResultPageIds = new LinkedHashSet<>();
            for (ActionDto.ResultFile resultFile : resultFiles) {
                if (resultFile.pageId() == null || !pageIds.contains(resultFile.pageId())) {
                    throw new SecurityException("Result page is outside this run scope");
                }
                MultipartFile file = resolveMultipart(files, resultFile.fieldName());
                String type = normalize(resultFile.type());
                if ("xml".equals(type)) {
                    if (!definition.isOutputsXml()) {
                        throw new SecurityException("XML outputs are not declared for this processor");
                    }
                    stored.add(storeXmlResult(run, resultFile, file));
                    xmlResultPageIds.add(resultFile.pageId());
                } else if ("image".equals(type) || "images".equals(type)) {
                    if (!definition.isOutputsImages()) {
                        throw new SecurityException("Image outputs are not declared for this processor");
                    }
                    stored.add(storeImageResult(run, resultFile, file));
                } else {
                    throw new IllegalArgumentException("Unsupported result type: " + resultFile.type());
                }
            }
            run.setResultSummaryJson(writeJson(stored));
            run.setStatus("failed".equalsIgnoreCase(manifest.status()) ? Status.FAILED : Status.COMPLETED);
            run.setStatusMessage(limit(manifest.message(), 2000));
            run.setProgressPercent(run.getStatus() == Status.COMPLETED ? 100 : run.getProgressPercent());
            run.setCompletedAt(LocalDateTime.now());
            releaseLocks(run);
            ActionRun savedRun = runRepository.save(run);
            schedulePageReindexAfterCommit(xmlResultPageIds);
            return toRunResponse(savedRun);
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(run.getWorkspaceId(), reservedBytes);
        }
    }

    private Map<String, Object> storeXmlResult(ActionRun run, ActionDto.ResultFile resultFile, MultipartFile file) throws IOException {
        String xmlText = new String(file.getBytes(), StandardCharsets.UTF_8);
        var validation = pageXmlValidationService.validatePageXml(xmlText);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Result XML is invalid");
        }
        Page page = pageRepository.findByIdAndProjectId(resultFile.pageId(), run.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        String originalName = chooseFileName(resultFile.fileName(), file.getOriginalFilename(), page.getName() + ".xml");
        List<PageXml> existing = pageXmlRepository.findByPage_Id(page.getId()).stream()
                .filter(xml -> xml.getSchema() == XmlSchema.PAGE_XML)
                .sorted(Comparator.comparing(xml -> !"original".equalsIgnoreCase(xml.getVariant())))
                .toList();
        var storedFile = fileStorageService.storeMultipartFile(new RenamedMultipartFile(file, originalName, "application/xml"),
                run.getWorkspaceId(), run.getProjectId(), StoredFileType.XML, run.getCreatedByUserId());
        String baseName = baseName(storedFile.originalFilename());

        PageXml pageXml;
        if (existing.isEmpty()) {
            pageXml = new PageXml(
                    storedFile.originalFilename(),
                    storedFile.storagePath(),
                    storedFile.mimeType(),
                    storedFile.sizeBytes(),
                    "original",
                    baseName,
                    XmlSchema.PAGE_XML,
                    validation.pageVersion(),
                    page
            );
        } else {
            pageXml = existing.get(0);
            String previousPath = pageXml.getFilePath();
            pageXmlVersionService.createVersion(pageXml.getId(), run.getCreatedByUserId(), "Before LAREX Action " + run.getId());
            pageXml.setFileName(storedFile.originalFilename());
            pageXml.setFilePath(storedFile.storagePath());
            pageXml.setMimeType(storedFile.mimeType());
            pageXml.setFileSize(storedFile.sizeBytes());
            pageXml.setBaseName(baseName);
            pageXml.setSchema(XmlSchema.PAGE_XML);
            pageXml.setSchemaVersion(validation.pageVersion());
            pageXml.setPage(page);
            annotationReadCache.evict(pageXml.getId());
            fileStorageService.deleteStoredFile(previousPath);

            for (int index = 1; index < existing.size(); index++) {
                PageXml duplicate = existing.get(index);
                fileStorageService.deleteStoredFile(duplicate.getFilePath());
                pageXmlRepository.delete(duplicate);
                annotationReadCache.evict(duplicate.getId());
            }
        }

        pageXml = pageXmlRepository.save(pageXml);
        pageXmlCanonicalizationService.canonicalizeAtIngest(pageXml, run.getCreatedByUserId(), "LAREX Action " + run.getId());
        workspaceQuotaRefreshService.scheduleUsageRefresh(run.getWorkspaceId());
        return Map.of("type", "xml", "pageId", page.getId(), "fileId", pageXml.getId(), "variant", pageXml.getVariant());
    }

    private void schedulePageReindexAfterCommit(Set<String> pageIds) {
        if (pageIds == null || pageIds.isEmpty()) {
            return;
        }
        Set<String> ids = new LinkedHashSet<>(pageIds);
        Runnable task = () -> {
            for (String pageId : ids) {
                pageRepository.findById(pageId).ifPresent(page -> {
                    try {
                        pageFilterIndexService.indexPageFromXml(page);
                    } catch (Exception e) {
                        log.warn("Failed to index page {} after LAREX Action result import: {}", pageId, e.getMessage(), e);
                    }
                });
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    importTaskExecutor.execute(task);
                }
            });
        } else {
            importTaskExecutor.execute(task);
        }
    }

    private Map<String, Object> storeImageResult(ActionRun run, ActionDto.ResultFile resultFile, MultipartFile file) throws IOException {
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("Image result has an invalid content type");
        }
        Page page = pageRepository.findByIdAndProjectId(resultFile.pageId(), run.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        String variant = normalizeVariant(resultFile.variant(), "action");
        String originalName = chooseFileName(resultFile.fileName(), file.getOriginalFilename(), page.getName() + "-" + variant);
        List<PageImage> existing = pageImageRepository.findByPageIdAndVariant(page.getId(), variant);
        for (PageImage image : existing) {
            fileStorageService.deleteStoredFile(image.getFilePath());
            if (image.getThumbnailPath() != null) {
                fileStorageService.deleteStoredFile(image.getThumbnailPath());
            }
            pageImageRepository.delete(image);
        }

        var storedFile = fileStorageService.storeMultipartFile(new RenamedMultipartFile(file, originalName, file.getContentType()),
                run.getWorkspaceId(), run.getProjectId(), StoredFileType.IMG, run.getCreatedByUserId());
        ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(storedFile.originalFilename());
        PageImage pageImage = new PageImage(
                storedFile.originalFilename(),
                storedFile.storagePath(),
                storedFile.mimeType(),
                storedFile.sizeBytes(),
                variant,
                nameInfo.baseName(),
                page
        );
        pageImage = pageImageRepository.save(pageImage);
        String thumbnailPath = thumbnailService.generateThumbnail(storedFile.storagePath());
        if (thumbnailPath != null) {
            pageImage.setThumbnailPath(thumbnailPath);
            pageImage = pageImageRepository.save(pageImage);
        }
        workspaceQuotaRefreshService.scheduleUsageRefresh(run.getWorkspaceId());
        return Map.of("type", "image", "pageId", page.getId(), "fileId", pageImage.getId(), "variant", variant);
    }

    private void dispatchAsync(String runId, String rawSecret, String publicApiBaseUrl) {
        importTaskExecutor.execute(() -> {
            try {
                dispatch(runId, rawSecret, publicApiBaseUrl);
            } catch (Exception e) {
                log.warn("Failed to dispatch LAREX Action run {}: {}", runId, describeException(e), e);
                markDispatchFailed(runId, e);
            }
        });
    }

    private void dispatchAfterCommit(String runId, String rawSecret, String publicApiBaseUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchAsync(runId, rawSecret, publicApiBaseUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchAsync(runId, rawSecret, publicApiBaseUrl);
            }
        });
    }

    private void dispatch(String runId, String rawSecret, String publicApiBaseUrl) throws IOException, InterruptedException {
        ActionRun run = runRepository.findWithProcessorDefinitionById(runId)
                .orElseThrow(() -> new IllegalStateException("Action run not found"));
        if (run.isCancelRequested() || run.getStatus() == Status.CANCELLED) {
            releaseLocks(run);
            return;
        }
        ActionProcessorDefinition definition = run.getProcessorDefinition();
        run.setStatus(Status.DISPATCHING);
        run.setStatusMessage("Dispatching");
        runRepository.save(run);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", run.getId());
        payload.put("protocolVersion", ACTION_PROTOCOL_VERSION);
        payload.put("processorId", definition.getProcessorKey());
        payload.put("workspaceId", run.getWorkspaceId());
        payload.put("projectId", run.getProjectId());
        payload.put("pageIds", readPageIds(run));
        payload.put("parameters", readObjectMap(run.getParametersJson()));
        payload.put("secret", rawSecret);
        payload.put("pullUrl", publicApiBaseUrl + "/public/actions/runs/" + run.getId() + "/input");
        payload.put("heartbeatUrl", publicApiBaseUrl + "/public/actions/runs/" + run.getId() + "/heartbeat");
        payload.put("resultUrl", publicApiBaseUrl + "/public/actions/runs/" + run.getId() + "/results");

        URI endpointUri = URI.create(definition.getEndpointUrl());
        String body = writeJson(payload);
        Map<String, String> authHeaders = endpointAuthService.buildDispatchHeaders(
                definitionService.readParsedDocument(definition).endpoint().auth(),
                definition.getProcessorKey(),
                run.getId(),
                endpointUri,
                generateOpaqueToken(18),
                body
        );

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpointUri)
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(definition.getEndpointTimeoutSeconds()))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        authHeaders.forEach(requestBuilder::header);
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IOException("Could not connect to Action endpoint " + endpointUri + ": " + describeException(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Interrupted while dispatching Action endpoint " + endpointUri);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Processor dispatch to " + endpointUri + " failed with HTTP " + response.statusCode());
        }

        run = runRepository.findById(runId).orElseThrow();
        if (run.isCancelRequested() || run.getStatus() == Status.CANCELLED) {
            releaseLocks(run);
            return;
        }
        run.setStatus(Status.RUNNING);
        run.setStatusMessage("Dispatched");
        run.setLastHeartbeatAt(LocalDateTime.now());
        runRepository.save(run);
    }

    private void markDispatchFailed(String runId, Exception e) {
        ActionRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        if (run.isCancelRequested() || run.getStatus() == Status.CANCELLED) {
            releaseLocks(run);
            return;
        }
        run.setStatus(Status.FAILED);
        run.setErrorMessage(limit(describeException(e), 4000));
        run.setStatusMessage("Dispatch failed");
        run.setCompletedAt(LocalDateTime.now());
        releaseLocks(run);
        runRepository.save(run);
    }

    private String describeException(Exception e) {
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return e.getClass().getSimpleName() + " caused by " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        return e.getClass().getSimpleName();
    }

    private void applyLocks(Project project, List<Page> pages, ActionRun run) {
        LocalDateTime now = LocalDateTime.now();
        if (run.getLockMode() == LockMode.PROJECT) {
            project.setLocked(true);
            project.setLockedReason("LAREX Action running: " + run.getProcessorDefinition().getName());
            project.setLockedByActionRunId(run.getId());
            project.setLockedAt(now);
            projectRepository.save(project);
        }
        for (Page page : pages) {
            page.setLocked(true);
            page.setLockedReason("LAREX Action running: " + run.getProcessorDefinition().getName());
            page.setLockedByActionRunId(run.getId());
            page.setLockedAt(now);
        }
        pageRepository.saveAll(pages);
    }

    private void releaseLocks(ActionRun run) {
        Project project = projectRepository.findById(run.getProjectId()).orElse(null);
        if (project != null && run.getId().equals(project.getLockedByActionRunId())) {
            project.setLocked(false);
            project.setLockedReason(null);
            project.setLockedByActionRunId(null);
            project.setLockedAt(null);
            projectRepository.save(project);
        }
        List<Page> pages = pageRepository.findAllByIdIn(readPageIds(run));
        for (Page page : pages) {
            if (run.getId().equals(page.getLockedByActionRunId())) {
                page.setLocked(false);
                page.setLockedReason(null);
                page.setLockedByActionRunId(null);
                page.setLockedAt(null);
            }
        }
        pageRepository.saveAll(pages);
    }

    private void validateLocks(Project project, List<Page> pages) {
        if (project.isLocked()) {
            throw new IllegalStateException(project.getLockedReason() == null ? "Project is locked" : project.getLockedReason());
        }
        for (Page page : pages) {
            if (page.isLocked()) {
                throw new IllegalStateException("Page is locked: " + page.getName());
            }
        }
    }

    private List<Page> resolveRunPages(String projectId, List<String> requestedPageIds) {
        List<Page> pages;
        if (requestedPageIds == null || requestedPageIds.isEmpty()) {
            pages = pageRepository.findByProjectId(projectId);
        } else {
            List<String> normalized = requestedPageIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();
            pages = pageRepository.findByIdInAndProjectId(normalized, projectId);
            if (pages.size() != normalized.size()) {
                throw new IllegalArgumentException("One or more pages do not belong to this project");
            }
        }
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("No pages selected");
        }
        return pages;
    }

    private void requireAssigned(String workspaceId, String projectId, String definitionId) {
        boolean assigned = assignmentRepository.findExecutableAssignments(workspaceId, projectId).stream()
                .anyMatch(assignment -> assignment.getProcessorDefinition().getId().equals(definitionId));
        if (!assigned) {
            throw new SecurityException("Action processor is not assigned to this project");
        }
    }

    private void requireExecuteAccess(ActionProcessorDefinition definition, String workspaceId, String userId) {
        boolean allowed = definition.getExecuteRole() == ExecuteRole.EDITOR
                ? workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)
                : workspaceAccessService.canManageProjects(workspaceId, userId);
        if (!allowed) {
            throw new SecurityException("You do not have permission to execute this Action");
        }
    }

    private Project requireProject(String workspaceId, String projectId) {
        return projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
    }

    private ActionRun requireRun(String workspaceId, String projectId, String runId) {
        ActionRun run = runRepository.findWithProcessorDefinitionById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Action run not found"));
        if (!workspaceId.equals(run.getWorkspaceId()) || !projectId.equals(run.getProjectId())) {
            throw new IllegalArgumentException("Action run not found");
        }
        return run;
    }

    private ActionRun authenticateRun(String runId, String authorizationHeader) {
        return authenticateRun(runId, authorizationHeader, false);
    }

    private ActionRun authenticateRunForUpdate(String runId, String authorizationHeader) {
        return authenticateRun(runId, authorizationHeader, true);
    }

    private ActionRun authenticateRun(String runId, String authorizationHeader, boolean forUpdate) {
        String rawToken = extractBearerToken(authorizationHeader);
        if (rawToken == null) {
            throw new SecurityException("Missing Action run secret");
        }
        ActionRun run = (forUpdate
                ? runRepository.findWithProcessorDefinitionByIdForUpdate(runId)
                : runRepository.findWithProcessorDefinitionById(runId))
                .orElseThrow(() -> new SecurityException("Invalid Action run secret"));
        if (!Objects.equals(run.getSecretHash(), sha256(rawToken))) {
            throw new SecurityException("Invalid Action run secret");
        }
        if (run.getSecretExpiresAt() == null || !run.getSecretExpiresAt().isAfter(LocalDateTime.now())) {
            throw new SecurityException("Action run secret has expired");
        }
        return run;
    }

    private ActionDto.AssignmentResponse toAssignmentResponse(ActionProcessorAssignment assignment) {
        return new ActionDto.AssignmentResponse(
                assignment.getId(),
                assignment.getWorkspaceId(),
                assignment.getProjectId(),
                assignment.isEnabled(),
                definitionService.toDefinitionResponse(assignment.getProcessorDefinition())
        );
    }

    private ActionDto.ExecutableProcessorResponse toExecutableResponse(ActionProcessorAssignment assignment,
                                                                       Project project,
                                                                       String userId) {
        ActionProcessorDefinition definition = assignment.getProcessorDefinition();
        boolean executable = true;
        String blockedReason = null;
        if (project.isLocked()) {
            executable = false;
            blockedReason = project.getLockedReason() == null ? "Project is locked" : project.getLockedReason();
        } else if (definition.getExecuteRole() == ExecuteRole.CURATOR && !workspaceAccessService.canManageProjects(assignment.getWorkspaceId(), userId)) {
            executable = false;
            blockedReason = "Curator access is required";
        } else if (definition.getExecuteRole() == ExecuteRole.EDITOR && !workspaceAccessService.hasWorkspaceAccess(assignment.getWorkspaceId(), userId)) {
            executable = false;
            blockedReason = "Workspace access is required";
        }
        return new ActionDto.ExecutableProcessorResponse(
                assignment.getId(),
                definitionService.toDefinitionResponse(definition),
                executable,
                blockedReason
        );
    }

    private ActionDto.RunResponse toRunResponse(ActionRun run) {
        ActionProcessorDefinition definition = run.getProcessorDefinition();
        return new ActionDto.RunResponse(
                run.getId(),
                definition.getId(),
                definition.getProcessorKey(),
                definition.getName(),
                run.getWorkspaceId(),
                run.getProjectId(),
                readPageIds(run),
                run.getStatus(),
                run.getLockMode(),
                run.getProgressPercent(),
                run.getStatusMessage(),
                run.getErrorMessage(),
                run.isCancelRequested(),
                run.getLastHeartbeatAt(),
                run.getCreated(),
                run.getUpdated(),
                run.getCompletedAt()
        );
    }

    private ActionDto.MachinePageFile toMachineImageFile(String publicApiBaseUrl, String runId, PageImage image) {
        return new ActionDto.MachinePageFile(
                image.getId(),
                image.getFileName(),
                image.getVariant(),
                image.getMimeType(),
                image.getFileSize(),
                publicApiBaseUrl + "/public/actions/runs/" + runId + "/files/images/" + image.getId()
        );
    }

    private ActionDto.MachinePageFile toMachineXmlFile(String publicApiBaseUrl, String runId, PageXml xml) {
        return new ActionDto.MachinePageFile(
                xml.getId(),
                xml.getFileName(),
                xml.getVariant(),
                xml.getMimeType(),
                xml.getFileSize(),
                publicApiBaseUrl + "/public/actions/runs/" + runId + "/files/xml/" + xml.getId()
        );
    }

    private MultipartFile resolveMultipart(MultiValueMap<String, MultipartFile> files, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("Result file fieldName is required");
        }
        MultipartFile file = files == null ? null : files.getFirst(fieldName);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Missing result file part: " + fieldName);
        }
        return file;
    }

    private List<String> readPageIds(ActionRun run) {
        try {
            return objectMapper.readValue(run.getPageIdsJson(), STRING_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Map<String, Object> readObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize Action payload", e);
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        String token = authorizationHeader.substring(prefix.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private String generateOpaqueToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String rawSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawSecret.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeVariant(String value, String fallback) {
        String variant = value == null || value.isBlank() ? fallback : value.trim();
        if (!variant.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Invalid output variant");
        }
        return variant;
    }

    private String chooseFileName(String preferred, String original, String fallback) {
        String value = preferred != null && !preferred.isBlank() ? preferred : original;
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String baseName(String fileName) {
        if (fileName == null) {
            return "action";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private void appendLog(ActionRun run, String logMessage) {
        if (logMessage == null || logMessage.isBlank()) {
            return;
        }
        String existing = run.getLogText() == null ? "" : run.getLogText();
        run.setLogText(limit(existing + (existing.isEmpty() ? "" : "\n") + logMessage.trim(), 100_000));
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record MachineFile(String storagePath, String fileName, String mimeType) {}

    private static class RenamedMultipartFile implements MultipartFile {
        private final MultipartFile delegate;
        private final String originalFilename;
        private final String contentType;

        private RenamedMultipartFile(MultipartFile delegate, String originalFilename, String contentType) {
            this.delegate = delegate;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public long getSize() {
            return delegate.getSize();
        }

        @Override
        public byte[] getBytes() throws IOException {
            return delegate.getBytes();
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            delegate.transferTo(dest);
        }
    }
}
