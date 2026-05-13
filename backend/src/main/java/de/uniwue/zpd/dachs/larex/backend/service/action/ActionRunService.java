package de.uniwue.zpd.dachs.larex.backend.service.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorAssignment;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ExecuteRole;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.LockMode;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRunLogEvent;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Status;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorAssignmentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorWorkspaceAvailabilityRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunLogEventRepository;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
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
import java.time.temporal.ChronoUnit;
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
import java.util.regex.Pattern;

@Service
@Transactional
public class ActionRunService {

    private static final Logger log = LoggerFactory.getLogger(ActionRunService.class);
    private static final int ACTION_PROTOCOL_VERSION = 1;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern ACTION_RUN_SECRET_PATTERN = Pattern.compile("lrx_act_[A-Za-z0-9_-]{20,}");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+\\-/]+=*");

    private final ActionProcessorDefinitionRepository definitionRepository;
    private final ActionProcessorAssignmentRepository assignmentRepository;
    private final ActionProcessorWorkspaceAvailabilityRepository availabilityRepository;
    private final ActionRunRepository runRepository;
    private final ActionRunLogEventRepository logEventRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final GlobalAdminService globalAdminService;
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
    private final ActionAuditService actionAuditService;
    private final HttpClient httpClient;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${larex.actions.dispatch.max-attempts:3}")
    private int dispatchMaxAttempts;

    @Value("${larex.actions.dispatch.retry-backoff-ms:3000}")
    private long dispatchRetryBackoffMillis;

    @Value("${larex.actions.timeout.dispatch-minutes:5}")
    private long dispatchTimeoutMinutes;

    @Value("${larex.actions.timeout.heartbeat-minutes:30}")
    private long heartbeatTimeoutMinutes;

    @Value("${larex.actions.retention.terminal-days:30}")
    private long terminalRunRetentionDays;

    @Value("${larex.actions.results.max-files:500}")
    private int maxResultFiles;

    @Value("${larex.actions.results.max-file-bytes:536870912}")
    private long maxResultFileBytes;

    @Value("${larex.actions.results.max-total-bytes:2147483648}")
    private long maxResultTotalBytes;

    public ActionRunService(ActionProcessorDefinitionRepository definitionRepository,
                            ActionProcessorAssignmentRepository assignmentRepository,
                            ActionProcessorWorkspaceAvailabilityRepository availabilityRepository,
                            ActionRunRepository runRepository,
                            ActionRunLogEventRepository logEventRepository,
                            ProjectRepository projectRepository,
                            PageRepository pageRepository,
                            PageImageRepository pageImageRepository,
                            PageXmlRepository pageXmlRepository,
                            WorkspaceAccessService workspaceAccessService,
                            GlobalAdminService globalAdminService,
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
                            AnnotationReadCache annotationReadCache,
                            ActionAuditService actionAuditService) {
        this.definitionRepository = definitionRepository;
        this.assignmentRepository = assignmentRepository;
        this.availabilityRepository = availabilityRepository;
        this.runRepository = runRepository;
        this.logEventRepository = logEventRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.globalAdminService = globalAdminService;
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
        this.actionAuditService = actionAuditService;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ActionDto.DefinitionResponse> listAvailableDefinitions(String workspaceId, String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        LinkedHashMap<String, ActionProcessorDefinition> available = new LinkedHashMap<>();
        definitionRepository.findByEnabledTrueAndGlobalAvailableTrueOrderByNameAsc()
                .forEach(definition -> available.put(definition.getId(), definition));
        availabilityRepository.findByWorkspaceIdAndEnabledTrueOrderByCreatedAsc(workspaceId).stream()
                .map(availability -> availability.getProcessorDefinition())
                .filter(ActionProcessorDefinition::isEnabled)
                .filter(definition -> !definition.isGlobalAvailable())
                .forEach(definition -> available.putIfAbsent(definition.getId(), definition));
        assignmentRepository.findByWorkspaceIdOrderByCreatedAsc(workspaceId).stream()
                .map(ActionProcessorAssignment::getProcessorDefinition)
                .filter(ActionProcessorDefinition::isEnabled)
                .filter(definition -> !definition.isGlobalAvailable())
                .forEach(definition -> available.putIfAbsent(definition.getId(), definition));
        return available.values().stream()
                .sorted(Comparator.comparing(ActionProcessorDefinition::getName, String.CASE_INSENSITIVE_ORDER))
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
        if (definition.isGlobalAvailable()) {
            throw new IllegalArgumentException("Global Actions are available automatically and cannot be assigned");
        }
        if (!availabilityRepository.existsByProcessorDefinitionIdAndWorkspaceIdAndEnabledTrue(definition.getId(), workspaceId)) {
            throw new SecurityException("Action workflow is not available to this workspace");
        }
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
        ActionProcessorAssignment saved = assignmentRepository.save(assignment);
        actionAuditService.record("ACTION_ASSIGNMENT_CREATE", "SUCCESS", userId, definition.getId(), null,
                workspaceId, projectId, Map.of("enabled", saved.isEnabled()));
        return toAssignmentResponse(saved);
    }

    public void unassignProcessor(String workspaceId, String assignmentId, String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        ActionProcessorAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Action processor assignment not found"));
        if (!workspaceId.equals(assignment.getWorkspaceId())) {
            throw new IllegalArgumentException("Action processor assignment not found");
        }
        String definitionId = assignment.getProcessorDefinition().getId();
        String projectId = assignment.getProjectId();
        assignmentRepository.delete(assignment);
        actionAuditService.record("ACTION_ASSIGNMENT_DELETE", "SUCCESS", userId, definitionId, null,
                workspaceId, projectId, Map.of());
    }

    @Transactional(readOnly = true)
    public List<ActionDto.ExecutableProcessorResponse> listExecutableProcessors(String workspaceId, String projectId, String userId) {
        Project project = requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        LinkedHashMap<String, ActionDto.ExecutableProcessorResponse> executable = new LinkedHashMap<>();
        definitionRepository.findByEnabledTrueAndGlobalAvailableTrueOrderByNameAsc()
                .forEach(definition -> executable.put(definition.getId(), toExecutableResponse(null, definition, workspaceId, project, userId)));

        assignmentRepository.findExecutableAssignments(workspaceId, projectId).stream()
                .filter(assignment -> !assignment.getProcessorDefinition().isGlobalAvailable())
                .filter(assignment -> isWorkspaceAvailable(assignment.getProcessorDefinition().getId(), workspaceId))
                .sorted(Comparator.comparing((ActionProcessorAssignment assignment) -> projectId.equals(assignment.getProjectId()) ? 1 : 0)
                        .thenComparing(ActionProcessorAssignment::getCreated))
                .forEach(assignment -> executable.put(assignment.getProcessorDefinition().getId(),
                        toExecutableResponse(assignment, assignment.getProcessorDefinition(), workspaceId, project, userId)));
        return executable.values().stream()
                .sorted(Comparator.comparing(response -> response.processor().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public ActionDto.StartRunResponse startRun(String workspaceId,
                                               String projectId,
                                               ActionDto.StartRunRequest request,
                                               String userId,
                                               String publicApiBaseUrl) {
        ActionProcessorDefinition definition = definitionRepository.findByIdForUpdate(request.processorDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found"));
        if (!definition.isEnabled()) {
            throw new IllegalArgumentException("Action processor is disabled");
        }
        requireAssigned(workspaceId, projectId, definition.getId());
        requireExecuteAccess(definition, workspaceId, userId);

        Project project = requireProjectForUpdate(workspaceId, projectId);
        List<Page> pages = resolveRunPagesForUpdate(projectId, request.pageIds());
        enforceConcurrencyLimit(definition, workspaceId, projectId);
        validateLocks(project, pages);
        return createRun(
                workspaceId,
                projectId,
                project,
                definition,
                pages,
                userId,
                publicApiBaseUrl,
                resolveRunParameters(definition),
                "ACTION_RUN_START",
                Map.of()
        );
    }

    private ActionDto.StartRunResponse createRun(String workspaceId,
                                                 String projectId,
                                                 Project project,
                                                 ActionProcessorDefinition definition,
                                                 List<Page> pages,
                                                 String userId,
                                                 String publicApiBaseUrl,
                                                 Map<String, Object> parameters,
                                                 String auditAction,
                                                 Map<String, ?> auditDetails) {
        String rawSecret = "lrx_act_" + generateOpaqueToken(32);
        ActionRun run = new ActionRun();
        run.setProcessorDefinition(definition);
        run.setWorkspaceId(workspaceId);
        run.setProjectId(projectId);
        run.setCreatedByUserId(userId);
        run.setStatus(Status.PENDING);
        run.setLockMode(definition.getLockMode());
        run.setPageIdsJson(writeJson(pages.stream().map(Page::getId).toList()));
        run.setParametersJson(writeJson(parameters));
        run.setSecretHash(sha256(rawSecret));
        run.setSecretPrefix(rawSecret.substring(0, Math.min(rawSecret.length(), 12)));
        run.setSecretExpiresAt(LocalDateTime.now().plusMinutes(definitionService.defaultTokenTtlMinutes()));
        run.setStatusMessage("Created");
        run = runRepository.save(run);

        applyLocks(project, pages, run);
        ActionRun savedRun = runRepository.save(run);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("pageCount", pages.size());
        if (auditDetails != null) {
            details.putAll(auditDetails);
        }
        actionAuditService.record(auditAction, "SUCCESS", userId, definition.getId(), savedRun.getId(),
                workspaceId, projectId, details);
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

    @Transactional(readOnly = true)
    public ActionDto.RunDetailResponse getRunDetail(String workspaceId, String projectId, String runId, String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ActionRun run = requireRun(workspaceId, projectId, runId);
        return toRunDetailResponse(run);
    }

    public ActionDto.StartRunResponse retryRun(String workspaceId,
                                               String projectId,
                                               String runId,
                                               String userId,
                                               String publicApiBaseUrl) {
        Project project = requireProject(workspaceId, projectId);
        ActionRun sourceRun = requireRun(workspaceId, projectId, runId);
        if (sourceRun.getStatus() != Status.FAILED && sourceRun.getStatus() != Status.CANCELLED) {
            throw new IllegalStateException("Only failed or cancelled Action runs can be retried");
        }

        ActionProcessorDefinition definition = definitionRepository.findByIdForUpdate(sourceRun.getProcessorDefinition().getId())
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found"));
        if (!definition.isEnabled()) {
            throw new IllegalArgumentException("Action processor is disabled");
        }
        requireAssigned(workspaceId, projectId, definition.getId());
        requireExecuteAccess(definition, workspaceId, userId);

        project = requireProjectForUpdate(workspaceId, projectId);
        List<Page> pages = resolveRunPagesForUpdate(projectId, readPageIds(sourceRun));
        enforceConcurrencyLimit(definition, workspaceId, projectId);
        validateLocks(project, pages);
        return createRun(
                workspaceId,
                projectId,
                project,
                definition,
                pages,
                userId,
                publicApiBaseUrl,
                readObjectMap(sourceRun.getParametersJson()),
                "ACTION_RUN_RETRY",
                Map.of("sourceRunId", sourceRun.getId())
        );
    }

    @Transactional(readOnly = true)
    public List<ActionDto.AdminRunResponse> listAdminRuns(String definitionId) {
        requireGlobalAdmin();
        requireDefinition(definitionId);
        return runRepository.findByProcessorDefinitionIdOrderByCreatedDesc(definitionId).stream()
                .map(this::toAdminRunResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ActionDto.AdminRunResponse getAdminRun(String definitionId, String runId) {
        requireGlobalAdmin();
        ActionRun run = runRepository.findWithProcessorDefinitionById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Action run not found"));
        if (!definitionId.equals(run.getProcessorDefinition().getId())) {
            throw new IllegalArgumentException("Action run not found");
        }
        return toAdminRunResponse(run);
    }

    public ActionDto.ClearRunsResponse clearTerminalAdminRuns(String definitionId) {
        requireGlobalAdmin();
        requireDefinition(definitionId);
        List<ActionRun> terminalRuns = runRepository.findByProcessorDefinitionIdAndStatusIn(definitionId, terminalStatuses());
        runRepository.deleteAll(terminalRuns);
        return new ActionDto.ClearRunsResponse(terminalRuns.size());
    }

    @Scheduled(fixedDelayString = "${larex.actions.watchdog-interval-ms:60000}")
    public void reconcileStaleRuns() {
        LocalDateTime now = LocalDateTime.now();
        expireDispatchingRuns(now.minusMinutes(Math.max(1, dispatchTimeoutMinutes)));
        expireHeartbeatRuns(now.minusMinutes(Math.max(1, heartbeatTimeoutMinutes)));
        pruneTerminalRuns(now.minusDays(Math.max(1, terminalRunRetentionDays)));
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
            expireRunSecret(run);
        }
        releaseLocks(run);
        ActionRun saved = runRepository.save(run);
        actionAuditService.record("ACTION_RUN_CANCEL", "SUCCESS", userId, run.getProcessorDefinition().getId(), run.getId(),
                workspaceId, projectId, Map.of("status", saved.getStatus().name()));
        return toRunResponse(saved);
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

    @Transactional(readOnly = true)
    public void authenticateMachineRun(String runId, String authorizationHeader) {
        authenticateRun(runId, authorizationHeader);
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
            run.setStatusMessage(limit(redactProcessorSecrets(request.statusMessage()), 2000));
        }
        appendLog(run, request.logLevel(), request.log());
        if ("failed".equalsIgnoreCase(request.status())) {
            run.setStatus(Status.FAILED);
            run.setErrorMessage(limit(redactProcessorSecrets(request.errorMessage()), 4000));
            run.setCompletedAt(LocalDateTime.now());
            expireRunSecret(run);
            releaseLocks(run);
            actionAuditService.record("ACTION_RUN_HEARTBEAT_FAILED", "FAILURE", run.getCreatedByUserId(),
                    run.getProcessorDefinition().getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(),
                    Map.of("error", limit(Objects.toString(redactProcessorSecrets(request.errorMessage()), ""), 1000)));
        }
        runRepository.save(run);
        return new ActionDto.HeartbeatResponse(run.isCancelRequested());
    }

    @Transactional(rollbackFor = Exception.class)
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
        validateResultManifest(resultFiles, files);
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
            run.setStatusMessage(limit(redactProcessorSecrets(manifest.message()), 2000));
            run.setProgressPercent(run.getStatus() == Status.COMPLETED ? 100 : run.getProgressPercent());
            run.setCompletedAt(LocalDateTime.now());
            expireRunSecret(run);
            releaseLocks(run);
            ActionRun savedRun = runRepository.save(run);
            schedulePageReindexAfterCommit(xmlResultPageIds);
            actionAuditService.record(run.getStatus() == Status.COMPLETED ? "ACTION_RUN_COMPLETE" : "ACTION_RUN_RESULT_FAILED",
                    run.getStatus() == Status.COMPLETED ? "SUCCESS" : "FAILURE",
                    run.getCreatedByUserId(), definition.getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(),
                    Map.of("resultCount", stored.size()));
            return toRunResponse(savedRun);
        } catch (IOException | RuntimeException e) {
            String failureMessage = describeException(e);
            run.setStatus(Status.FAILED);
            run.setStatusMessage("Result import failed");
            run.setErrorMessage(limit(failureMessage, 4000));
            run.setCompletedAt(LocalDateTime.now());
            expireRunSecret(run);
            releaseLocks(run);
            runRepository.save(run);
            actionAuditService.record("ACTION_RUN_RESULT_IMPORT_FAILED", "FAILURE", run.getCreatedByUserId(),
                    definition.getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(),
                    Map.of("error", limit(failureMessage, 1000)));
            scheduleResultImportFailureAfterRollback(run.getId(), failureMessage);
            throw e;
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(run.getWorkspaceId(), reservedBytes);
        }
    }

    private Map<String, Object> storeXmlResult(ActionRun run, ActionDto.ResultFile resultFile, MultipartFile file) throws IOException {
        validateResultFileSize(file);
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
            List<String> replacedXmlStoragePaths = new ArrayList<>();
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
            replacedXmlStoragePaths.add(previousPath);

            for (int index = 1; index < existing.size(); index++) {
                PageXml duplicate = existing.get(index);
                replacedXmlStoragePaths.add(duplicate.getFilePath());
                pageXmlRepository.delete(duplicate);
                annotationReadCache.evict(duplicate.getId());
            }
            deleteStoredFilesAfterCommit(replacedXmlStoragePaths);
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

    private void deleteStoredFilesAfterCommit(Collection<String> storagePaths) {
        if (storagePaths == null || storagePaths.isEmpty()) {
            return;
        }
        List<String> paths = storagePaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .toList();
        if (paths.isEmpty()) {
            return;
        }
        Runnable task = () -> fileStorageService.deleteStoredFiles(paths);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    importTaskExecutor.execute(task);
                }
            });
        } else {
            task.run();
        }
    }

    private void scheduleResultImportFailureAfterRollback(String runId, String failureMessage) {
        Runnable task = () -> persistResultImportFailure(runId, failureMessage);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        importTaskExecutor.execute(task);
                    }
                }
            });
        } else {
            task.run();
        }
    }

    private void persistResultImportFailure(String runId, String failureMessage) {
        try {
            ActionRun run = runRepository.findWithProcessorDefinitionById(runId).orElse(null);
            if (run == null || terminalStatuses().contains(run.getStatus())) {
                return;
            }
            run.setStatus(Status.FAILED);
            run.setStatusMessage("Result import failed");
            run.setErrorMessage(limit(failureMessage, 4000));
            run.setCompletedAt(LocalDateTime.now());
            expireRunSecret(run);
            releaseLocks(run);
            runRepository.save(run);
            actionAuditService.record("ACTION_RUN_RESULT_IMPORT_FAILED", "FAILURE", run.getCreatedByUserId(),
                    run.getProcessorDefinition().getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(),
                    Map.of("error", limit(failureMessage, 1000)));
        } catch (Exception persistFailure) {
            log.warn("Failed to persist LAREX Action result import failure for run {}: {}",
                    runId, describeException(persistFailure), persistFailure);
        }
    }

    private Map<String, Object> storeImageResult(ActionRun run, ActionDto.ResultFile resultFile, MultipartFile file) throws IOException {
        validateResultFileSize(file);
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("Image result has an invalid content type");
        }
        if (ImageIO.read(file.getInputStream()) == null) {
            throw new IllegalArgumentException("Image result could not be decoded");
        }
        Page page = pageRepository.findByIdAndProjectId(resultFile.pageId(), run.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        String variant = normalizeVariant(resultFile.variant(), "action");
        String originalName = chooseFileName(resultFile.fileName(), file.getOriginalFilename(), page.getName() + "-" + variant);
        List<PageImage> existing = pageImageRepository.findByPageIdAndVariant(page.getId(), variant);
        List<String> replacedStoragePaths = new ArrayList<>();
        for (PageImage image : existing) {
            replacedStoragePaths.add(image.getFilePath());
            if (image.getThumbnailPath() != null) {
                replacedStoragePaths.add(image.getThumbnailPath());
            }
            pageImageRepository.delete(image);
        }
        deleteStoredFilesAfterCommit(replacedStoragePaths);

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
            int attempts = Math.max(1, dispatchMaxAttempts);
            Exception lastFailure = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    dispatch(runId, rawSecret, publicApiBaseUrl, attempt, attempts);
                    return;
                } catch (Exception e) {
                    lastFailure = e;
                    log.warn("Failed to dispatch LAREX Action run {} on attempt {}/{}: {}", runId, attempt, attempts, describeException(e), e);
                    if (attempt < attempts && !isRunCancelled(runId)) {
                        sleepBeforeDispatchRetry();
                    }
                }
            }
            markDispatchFailed(runId, lastFailure == null ? new IllegalStateException("Dispatch failed") : lastFailure);
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

    private void dispatch(String runId, String rawSecret, String publicApiBaseUrl, int attempt, int attempts) throws IOException, InterruptedException {
        ActionRun run = runRepository.findWithProcessorDefinitionById(runId)
                .orElseThrow(() -> new IllegalStateException("Action run not found"));
        if (run.isCancelRequested() || run.getStatus() == Status.CANCELLED) {
            releaseLocks(run);
            return;
        }
        ActionProcessorDefinition definition = run.getProcessorDefinition();
        run.setStatus(Status.DISPATCHING);
        run.setStatusMessage(attempts > 1 ? "Dispatching (attempt " + attempt + "/" + attempts + ")" : "Dispatching");
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

        definitionService.requireEndpointUrlAllowed(definition.getEndpointUrl());
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
        actionAuditService.record("ACTION_RUN_DISPATCH", "SUCCESS", run.getCreatedByUserId(), definition.getId(), run.getId(),
                run.getWorkspaceId(), run.getProjectId(), Map.of("attempt", attempt));
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
        expireRunSecret(run);
        releaseLocks(run);
        runRepository.save(run);
        actionAuditService.record("ACTION_RUN_DISPATCH_FAILED", "FAILURE", run.getCreatedByUserId(), run.getProcessorDefinition().getId(), run.getId(),
                run.getWorkspaceId(), run.getProjectId(), Map.of("error", limit(describeException(e), 1000)));
    }

    private boolean isRunCancelled(String runId) {
        return runRepository.findById(runId)
                .map(run -> run.isCancelRequested() || run.getStatus() == Status.CANCELLED)
                .orElse(true);
    }

    private void sleepBeforeDispatchRetry() {
        try {
            Thread.sleep(Math.max(0, dispatchRetryBackoffMillis));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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

    private List<Page> resolveRunPagesForUpdate(String projectId, List<String> requestedPageIds) {
        List<Page> pages;
        if (requestedPageIds == null || requestedPageIds.isEmpty()) {
            pages = pageRepository.findByProjectIdForUpdate(projectId);
        } else {
            List<String> normalized = requestedPageIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();
            pages = pageRepository.findByIdInAndProjectIdForUpdate(normalized, projectId);
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
        ActionProcessorDefinition definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found"));
        if (definition.isEnabled() && definition.isGlobalAvailable()) {
            return;
        }
        boolean assigned = assignmentRepository.findExecutableAssignments(workspaceId, projectId).stream()
                .filter(assignment -> isWorkspaceAvailable(assignment.getProcessorDefinition().getId(), workspaceId))
                .anyMatch(assignment -> assignment.getProcessorDefinition().getId().equals(definitionId));
        if (!assigned) {
            throw new SecurityException("Action processor is not assigned to this project");
        }
    }

    private void enforceConcurrencyLimit(ActionProcessorDefinition definition, String workspaceId, String projectId) {
        ActionDefinitionDocument.Concurrency concurrency = definitionService.readParsedDocument(definition).concurrency();
        int maxActiveRuns = concurrency == null || concurrency.maxActiveRuns() == null ? 1 : concurrency.maxActiveRuns();
        String scope = concurrency == null || concurrency.scope() == null || concurrency.scope().isBlank()
                ? "PROJECT"
                : concurrency.scope().trim().toUpperCase(Locale.ROOT);
        long active = switch (scope) {
            case "GLOBAL" -> runRepository.countByProcessorDefinitionIdAndStatusIn(definition.getId(), activeStatuses());
            case "WORKSPACE" -> runRepository.countByProcessorDefinitionIdAndWorkspaceIdAndStatusIn(definition.getId(), workspaceId, activeStatuses());
            default -> runRepository.countByProcessorDefinitionIdAndWorkspaceIdAndProjectIdAndStatusIn(definition.getId(), workspaceId, projectId, activeStatuses());
        };
        if (active >= maxActiveRuns) {
            throw new IllegalStateException("Action concurrency limit reached (" + active + "/" + maxActiveRuns + " active " + scope.toLowerCase(Locale.ROOT) + " run(s))");
        }
    }

    private boolean isWorkspaceAvailable(String definitionId, String workspaceId) {
        return availabilityRepository.existsByProcessorDefinitionIdAndWorkspaceIdAndEnabledTrue(definitionId, workspaceId)
                || assignmentRepository.existsByProcessorDefinitionIdAndWorkspaceId(definitionId, workspaceId);
    }

    private void requireGlobalAdmin() {
        if (!globalAdminService.isGlobalAdmin()) {
            throw new SecurityException("Global administrator access is required");
        }
    }

    private ActionProcessorDefinition requireDefinition(String definitionId) {
        return definitionRepository.findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found"));
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

    private Project requireProjectForUpdate(String workspaceId, String projectId) {
        return projectRepository.findByIdAndLibraryWorkspaceIdForUpdate(projectId, workspaceId)
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

    private void expireDispatchingRuns(LocalDateTime cutoff) {
        List<ActionRun> stale = runRepository.findByStatusInAndUpdatedBefore(
                List.of(Status.PENDING, Status.DISPATCHING),
                cutoff
        );
        for (ActionRun run : stale) {
            failRunFromWatchdog(run, "Action dispatch timed out");
        }
    }

    private void expireHeartbeatRuns(LocalDateTime cutoff) {
        List<ActionRun> stale = runRepository.findByStatusInAndLastHeartbeatAtBefore(
                List.of(Status.RUNNING, Status.IMPORTING_RESULTS, Status.CANCEL_REQUESTED),
                cutoff
        );
        for (ActionRun run : stale) {
            failRunFromWatchdog(run, "Action heartbeat timed out");
        }
    }

    private void failRunFromWatchdog(ActionRun run, String message) {
        if (run.getStatus() == Status.COMPLETED || run.getStatus() == Status.FAILED || run.getStatus() == Status.CANCELLED) {
            return;
        }
        run.setStatus(Status.FAILED);
        run.setStatusMessage(message);
        run.setErrorMessage(message);
        run.setCompletedAt(LocalDateTime.now());
        expireRunSecret(run);
        releaseLocks(run);
        runRepository.save(run);
        appendLogEvent(run, "WARN", message);
        actionAuditService.record("ACTION_RUN_WATCHDOG_FAILED", "FAILURE", run.getCreatedByUserId(),
                run.getProcessorDefinition().getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(), Map.of("message", message));
    }

    private void pruneTerminalRuns(LocalDateTime cutoff) {
        List<ActionRun> expired = runRepository.findByStatusInAndCompletedAtBefore(terminalStatuses(), cutoff);
        if (expired.isEmpty()) {
            return;
        }
        runRepository.deleteAll(expired);
        log.info("Pruned {} expired LAREX Action run(s)", expired.size());
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
        if (terminalStatuses().contains(run.getStatus())) {
            throw new SecurityException("Action run secret has expired");
        }
        if (run.getSecretExpiresAt() == null || !run.getSecretExpiresAt().isAfter(LocalDateTime.now())) {
            throw new SecurityException("Action run secret has expired");
        }
        return run;
    }

    private void expireRunSecret(ActionRun run) {
        LocalDateTime now = LocalDateTime.now();
        if (run.getSecretExpiresAt() == null || run.getSecretExpiresAt().isAfter(now)) {
            run.setSecretExpiresAt(now);
        }
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
                                                                       ActionProcessorDefinition definition,
                                                                       String workspaceId,
                                                                       Project project,
                                                                       String userId) {
        boolean executable = true;
        String blockedReason = null;
        if (project.isLocked()) {
            executable = false;
            blockedReason = project.getLockedReason() == null ? "Project is locked" : project.getLockedReason();
        } else if (definition.getExecuteRole() == ExecuteRole.CURATOR && !workspaceAccessService.canManageProjects(workspaceId, userId)) {
            executable = false;
            blockedReason = "Curator access is required";
        } else if (definition.getExecuteRole() == ExecuteRole.EDITOR && !workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            executable = false;
            blockedReason = "Workspace access is required";
        }
        return new ActionDto.ExecutableProcessorResponse(
                assignment == null ? null : assignment.getId(),
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

    private ActionDto.RunDetailResponse toRunDetailResponse(ActionRun run) {
        return new ActionDto.RunDetailResponse(
                toRunResponse(run),
                run.getLogText(),
                logEventRepository.findByRunIdOrderByCreatedAsc(run.getId()).stream()
                        .map(this::toLogEventResponse)
                        .toList(),
                readResultSummary(run.getResultSummaryJson()),
                durationSeconds(run)
        );
    }

    private ActionDto.AdminRunResponse toAdminRunResponse(ActionRun run) {
        ActionProcessorDefinition definition = run.getProcessorDefinition();
        Project project = projectRepository.findById(run.getProjectId()).orElse(null);
        return new ActionDto.AdminRunResponse(
                run.getId(),
                definition.getId(),
                definition.getProcessorKey(),
                definition.getName(),
                run.getWorkspaceId(),
                run.getWorkspaceId(),
                run.getProjectId(),
                project == null ? run.getProjectId() : project.getName(),
                readPageIds(run).size(),
                run.getStatus(),
                run.getProgressPercent(),
                run.getStatusMessage(),
                run.getErrorMessage(),
                run.isCancelRequested(),
                run.getLogText(),
                logEventRepository.findByRunIdOrderByCreatedAsc(run.getId()).stream()
                        .map(this::toLogEventResponse)
                        .toList(),
                readResultSummary(run.getResultSummaryJson()),
                run.getLastHeartbeatAt(),
                run.getCreated(),
                run.getUpdated(),
                run.getCompletedAt(),
                durationSeconds(run)
        );
    }

    private ActionDto.ActionRunLogEventResponse toLogEventResponse(ActionRunLogEvent event) {
        return new ActionDto.ActionRunLogEventResponse(
                event.getId(),
                event.getLevel(),
                event.getMessage(),
                event.getCreated()
        );
    }

    private Map<String, Object> resolveRunParameters(ActionProcessorDefinition definition) {
        ActionDefinitionDocument document = definitionService.readParsedDocument(definition);
        Map<String, ActionDefinitionDocument.Parameter> definitions = document.parameters() == null ? Map.of() : document.parameters();
        Map<String, Object> resolved = new LinkedHashMap<>();

        for (Map.Entry<String, ActionDefinitionDocument.Parameter> entry : definitions.entrySet()) {
            resolved.put(entry.getKey(), defaultParameterValue(entry.getValue()));
        }
        return resolved;
    }

    private Object defaultParameterValue(ActionDefinitionDocument.Parameter parameter) {
        if (parameter.defaultValue() != null) {
            return coerceParameterValue("default", parameter, parameter.defaultValue());
        }
        return switch (parameterType(parameter)) {
            case "boolean" -> false;
            case "number" -> 0.0;
            case "integer" -> 0;
            default -> "";
        };
    }

    private Object coerceParameterValue(String key, ActionDefinitionDocument.Parameter parameter, Object value) {
        String type = parameterType(parameter);
        if ("boolean".equals(type)) {
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value instanceof String stringValue && ("true".equalsIgnoreCase(stringValue) || "false".equalsIgnoreCase(stringValue))) {
                return Boolean.parseBoolean(stringValue);
            }
            throw new IllegalArgumentException("Action parameter " + key + " must be boolean");
        }

        if ("number".equals(type) || "integer".equals(type)) {
            double numericValue = numericParameterValue(key, value);
            if (parameter.min() != null && numericValue < parameter.min()) {
                throw new IllegalArgumentException("Action parameter " + key + " must be greater than or equal to " + parameter.min());
            }
            if (parameter.max() != null && numericValue > parameter.max()) {
                throw new IllegalArgumentException("Action parameter " + key + " must be less than or equal to " + parameter.max());
            }
            if ("integer".equals(type)) {
                if (Math.rint(numericValue) != numericValue) {
                    throw new IllegalArgumentException("Action parameter " + key + " must be an integer");
                }
                return (int) numericValue;
            }
            return numericValue;
        }

        return value == null ? "" : String.valueOf(value);
    }

    private double numericParameterValue(String key, Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                // Fall through to the consistent validation error below.
            }
        }
        throw new IllegalArgumentException("Action parameter " + key + " must be numeric");
    }

    private String parameterType(ActionDefinitionDocument.Parameter parameter) {
        return parameter.type() == null ? "string" : parameter.type().trim().toLowerCase(Locale.ROOT);
    }

    private Object readResultSummary(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    private Long durationSeconds(ActionRun run) {
        if (run.getCreated() == null) {
            return null;
        }
        LocalDateTime end = run.getCompletedAt() == null ? run.getUpdated() : run.getCompletedAt();
        return end == null ? null : ChronoUnit.SECONDS.between(run.getCreated(), end);
    }

    private List<Status> terminalStatuses() {
        return List.of(Status.COMPLETED, Status.FAILED, Status.CANCELLED);
    }

    private List<Status> activeStatuses() {
        return List.of(
                Status.PENDING,
                Status.DISPATCHING,
                Status.RUNNING,
                Status.IMPORTING_RESULTS,
                Status.CANCEL_REQUESTED
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

    private void validateResultManifest(List<ActionDto.ResultFile> resultFiles, MultiValueMap<String, MultipartFile> files) {
        if (resultFiles.size() > maxResultFiles) {
            throw new IllegalArgumentException("Too many Action result files: " + resultFiles.size() + " > " + maxResultFiles);
        }
        long totalBytes = 0L;
        Set<String> fieldNames = new LinkedHashSet<>();
        for (ActionDto.ResultFile resultFile : resultFiles) {
            if (resultFile.fieldName() == null || resultFile.fieldName().isBlank()) {
                throw new IllegalArgumentException("Result file fieldName is required");
            }
            if (!fieldNames.add(resultFile.fieldName())) {
                throw new IllegalArgumentException("Duplicate result file fieldName: " + resultFile.fieldName());
            }
            MultipartFile file = resolveMultipart(files, resultFile.fieldName());
            validateResultFileSize(file);
            totalBytes += file.getSize();
            if (totalBytes > maxResultTotalBytes) {
                throw new IllegalArgumentException("Action result upload exceeds total size limit");
            }
        }
    }

    private void validateResultFileSize(MultipartFile file) {
        if (file.getSize() > maxResultFileBytes) {
            throw new IllegalArgumentException("Action result file exceeds size limit");
        }
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

    private void appendLog(ActionRun run, String level, String logMessage) {
        if (logMessage == null || logMessage.isBlank()) {
            return;
        }
        String safeLogMessage = redactProcessorSecrets(logMessage).trim();
        String existing = run.getLogText() == null ? "" : run.getLogText();
        run.setLogText(limit(existing + (existing.isEmpty() ? "" : "\n") + safeLogMessage, 100_000));
        appendLogEvent(run, level, safeLogMessage);
    }

    private void appendLogEvent(ActionRun run, String level, String logMessage) {
        if (logMessage == null || logMessage.isBlank()) {
            return;
        }
        ActionRunLogEvent event = new ActionRunLogEvent();
        event.setRun(run);
        event.setLevel(normalizeLogLevel(level));
        event.setMessage(limit(logMessage.trim(), 4000));
        logEventRepository.save(event);
    }

    private String normalizeLogLevel(String level) {
        if (level == null || level.isBlank()) {
            return "INFO";
        }
        String normalized = level.trim().toUpperCase(Locale.ROOT);
        return List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR").contains(normalized) ? normalized : "INFO";
    }

    private String redactProcessorSecrets(String value) {
        if (value == null) {
            return null;
        }
        String redacted = ACTION_RUN_SECRET_PATTERN.matcher(value).replaceAll("lrx_act_[redacted]");
        return BEARER_TOKEN_PATTERN.matcher(redacted).replaceAll("Bearer [redacted]");
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
