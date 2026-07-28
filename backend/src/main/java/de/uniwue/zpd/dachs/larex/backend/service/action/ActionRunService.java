package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorAssignment;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ExecuteRole;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.LockMode;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRunPageResult;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRunDismissal;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRunLogEvent;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Status;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.exception.ActionConcurrencyLimitException;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorAssignmentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorWorkspaceAvailabilityRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunDismissalRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunLogEventRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunPageResultRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusTracker;
import de.uniwue.zpd.dachs.larex.backend.service.notification.JobRealtimePublisher;
import de.uniwue.zpd.dachs.larex.backend.service.search.SearchLexiconService;
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
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@Transactional
public class ActionRunService {

    private static final Logger log = LoggerFactory.getLogger(ActionRunService.class);
    private static final int ACTION_PROTOCOL_VERSION = 1;
    private static final int DEFAULT_RUN_HISTORY_LIMIT = 200;
    private static final int MAX_RUN_HISTORY_LIMIT = 1000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern ACTION_RUN_SECRET_PATTERN = Pattern.compile("lrx_act_[A-Za-z0-9_-]{20,}");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+\\-/]+=*");

    private final ActionProcessorDefinitionRepository definitionRepository;
    private final ActionProcessorAssignmentRepository assignmentRepository;
    private final ActionProcessorWorkspaceAvailabilityRepository availabilityRepository;
    private final ActionRunRepository runRepository;
    private final ActionRunDismissalRepository runDismissalRepository;
    private final ActionRunLogEventRepository logEventRepository;
    private final ActionRunPageResultRepository pageResultRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final GlobalAdminService globalAdminService;
    private final ActionDefinitionService definitionService;
    private final ActionEndpointAuthService endpointAuthService;
    private final TaskExecutor importTaskExecutor;
    private final TaskScheduler actionResultTaskScheduler;
    private final HierarchicalFileStorageService fileStorageService;
    private final ThumbnailService thumbnailService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final WorkspaceQuotaRefreshService workspaceQuotaRefreshService;
    private final PageXmlValidationService pageXmlValidationService;
    private final PageXmlCanonicalizationService pageXmlCanonicalizationService;
    private final PageXmlVersionService pageXmlVersionService;
    private final PageFilterIndexService pageFilterIndexService;
    private final PageIndexStatusTracker pageIndexStatusTracker;
    private final SearchLexiconService searchLexiconService;
    private final AnnotationReadCache annotationReadCache;
    private final AnnotationProcessingService annotationProcessingService;
    private final PageXmlToAnnotationParser pageXmlToAnnotationParser;
    private final PageOrderService pageOrderService;
    private final ActionAuditService actionAuditService;
    private final ActionProperties actionProperties;
    private final ActionRunPayloadService payloadService;
    private final ActionRunResponseMapper responseMapper;
    private final ActionResultPageMergeService resultPageMergeService;
    private final ActionRealtimePublisher realtimePublisher;
    private final ActionOutputService actionOutputService;
    private final JobRealtimePublisher jobRealtimePublisher;
    private final ActionMetrics metrics;
    private final HttpClient httpClient;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate resultImportTransactionTemplate;
    private final Map<String, ScheduledFuture<?>> pendingLexiconRebuilds = new ConcurrentHashMap<>();

    public ActionRunService(ActionProcessorDefinitionRepository definitionRepository,
                            ActionProcessorAssignmentRepository assignmentRepository,
                            ActionProcessorWorkspaceAvailabilityRepository availabilityRepository,
                            ActionRunRepository runRepository,
                            ActionRunDismissalRepository runDismissalRepository,
                            ActionRunLogEventRepository logEventRepository,
                            ActionRunPageResultRepository pageResultRepository,
                            ProjectRepository projectRepository,
                            PageRepository pageRepository,
                            PageImageRepository pageImageRepository,
                            PageXmlRepository pageXmlRepository,
                            WorkspaceAccessService workspaceAccessService,
                            GlobalAdminService globalAdminService,
                            ActionDefinitionService definitionService,
                            ActionEndpointAuthService endpointAuthService,
                            @org.springframework.beans.factory.annotation.Qualifier("importTaskExecutor") TaskExecutor importTaskExecutor,
                            @org.springframework.beans.factory.annotation.Qualifier("actionResultTaskScheduler") TaskScheduler actionResultTaskScheduler,
                            HierarchicalFileStorageService fileStorageService,
                            ThumbnailService thumbnailService,
                            WorkspaceQuotaGuardService workspaceQuotaGuardService,
                            WorkspaceQuotaRefreshService workspaceQuotaRefreshService,
                            PageXmlValidationService pageXmlValidationService,
                            PageXmlCanonicalizationService pageXmlCanonicalizationService,
                            PageXmlVersionService pageXmlVersionService,
                            PageFilterIndexService pageFilterIndexService,
                            PageIndexStatusTracker pageIndexStatusTracker,
                            SearchLexiconService searchLexiconService,
                            AnnotationReadCache annotationReadCache,
                            AnnotationProcessingService annotationProcessingService,
                            PageXmlToAnnotationParser pageXmlToAnnotationParser,
                            PageOrderService pageOrderService,
                            ActionAuditService actionAuditService,
                            ActionProperties actionProperties,
                            ActionRunPayloadService payloadService,
                            ActionRunResponseMapper responseMapper,
                            ActionResultPageMergeService resultPageMergeService,
                            ActionRealtimePublisher realtimePublisher,
                            ActionOutputService actionOutputService,
                            JobRealtimePublisher jobRealtimePublisher,
                            ActionMetrics metrics,
                            TransactionTemplate transactionTemplate) {
        this.definitionRepository = definitionRepository;
        this.assignmentRepository = assignmentRepository;
        this.availabilityRepository = availabilityRepository;
        this.runRepository = runRepository;
        this.runDismissalRepository = runDismissalRepository;
        this.logEventRepository = logEventRepository;
        this.pageResultRepository = pageResultRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.globalAdminService = globalAdminService;
        this.definitionService = definitionService;
        this.endpointAuthService = endpointAuthService;
        this.importTaskExecutor = importTaskExecutor;
        this.actionResultTaskScheduler = actionResultTaskScheduler;
        this.fileStorageService = fileStorageService;
        this.thumbnailService = thumbnailService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
        this.pageXmlValidationService = pageXmlValidationService;
        this.pageXmlCanonicalizationService = pageXmlCanonicalizationService;
        this.pageXmlVersionService = pageXmlVersionService;
        this.pageFilterIndexService = pageFilterIndexService;
        this.pageIndexStatusTracker = pageIndexStatusTracker;
        this.searchLexiconService = searchLexiconService;
        this.annotationReadCache = annotationReadCache;
        this.annotationProcessingService = annotationProcessingService;
        this.pageXmlToAnnotationParser = pageXmlToAnnotationParser;
        this.pageOrderService = pageOrderService;
        this.actionAuditService = actionAuditService;
        this.actionProperties = actionProperties;
        this.payloadService = payloadService;
        this.responseMapper = responseMapper;
        this.resultPageMergeService = resultPageMergeService;
        this.realtimePublisher = realtimePublisher;
        this.actionOutputService = actionOutputService;
        this.jobRealtimePublisher = jobRealtimePublisher;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
        this.resultImportTransactionTemplate = new TransactionTemplate(Objects.requireNonNull(
                transactionTemplate.getTransactionManager(), "Transaction manager is required"));
        this.resultImportTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
        return listExecutableProcessors(workspaceId, projectId, userId, null);
    }

    @Transactional(readOnly = true)
    public List<ActionDto.ExecutableProcessorResponse> listExecutableProcessors(String workspaceId, String projectId, String userId, ActionTarget target) {
        Project project = requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        LinkedHashMap<String, ActionDto.ExecutableProcessorResponse> executable = new LinkedHashMap<>();
        definitionRepository.findByEnabledTrueAndGlobalAvailableTrueOrderByNameAsc()
                .stream()
                .filter(definition -> target == null || definitionService.readTargetTypes(definition).contains(target))
                .forEach(definition -> executable.put(definition.getId(), toExecutableResponse(null, definition, workspaceId, project, userId)));

        assignmentRepository.findExecutableAssignments(workspaceId, projectId).stream()
                .filter(assignment -> target == null || definitionService.readTargetTypes(assignment.getProcessorDefinition()).contains(target))
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
        ActionDto.TargetSelection targetSelection = normalizeTargetSelection(request, projectId);
        requireTargetSupported(definition, targetSelection.type());
        List<Page> pages = resolveRunPagesForUpdate(projectId, targetSelection.pages().stream()
                .map(ActionDto.TargetSelectionPage::pageId)
                .toList());
        Map<String, Object> parameters = resolveRunParameters(definition, request.imageVariantSelection());
        pages = processorPages(pages, definition, parameters);
        targetSelection = restrictTargetSelection(targetSelection, pages);
        ConcurrencyDecision concurrency = evaluateConcurrency(definition, workspaceId, projectId);
        boolean dispatchImmediately = concurrency.available();
        if (!dispatchImmediately && !Boolean.TRUE.equals(request.enqueueIfBusy())) {
            throw new ActionConcurrencyLimitException(concurrency.message());
        }
        if (dispatchImmediately) {
            validateLocks(project, pages);
        }
        return createRun(
                workspaceId,
                projectId,
                project,
                definition,
                pages,
                targetSelection,
                userId,
                publicApiBaseUrl,
                parameters,
                dispatchImmediately ? Status.PENDING : Status.QUEUED,
                dispatchImmediately ? "Created" : "Queued; waiting for an available slot",
                "ACTION_RUN_START",
                Map.of(
                        "targetType", targetSelection.type().name(),
                        "queued", !dispatchImmediately
                ),
                dispatchImmediately
        );
    }

    private ActionDto.StartRunResponse createRun(String workspaceId,
                                                 String projectId,
                                                 Project project,
                                                 ActionProcessorDefinition definition,
                                                 List<Page> pages,
                                                 ActionDto.TargetSelection targetSelection,
                                                 String userId,
                                                 String publicApiBaseUrl,
                                                 Map<String, Object> parameters,
                                                 Status initialStatus,
                                                 String initialStatusMessage,
                                                 String auditAction,
                                                 Map<String, ?> auditDetails,
                                                 boolean dispatchImmediately) {
        ActionRun run = new ActionRun();
        run.setProcessorDefinition(definition);
        run.setWorkspaceId(workspaceId);
        run.setProjectId(projectId);
        run.setCreatedByUserId(userId);
        run.setStatus(initialStatus);
        run.setLockMode(definition.getLockMode());
        run.setPageIdsJson(payloadService.writeJson(pages.stream().map(Page::getId).toList()));
        run.setTargetSelectionJson(payloadService.writeJson(targetSelection));
        run.setParametersJson(payloadService.writeJson(parameters));
        run.setPublicApiBaseUrl(publicApiBaseUrl);
        String rawSecret = issueRunSecret(run);
        run.setStatusMessage(initialStatusMessage);
        run = runRepository.save(run);
        actionOutputService.createDraft(run, project);

        if (dispatchImmediately) {
            applyLocks(project, pages, run);
        }
        ActionRun savedRun = runRepository.save(run);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("pageCount", pages.size());
        details.put("targetType", targetSelection.type().name());
        if (auditDetails != null) {
            details.putAll(auditDetails);
        }
        actionAuditService.record(auditAction, "SUCCESS", userId, definition.getId(), savedRun.getId(),
                workspaceId, projectId, details);
        publishActionRunUpdatedAfterCommit(savedRun);
        if (dispatchImmediately) {
            dispatchAfterCommit(savedRun.getId(), rawSecret, publicApiBaseUrl);
        } else {
            dispatchQueuedRunsAfterCommit();
        }
        return new ActionDto.StartRunResponse(responseMapper.toRunResponse(savedRun, project.getName(), userId));
    }

    @Transactional(readOnly = true)
    public List<ActionDto.RunResponse> listRuns(String workspaceId, String projectId, String userId) {
        return listRuns(workspaceId, projectId, userId, DEFAULT_RUN_HISTORY_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ActionDto.RunResponse> listRuns(String workspaceId, String projectId, String userId, int limit) {
        Project project = requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        List<ActionRun> runs = runRepository.findByWorkspaceIdAndProjectIdOrderByCreatedDesc(
                workspaceId, projectId, PageRequest.of(0, runHistoryLimit(limit)));
        Set<String> dismissedRunIds = dismissedTerminalRunIds(userId, runs);
        Map<String, Integer> queuePositions = responseMapper.queuePositionsByRunId(runs);
        Map<String, List<String>> completedPageIds = responseMapper.completedPageIdsByRunId(runs);
        return runs.stream()
                .filter(run -> !dismissedRunIds.contains(run.getId()))
                .map(run -> responseMapper.toRunResponse(run, project.getName(), userId, queuePositions, completedPageIds))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActionDto.RunResponse> listWorkspaceRuns(String workspaceId, String userId) {
        return listWorkspaceRuns(workspaceId, userId, DEFAULT_RUN_HISTORY_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ActionDto.RunResponse> listWorkspaceRuns(String workspaceId, String userId, int limit) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        List<ActionRun> runs = runRepository.findByWorkspaceIdOrderByCreatedDesc(
                workspaceId, PageRequest.of(0, runHistoryLimit(limit)));
        Set<String> dismissedRunIds = dismissedTerminalRunIds(userId, runs);
        Map<String, String> projectLabels = projectLabelsById(workspaceId);
        Map<String, Integer> queuePositions = responseMapper.queuePositionsByRunId(runs);
        Map<String, List<String>> completedPageIds = responseMapper.completedPageIdsByRunId(runs);
        return runs.stream()
                .filter(run -> !dismissedRunIds.contains(run.getId()))
                .map(run -> responseMapper.toRunResponse(
                        run,
                        projectLabels.getOrDefault(run.getProjectId(), run.getProjectId()),
                        userId,
                        queuePositions,
                        completedPageIds
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ActionDto.RunDetailResponse getRunDetail(String workspaceId, String projectId, String runId, String userId) {
        Project project = requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ActionRun run = requireRun(workspaceId, projectId, runId);
        return responseMapper.toRunDetailResponse(run, project.getName(), userId);
    }

    @Transactional(readOnly = true)
    public ActionDto.RunDetailResponse getWorkspaceRunDetail(String workspaceId, String runId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ActionRun run = runRepository.findWithProcessorDefinitionById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Action run not found"));
        if (!workspaceId.equals(run.getWorkspaceId())) {
            throw new IllegalArgumentException("Action run not found");
        }
        return responseMapper.toRunDetailResponse(run, resolveProjectLabel(run.getProjectId()), userId);
    }

    public ActionDto.StartRunResponse retryRun(String workspaceId,
                                               String projectId,
                                               String runId,
                                               boolean enqueueIfBusy,
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
        ActionDto.TargetSelection targetSelection = payloadService.readTargetSelection(sourceRun);
        requireTargetSupported(definition, targetSelection.type());
        List<Page> pages = resolveRunPagesForUpdate(projectId, payloadService.readPageIds(sourceRun));
        Map<String, Object> parameters = payloadService.readObjectMap(sourceRun.getParametersJson());
        pages = processorPages(pages, definition, parameters);
        targetSelection = restrictTargetSelection(targetSelection, pages);
        ConcurrencyDecision concurrency = evaluateConcurrency(definition, workspaceId, projectId);
        boolean dispatchImmediately = concurrency.available();
        if (!dispatchImmediately && !enqueueIfBusy) {
            throw new ActionConcurrencyLimitException(concurrency.message());
        }
        if (dispatchImmediately) {
            validateLocks(project, pages);
        }
        return createRun(
                workspaceId,
                projectId,
                project,
                definition,
                pages,
                targetSelection,
                userId,
                publicApiBaseUrl,
                parameters,
                dispatchImmediately ? Status.PENDING : Status.QUEUED,
                dispatchImmediately ? "Created" : "Queued; waiting for an available slot",
                "ACTION_RUN_RETRY",
                Map.of(
                        "sourceRunId", sourceRun.getId(),
                        "queued", !dispatchImmediately
                ),
                dispatchImmediately
        );
    }

    @Transactional(readOnly = true)
    public List<ActionDto.AdminRunResponse> listAdminRuns(String definitionId) {
        return listAdminRuns(definitionId, DEFAULT_RUN_HISTORY_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ActionDto.AdminRunResponse> listAdminRuns(String definitionId, int limit) {
        requireGlobalAdmin();
        requireDefinition(definitionId);
        List<ActionRun> runs = runRepository.findByProcessorDefinitionIdOrderByCreatedDesc(
                definitionId, PageRequest.of(0, runHistoryLimit(limit)));
        Map<String, Integer> queuePositions = responseMapper.queuePositionsByRunId(runs);
        return runs.stream()
                .map(run -> responseMapper.toAdminRunResponse(run, queuePositions))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActionDto.AdminRunResponse> listAllAdminRuns() {
        return listAllAdminRuns(DEFAULT_RUN_HISTORY_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ActionDto.AdminRunResponse> listAllAdminRuns(int limit) {
        requireGlobalAdmin();
        List<ActionRun> runs = runRepository.findAllByOrderByCreatedDesc(PageRequest.of(0, runHistoryLimit(limit)));
        Map<String, Integer> queuePositions = responseMapper.queuePositionsByRunId(runs);
        return runs.stream()
                .map(run -> responseMapper.toAdminRunResponse(run, queuePositions))
                .toList();
    }

    public ActionDto.BulkCancelRunsResponse cancelActiveAdminRuns(String definitionId, String userId) {
        requireGlobalAdmin();
        requireDefinition(definitionId);
        List<ActionRun> runs = runRepository.findByProcessorDefinitionIdAndStatusIn(definitionId, cancelableStatuses());
        int cancelledCount = 0;
        for (ActionRun run : runs) {
            ActionDto.RunResponse result = cancelRun(run.getWorkspaceId(), run.getProjectId(), run.getId(), userId);
            if (result.status() == Status.CANCELLED || result.status() == Status.CANCEL_REQUESTED) {
                cancelledCount += 1;
            }
        }
        return new ActionDto.BulkCancelRunsResponse(cancelledCount);
    }

    @Transactional(readOnly = true)
    public ActionDto.AdminRunResponse getAdminRun(String definitionId, String runId) {
        requireGlobalAdmin();
        ActionRun run = runRepository.findWithProcessorDefinitionById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Action run not found"));
        if (!definitionId.equals(run.getProcessorDefinition().getId())) {
            throw new IllegalArgumentException("Action run not found");
        }
        return responseMapper.toAdminRunResponse(run);
    }

    public ActionDto.ClearRunsResponse clearTerminalAdminRuns(String definitionId) {
        requireGlobalAdmin();
        requireDefinition(definitionId);
        List<ActionRun> terminalRuns = runRepository.findByProcessorDefinitionIdAndStatusIn(definitionId, terminalStatuses());
        deleteRunsWithLogs(terminalRuns);
        return new ActionDto.ClearRunsResponse(terminalRuns.size());
    }

    public ActionDto.ClearRunsResponse clearProjectRunHistory(String workspaceId, String projectId, String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        List<ActionRun> historyRuns = runRepository.findByWorkspaceIdAndProjectIdAndStatusIn(
                workspaceId,
                projectId,
                List.of(Status.COMPLETED, Status.FAILED)
        );
        deleteRunsWithLogs(historyRuns);
        actionAuditService.record("ACTION_RUN_HISTORY_CLEAR", "SUCCESS", userId, null, null,
                workspaceId, projectId, Map.of("deletedCount", historyRuns.size()));
        return new ActionDto.ClearRunsResponse(historyRuns.size());
    }

    public void dismissRun(String workspaceId, String projectId, String runId, String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ActionRun run = requireRun(workspaceId, projectId, runId);
        if (!terminalStatuses().contains(run.getStatus())) {
            throw new IllegalStateException("Only completed Action runs can be dismissed");
        }
        dismissRuns(List.of(run), userId);
    }

    public ActionDto.ClearRunsResponse dismissProjectRunHistory(String workspaceId, String projectId, String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        List<ActionRun> terminalRuns = runRepository.findByWorkspaceIdAndProjectIdAndStatusIn(
                workspaceId,
                projectId,
                terminalStatuses()
        );
        int dismissed = dismissRuns(terminalRuns, userId);
        return new ActionDto.ClearRunsResponse(dismissed);
    }

    public ActionDto.ClearRunsResponse dismissWorkspaceRunHistory(String workspaceId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        List<ActionRun> terminalRuns = runRepository.findByWorkspaceIdAndStatusIn(workspaceId, terminalStatuses());
        int dismissed = dismissRuns(terminalRuns, userId);
        return new ActionDto.ClearRunsResponse(dismissed);
    }

    @Scheduled(fixedDelayString = "${larex.actions.watchdog-interval-ms:60000}")
    public void reconcileStaleRuns() {
        LocalDateTime now = LocalDateTime.now();
        expireDispatchingRuns(now.minusMinutes(Math.max(1, actionProperties.getTimeout().getDispatchMinutes())));
        expireHeartbeatRuns(now.minusMinutes(Math.max(1, actionProperties.getTimeout().getHeartbeatMinutes())));
        expireCancellationRuns(now.minusMinutes(Math.max(1, actionProperties.getTimeout().getHeartbeatMinutes())));
        pruneTerminalRuns(now.minusDays(Math.max(1, actionProperties.getRetention().getTerminalDays())));
        dispatchQueuedRunsAsync();
    }

    public ActionDto.RunResponse cancelRun(String workspaceId, String projectId, String runId, String userId) {
        requireProject(workspaceId, projectId);
        ActionRun run = requireRun(workspaceId, projectId, runId);
        requireCancelAccess(workspaceId, run, userId);
        ActionRun saved = cancelRunInternal(run, userId, "ACTION_RUN_CANCEL");
        return responseMapper.toRunResponse(saved, resolveProjectLabel(saved.getProjectId()), userId);
    }

    @Transactional(readOnly = true)
    public ActionDto.MachineInputResponse buildMachineInput(String runId, String authorizationHeader, String publicApiBaseUrl) {
        ActionRun run = authenticateRun(runId, authorizationHeader);
        ActionProcessorDefinition definition = run.getProcessorDefinition();
        List<String> pageIds = payloadService.readPageIds(run);
        Map<String, Object> parameters = payloadService.readObjectMap(run.getParametersJson());
        ActionDto.ImageVariantSelection imageVariantSelection = payloadService.readImageVariantSelection(parameters);

        Map<String, List<PageImage>> imagesByPage = definition.isAcceptsImages()
                ? pageImageRepository.findByPageIdIn(pageIds).stream().collect(Collectors.groupingBy(image -> image.getPage().getId()))
                : Map.of();
        Map<String, PageXml> xmlByPage = definition.isAcceptsXml()
                ? pageXmlRepository.findByPage_IdIn(pageIds).stream()
                        .collect(Collectors.toMap(xml -> xml.getPage().getId(), xml -> xml))
                : Map.of();

        List<ActionDto.MachinePageInput> pages = pageOrderService.sortPages(pageRepository.findByIdInAndProjectId(pageIds, run.getProjectId())).stream()
                .map(page -> toMachinePageInput(page, imagesByPage, xmlByPage, imageVariantSelection, definition, publicApiBaseUrl, runId))
                .flatMap(Optional::stream)
                .toList();
        Set<String> includedPageIds = pages.stream()
                .map(ActionDto.MachinePageInput::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new ActionDto.MachineInputResponse(
                ACTION_PROTOCOL_VERSION,
                run.getId(),
                definition.getProcessorKey(),
                run.getProjectId(),
                payloadService.processorParameters(parameters),
                pages,
                buildMachineTargetSelection(run, includedPageIds),
                imageVariantSelection,
                new ActionDto.MachineCapabilities(true, true),
                run.isCancelRequested()
        );
    }

    @Transactional(readOnly = true)
    public MachineFile resolveMachineFile(String runId, String authorizationHeader, String type, String fileId) {
        ActionRun run = authenticateRun(runId, authorizationHeader);
        List<String> pageIds = payloadService.readPageIds(run);
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
            run.setProgressPercent(Math.max(
                    Math.min(run.getProgressPercent(), 99),
                    Math.max(0, Math.min(99, request.progressPercent()))
            ));
        }
        if (request.statusMessage() != null) {
            run.setStatusMessage(limit(redactProcessorSecrets(request.statusMessage()), 2000));
        }
        appendLog(run, request.logLevel(), request.log());
        if ("cancelled".equalsIgnoreCase(request.status())) {
            int cancelledProgress = request.progressPercent() == null
                    ? run.getProgressPercent()
                    : Math.max(0, Math.min(99, request.progressPercent()));
            run.setProgressPercent(Math.max(Math.min(run.getProgressPercent(), 99), cancelledProgress));
            run.setStatusMessage(limit(redactProcessorSecrets(
                    request.statusMessage() == null || request.statusMessage().isBlank() ? "Cancelled" : request.statusMessage()
            ), 2000));
            finalizeCancelledRun(run, run.getStatusMessage());
            runRepository.save(run);
            publishActionRunUpdatedAfterCommit(run);
            actionAuditService.record("ACTION_RUN_HEARTBEAT_CANCELLED", "SUCCESS", run.getCreatedByUserId(),
                    run.getProcessorDefinition().getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(), Map.of());
            dispatchQueuedRunsAfterCommit();
            return new ActionDto.HeartbeatResponse(true);
        }
        if ("failed".equalsIgnoreCase(request.status())) {
            run.setStatus(Status.FAILED);
            run.setErrorMessage(limit(redactProcessorSecrets(request.errorMessage()), 4000));
            run.setCompletedAt(LocalDateTime.now());
            expireRunSecret(run);
            releaseLocks(run);
            actionOutputService.discardDraft(run.getId());
            actionAuditService.record("ACTION_RUN_HEARTBEAT_FAILED", "FAILURE", run.getCreatedByUserId(),
                    run.getProcessorDefinition().getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(),
                    Map.of("error", limit(Objects.toString(redactProcessorSecrets(request.errorMessage()), ""), 1000)));
        }
        runRepository.save(run);
        publishActionRunUpdatedAfterCommit(run);
        if (run.getStatus() == Status.FAILED || run.getStatus() == Status.CANCELLED) {
            dispatchQueuedRunsAfterCommit();
        }
        return new ActionDto.HeartbeatResponse(run.isCancelRequested());
    }

    @Transactional(rollbackFor = Exception.class)
    public ActionDto.RunResponse receiveResults(String runId,
                                                String authorizationHeader,
                                                ActionDto.ResultManifest manifest,
                                                MultiValueMap<String, MultipartFile> files) throws IOException {
        ActionRun run = authenticateResultRunForUpdate(runId, authorizationHeader);
        if (run.getStatus() == Status.COMPLETED || run.getStatus() == Status.FAILED) {
            metrics.recordTerminalResultReplay();
            return responseMapper.toRunResponse(run);
        }
        if (run.isCancelRequested() || run.getStatus() == Status.CANCELLED || run.getStatus() == Status.CANCEL_REQUESTED) {
            throw new SecurityException("This run has been cancelled");
        }
        if (manifest.protocolVersion() != null && manifest.protocolVersion() != ACTION_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported Action result protocol version");
        }

        List<String> pageIds = payloadService.readPageIds(run);
        Set<String> pageIdSet = new LinkedHashSet<>(pageIds);
        List<ActionDto.ResultFile> resultFiles = manifest.files() == null ? List.of() : manifest.files();
        List<ActionDto.ResultPatch> resultPatches = manifest.patches() == null ? List.of() : manifest.patches();
        if (!resultPatches.isEmpty()) {
            throw new IllegalArgumentException("Result patches are no longer supported; return PAGE XML as an XML result file");
        }
        String resultStatus = manifest.status() == null || manifest.status().isBlank()
                ? "completed"
                : normalize(manifest.status());
        if (!Set.of("running", "completed", "failed").contains(resultStatus)) {
            throw new IllegalArgumentException("Unsupported Action result status: " + manifest.status());
        }

        if ("running".equals(resultStatus)) {
            return receiveIncrementalPageResult(run, manifest, resultFiles, files, pageIdSet);
        }

        boolean hasIncrementalResults = pageResultRepository.existsByRunId(run.getId());
        if (hasIncrementalResults) {
            if (!resultFiles.isEmpty()) {
                throw new IllegalStateException("Bulk result files cannot be uploaded after incremental page results");
            }
            validateResultManifest(resultFiles, files);
            if ("failed".equals(resultStatus)) {
                return finalizeResultRun(run, Status.FAILED, manifest.message(), 0);
            }
            long completedPages = pageResultRepository.countByRunId(run.getId());
            if (completedPages != pageIds.size()) {
                throw new IllegalStateException("Cannot complete incremental Action run before every page result was submitted");
            }
            return finalizeResultRun(run, Status.COMPLETED, manifest.message(), 0);
        }

        return receiveLegacyBulkResults(run, manifest, resultFiles, files, pageIdSet, resultStatus);
    }

    private ActionDto.RunResponse receiveIncrementalPageResult(ActionRun run,
                                                               ActionDto.ResultManifest manifest,
                                                               List<ActionDto.ResultFile> resultFiles,
                                                               MultiValueMap<String, MultipartFile> files,
                                                               Set<String> pageIds) throws IOException {
        String pageId = manifest.pageId();
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("Incremental page results require pageId");
        }
        if (!pageIds.contains(pageId)) {
            throw new SecurityException("Result page is outside this run scope");
        }
        if (pageResultRepository.findByRunIdAndPageId(run.getId(), pageId).isPresent()) {
            metrics.recordDuplicatePageSubmission();
            return responseMapper.toRunResponse(run);
        }
        if (resultFiles.stream().anyMatch(file -> !pageId.equals(file.pageId()))) {
            throw new IllegalArgumentException("Every incremental result file must match manifest pageId");
        }

        validateResultManifest(resultFiles, files);
        run.setStatus(Status.IMPORTING_RESULTS);
        run.setStatusMessage("Importing page result");
        runRepository.saveAndFlush(run);
        long reservedBytes = 0L;
        long importStartedNanos = System.nanoTime();
        try {
            reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    run.getWorkspaceId(),
                    resultUploadBytes(resultFiles, files),
                    "larex-action-result"
            );
            ImportResult imported = importResultFiles(run, resultFiles, files, pageIds);
            ActionRunPageResult pageResult = new ActionRunPageResult();
            pageResult.setRun(run);
            pageResult.setPageId(pageId);
            pageResult.setResultSummaryJson(payloadService.writeJson(imported.stored()));
            pageResultRepository.saveAndFlush(pageResult);

            List<Map<String, Object>> aggregate = new ArrayList<>(payloadService.readObjectList(run.getResultSummaryJson()));
            aggregate.addAll(imported.stored());
            long completedPages = pageResultRepository.countByRunId(run.getId());
            int pageProgress = Math.min(99, (int) Math.floor((completedPages * 100.0) / pageIds.size()));
            run.setResultSummaryJson(payloadService.writeJson(aggregate));
            run.setStatus(Status.RUNNING);
            run.setStatusMessage(limit(redactProcessorSecrets(
                    manifest.message() == null || manifest.message().isBlank()
                            ? "Imported page " + completedPages + "/" + pageIds.size()
                            : manifest.message()), 2000));
            run.setProgressPercent(Math.max(Math.min(run.getProgressPercent(), 99), pageProgress));
            releasePageLock(run, pageId);
            ActionRun savedRun = runRepository.save(run);
            schedulePageReindexAfterCommit(run.getProjectId(), imported.xmlPageIds());
            actionAuditService.record("ACTION_RUN_PAGE_RESULT_IMPORTED", "SUCCESS",
                    run.getCreatedByUserId(), run.getProcessorDefinition().getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(),
                    Map.of("pageId", pageId, "resultCount", imported.stored().size()));
            publishActionPageResultAfterCommit(savedRun, pageId, imported.resultTypes());
            metrics.recordPageImport(importStartedNanos, resultFiles.size());
            return responseMapper.toRunResponse(savedRun);
        } catch (IOException | RuntimeException e) {
            scheduleResultImportFailureAfterRollback(run.getId(), describeException(e));
            throw e;
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(run.getWorkspaceId(), reservedBytes);
        }
    }

    private ActionDto.RunResponse receiveLegacyBulkResults(ActionRun run,
                                                           ActionDto.ResultManifest manifest,
                                                           List<ActionDto.ResultFile> resultFiles,
                                                           MultiValueMap<String, MultipartFile> files,
                                                           Set<String> pageIds,
                                                           String resultStatus) throws IOException {
        validateResultManifest(resultFiles, files);
        run.setStatus(Status.IMPORTING_RESULTS);
        run.setStatusMessage("Importing results");
        runRepository.saveAndFlush(run);
        long reservedBytes = 0L;
        try {
            reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    run.getWorkspaceId(), resultUploadBytes(resultFiles, files), "larex-action-result");
            ImportResult imported = importResultFiles(run, resultFiles, files, pageIds);
            run.setResultSummaryJson(payloadService.writeJson(imported.stored()));
            schedulePageReindexAfterCommit(run.getProjectId(), imported.xmlPageIds());
            return finalizeResultRun(run, "failed".equals(resultStatus) ? Status.FAILED : Status.COMPLETED,
                    manifest.message(), imported.stored().size());
        } catch (IOException | RuntimeException e) {
            scheduleResultImportFailureAfterRollback(run.getId(), describeException(e));
            throw e;
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(run.getWorkspaceId(), reservedBytes);
        }
    }

    private ImportResult importResultFiles(ActionRun run,
                                           List<ActionDto.ResultFile> resultFiles,
                                           MultiValueMap<String, MultipartFile> files,
                                           Set<String> pageIds) throws IOException {
        ActionProcessorDefinition definition = run.getProcessorDefinition();
        List<Map<String, Object>> stored = new ArrayList<>();
        Set<String> xmlPageIds = new LinkedHashSet<>();
        Set<String> resultTypes = new LinkedHashSet<>();
        for (ActionDto.ResultFile resultFile : resultFiles) {
            MultipartFile file = resolveMultipart(files, resultFile.fieldName());
            String type = normalize(resultFile.type());
            if ("xml".equals(type)) {
                requireResultPageInScope(resultFile.pageId(), pageIds);
                if (!definition.isOutputsXml()) {
                    throw new SecurityException("XML outputs are not declared for this processor");
                }
                stored.add(storeXmlResult(run, resultFile, file));
                xmlPageIds.add(resultFile.pageId());
                resultTypes.add("xml");
            } else if ("image".equals(type) || "images".equals(type)) {
                requireResultPageInScope(resultFile.pageId(), pageIds);
                if (!definition.isOutputsImages()) {
                    throw new SecurityException("Image outputs are not declared for this processor");
                }
                stored.add(storeImageResult(run, resultFile, file));
                resultTypes.add("image");
            } else if ("file".equals(type) || "files".equals(type)) {
                if (!definition.isOutputsFiles()) {
                    throw new SecurityException("File outputs are not declared for this processor");
                }
                if (resultFile.pageId() != null && !pageIds.contains(resultFile.pageId())) {
                    throw new SecurityException("Result page is outside this run scope");
                }
                stored.add(actionOutputService.storeResultFile(run, resultFile, file));
                resultTypes.add("file");
            } else {
                throw new IllegalArgumentException("Unsupported result type: " + resultFile.type());
            }
        }
        return new ImportResult(stored, xmlPageIds, resultTypes);
    }

    private void requireResultPageInScope(String pageId, Set<String> pageIds) {
        if (pageId == null || !pageIds.contains(pageId)) {
            throw new SecurityException("Result page is outside this run scope");
        }
    }

    private ActionDto.RunResponse finalizeResultRun(ActionRun run, Status status, String message, int resultCount) {
        run.setStatus(status);
        String safeMessage = limit(redactProcessorSecrets(message), 2000);
        run.setStatusMessage(safeMessage);
        if (status == Status.FAILED) {
            run.setErrorMessage(limit(
                    safeMessage == null || safeMessage.isBlank() ? "Processor reported a failed result" : safeMessage,
                    4000
            ));
        }
        run.setProgressPercent(status == Status.COMPLETED ? 100 : run.getProgressPercent());
        run.setCompletedAt(LocalDateTime.now());
        if (status == Status.COMPLETED) {
            actionOutputService.finalizeDraft(run.getId(), run.getCompletedAt());
        } else {
            actionOutputService.discardDraft(run.getId());
        }
        expireRunSecret(run);
        releaseLocks(run);
        ActionRun savedRun = runRepository.save(run);
        actionAuditService.record(status == Status.COMPLETED ? "ACTION_RUN_COMPLETE" : "ACTION_RUN_RESULT_FAILED",
                status == Status.COMPLETED ? "SUCCESS" : "FAILURE",
                run.getCreatedByUserId(), run.getProcessorDefinition().getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(),
                Map.of("resultCount", resultCount));
        publishActionRunUpdatedAfterCommit(savedRun);
        dispatchQueuedRunsAfterCommit();
        return responseMapper.toRunResponse(savedRun);
    }

    private Map<String, Object> storeXmlResult(ActionRun run, ActionDto.ResultFile resultFile, MultipartFile file) throws IOException {
        validateResultFileSize(file);
        var validation = pageXmlValidationService.validatePageXml(file.getResource());
        if (!validation.valid()) {
            throw new IllegalArgumentException("Result XML is invalid");
        }
        ActionDto.TargetSelection targetSelection = payloadService.readTargetSelection(run);
        if (targetSelection.type() != ActionTarget.PAGE) {
            return storeScopedXmlResult(run, resultFile, file);
        }
        Page page = pageRepository.findByIdAndProjectId(resultFile.pageId(), run.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        String originalName = chooseFileName(resultFile.fileName(), file.getOriginalFilename(), page.getName() + ".xml");
        Optional<PageXml> existing = pageXmlRepository.findByPage_Id(page.getId());
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
            pageXml = existing.orElseThrow();
            String previousPath = pageXml.getFilePath();
            pageXmlVersionService.createVersion(pageXml.getId(), run.getCreatedByUserId(), "Before LAREX Action " + run.getId());
            pageXml.setFileName(storedFile.originalFilename());
            pageXml.setFilePath(storedFile.storagePath());
            pageXml.setMimeType(storedFile.mimeType());
            pageXml.setFileSize(storedFile.sizeBytes());
            pageXml.setVariant("original");
            pageXml.setBaseName(baseName);
            pageXml.setSchema(XmlSchema.PAGE_XML);
            pageXml.setSchemaVersion(validation.pageVersion());
            pageXml.setPage(page);
            annotationReadCache.evict(pageXml.getId());
            deleteStoredFilesAfterCommit(List.of(previousPath));
        }

        pageXml = pageXmlRepository.save(pageXml);
        pageXmlCanonicalizationService.canonicalizeAtIngest(pageXml, run.getCreatedByUserId(), "LAREX Action " + run.getId());
        workspaceQuotaRefreshService.scheduleUsageRefresh(run.getWorkspaceId());
        return Map.of("type", "xml", "pageId", page.getId(), "fileId", pageXml.getId(), "variant", pageXml.getVariant());
    }

    private Map<String, Object> storeScopedXmlResult(ActionRun run, ActionDto.ResultFile resultFile, MultipartFile file) throws IOException {
        ActionDto.TargetSelection targetSelection = payloadService.readTargetSelection(run);
        PageXml existingXml = primaryPageXml(resultFile.pageId());
        PageDto existing = annotationProcessingService.parseXmlToAnnotation(existingXml.getId());
        PageDto incoming = parseResultPageXml(file, existingXml);
        PageDto merged;
        if (targetSelection.type() == ActionTarget.REGION) {
            Set<String> selectedRegionIds = targetSelection.pages().stream()
                    .filter(page -> resultFile.pageId().equals(page.pageId()))
                    .flatMap(page -> safeList(page.regionIds()).stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (selectedRegionIds.isEmpty()) {
                throw new SecurityException("Scoped XML result has no selected region target scope");
            }
            merged = resultPageMergeService.replaceTargetRegions(existing, incoming, selectedRegionIds);
        } else if (targetSelection.type() == ActionTarget.TEXT_LINE) {
            Set<String> selectedTextLineIds = targetSelection.pages().stream()
                    .filter(page -> resultFile.pageId().equals(page.pageId()))
                    .flatMap(page -> safeList(page.textLineIds()).stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (selectedTextLineIds.isEmpty()) {
                throw new SecurityException("Scoped XML result has no selected textline target scope");
            }
            merged = resultPageMergeService.replaceTargetTextLines(existing, incoming, selectedTextLineIds);
        } else {
            merged = incoming;
        }
        annotationProcessingService.saveAnnotationToXml(existingXml.getId(), merged, run.getCreatedByUserId());
        workspaceQuotaRefreshService.scheduleUsageRefresh(run.getWorkspaceId());
        return Map.of(
                "type", "xml",
                "scope", targetSelection.type().name(),
                "pageId", resultFile.pageId(),
                "fileId", existingXml.getId()
        );
    }

    private void schedulePageReindexAfterCommit(String projectId, Set<String> pageIds) {
        if (pageIds == null || pageIds.isEmpty()) {
            return;
        }
        Set<String> ids = new LinkedHashSet<>(pageIds);
        Runnable task = () -> {
            for (String pageId : ids) {
                boolean acquiredIndexingSlot = pageIndexStatusTracker.markIndexingIfAbsent(pageId);
                String finalStatus = "UNINDEXED";
                try {
                    if (acquiredIndexingSlot) {
                        publishIndexUpdate(projectId, pageId, "INDEXING");
                    }
                    Page page = pageRepository.findById(pageId).orElse(null);
                    if (page != null) {
                        pageFilterIndexService.indexPageFromXml(page);
                        finalStatus = "INDEXED";
                    }
                } catch (Exception e) {
                    log.warn("Failed to index page {} after LAREX Action result import: {}", pageId, e.getMessage(), e);
                } finally {
                    if (acquiredIndexingSlot) {
                        pageIndexStatusTracker.clearIndexing(pageId);
                        publishIndexUpdate(projectId, pageId, finalStatus);
                    }
                }
            }
            scheduleProjectLexiconRebuild(projectId);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeResultImportTaskWithBackpressure(task, "page reindex", true);
                }
            });
        } else {
            executeResultImportTaskWithBackpressure(task, "page reindex", true);
        }
    }

    private void publishIndexUpdate(String projectId, String pageId, String status) {
        jobRealtimePublisher.publish("PAGE_INDEX", pageId, null, projectId, status, null);
    }

    private void scheduleProjectLexiconRebuild(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        pendingLexiconRebuilds.compute(projectId, (id, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            return actionResultTaskScheduler.schedule(() -> {
                pendingLexiconRebuilds.remove(id);
                try {
                    searchLexiconService.rebuildProjectLexicon(id);
                } catch (Exception e) {
                    log.warn("Failed to rebuild search lexicon for project {} after LAREX Action result import: {}",
                            id, e.getMessage(), e);
                }
            }, Instant.now().plusMillis(500));
        });
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
                    executeResultImportTaskWithBackpressure(task, "replaced file cleanup", false);
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
                        executeResultImportTaskWithBackpressure(task, "result import failure persistence", true);
                    }
                }
            });
        } else {
            task.run();
        }
    }

    private void executeResultImportTaskWithBackpressure(Runnable task,
                                                         String taskDescription,
                                                         boolean requiresNewTransaction) {
        try {
            importTaskExecutor.execute(task);
        } catch (TaskRejectedException rejected) {
            log.warn("Import task executor saturated while scheduling LAREX Action {}; running on the caller thread",
                    taskDescription);
            try {
                if (requiresNewTransaction) {
                    resultImportTransactionTemplate.executeWithoutResult(status -> task.run());
                } else {
                    task.run();
                }
            } catch (RuntimeException taskFailure) {
                log.error("LAREX Action {} failed while running on the caller thread: {}",
                        taskDescription, describeException(taskFailure), taskFailure);
            }
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
            actionOutputService.discardDraft(run.getId());
            runRepository.save(run);
            publishActionRunUpdatedAfterCommit(run);
            actionAuditService.record("ACTION_RUN_RESULT_IMPORT_FAILED", "FAILURE", run.getCreatedByUserId(),
                    run.getProcessorDefinition().getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(),
                    Map.of("error", limit(failureMessage, 1000)));
            dispatchQueuedRunsAsync();
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

    private PageDto parseResultPageXml(MultipartFile file, PageXml existingXml) throws IOException {
        Path tempPath = Files.createTempFile("larex-action-layout-", ".xml");
        try {
            file.transferTo(tempPath);
            return pageXmlToAnnotationParser.parse(tempPath, existingXml);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private PageXml primaryPageXml(String pageId) {
        return pageXmlRepository.findByPage_Id(pageId)
                .filter(xml -> xml.getSchema() == XmlSchema.PAGE_XML)
                .orElseThrow(() -> new IllegalArgumentException("No PAGE XML found for page " + pageId));
    }

    private void dispatchQueuedRunsAsync() {
        importTaskExecutor.execute(() -> {
            for (String runId : runRepository.findIdsByStatusOrderByCreatedAsc(Status.QUEUED)) {
                try {
                    tryActivateQueuedRun(runId);
                } catch (RuntimeException error) {
                    log.warn("Failed to promote queued LAREX Action run {}: {}", runId, describeException(error), error);
                }
            }
        });
    }

    private void dispatchQueuedRunsAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchQueuedRunsAsync();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchQueuedRunsAsync();
            }
        });
    }

    private void tryActivateQueuedRun(String runId) {
        transactionTemplate.executeWithoutResult(status -> {
            if (runRepository.claimQueuedRunId(runId).isEmpty()) {
                return;
            }

            ActionRun run = runRepository.findWithProcessorDefinitionByIdForUpdate(runId).orElse(null);
            if (run == null || run.getStatus() != Status.QUEUED) {
                return;
            }
            if (run.isCancelRequested() || run.getStatus() == Status.CANCELLED) {
                return;
            }

            ActionProcessorDefinition definition = definitionRepository.findByIdForUpdate(run.getProcessorDefinition().getId())
                    .orElseThrow(() -> new IllegalStateException("Action processor definition not found"));
            run.setProcessorDefinition(definition);

            Project project = requireProjectForUpdate(run.getWorkspaceId(), run.getProjectId());
            List<Page> pages = resolveRunPagesForUpdate(run.getProjectId(), payloadService.readPageIds(run));
            ConcurrencyDecision concurrency = evaluateConcurrency(definition, run.getWorkspaceId(), run.getProjectId());
            if (!concurrency.available() || hasBlockingLocks(project, pages)) {
                return;
            }
            if (run.getPublicApiBaseUrl() == null || run.getPublicApiBaseUrl().isBlank()) {
                run.setStatus(Status.FAILED);
                run.setStatusMessage("Queued run failed");
                run.setErrorMessage("Queued Action run is missing its public API base URL");
                run.setCompletedAt(LocalDateTime.now());
                expireRunSecret(run);
                actionOutputService.discardDraft(run.getId());
                runRepository.save(run);
                publishActionRunUpdatedAfterCommit(run);
                actionAuditService.record("ACTION_RUN_QUEUE_FAILED", "FAILURE", run.getCreatedByUserId(),
                        run.getProcessorDefinition().getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(),
                        Map.of("error", "Queued Action run is missing its public API base URL"));
                return;
            }

            String rawSecret = issueRunSecret(run);
            run.setStatus(Status.PENDING);
            run.setStatusMessage("Created");
            applyLocks(project, pages, run);
            ActionRun savedRun = runRepository.saveAndFlush(run);
            publishActionRunUpdatedAfterCommit(savedRun);
            actionAuditService.record("ACTION_RUN_QUEUE_DEQUEUED", "SUCCESS", run.getCreatedByUserId(),
                    definition.getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(),
                    Map.of("pageCount", pages.size()));
            dispatchAfterCommit(savedRun.getId(), rawSecret, savedRun.getPublicApiBaseUrl());
        });
    }

    private void dispatchAsync(String runId, String rawSecret, String publicApiBaseUrl) {
        dispatchAttemptAsync(runId, rawSecret, publicApiBaseUrl, 1,
                Math.max(1, actionProperties.getDispatch().getMaxAttempts()));
    }

    private void dispatchAttemptAsync(String runId, String rawSecret, String publicApiBaseUrl, int attempt, int attempts) {
        importTaskExecutor.execute(() -> {
            try {
                dispatch(runId, rawSecret, publicApiBaseUrl, attempt, attempts);
            } catch (Exception error) {
                log.warn("Failed to dispatch LAREX Action run {} on attempt {}/{}: {}",
                        runId, attempt, attempts, describeException(error), error);
                if (attempt < attempts && !isRunCancelled(runId)) {
                    actionResultTaskScheduler.schedule(
                            () -> dispatchAttemptAsync(runId, rawSecret, publicApiBaseUrl, attempt + 1, attempts),
                            java.time.Instant.now().plusMillis(Math.max(0, actionProperties.getDispatch().getRetryBackoffMs()))
                    );
                    return;
                }
                markDispatchFailed(runId, error);
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

    private void dispatch(String runId, String rawSecret, String publicApiBaseUrl, int attempt, int attempts) throws IOException, InterruptedException {
        ActionRun run = runRepository.findWithProcessorDefinitionById(runId)
                .orElseThrow(() -> new IllegalStateException("Action run not found"));
        if (run.getStatus() == Status.CANCELLED) {
            dispatchQueuedRunsAsync();
            return;
        }
        if (run.isCancelRequested() || run.getStatus() == Status.CANCEL_REQUESTED) {
            finalizeCancelledRun(run, "Cancelled");
            runRepository.save(run);
            publishActionRunUpdatedAfterCommit(run);
            dispatchQueuedRunsAsync();
            return;
        }
        ActionProcessorDefinition definition = run.getProcessorDefinition();
        Map<String, Object> runParameters = payloadService.readObjectMap(run.getParametersJson());
        ActionDto.ImageVariantSelection imageVariantSelection = payloadService.readImageVariantSelection(runParameters);
        List<String> processorPageIds = processorPageIds(run, definition, imageVariantSelection);
        Set<String> processorPageIdSet = new LinkedHashSet<>(processorPageIds);
        run.setStatus(Status.DISPATCHING);
        run.setStatusMessage(attempts > 1 ? "Dispatching (attempt " + attempt + "/" + attempts + ")" : "Dispatching");
        runRepository.save(run);
        publishActionRunUpdatedAfterCommit(run);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", run.getId());
        payload.put("protocolVersion", ACTION_PROTOCOL_VERSION);
        payload.put("processorId", definition.getProcessorKey());
        payload.put("workspaceId", run.getWorkspaceId());
        payload.put("projectId", run.getProjectId());
        payload.put("pageIds", processorPageIds);
        payload.put("targetSelection", buildMachineTargetSelection(run, processorPageIdSet));
        payload.put("parameters", payloadService.processorParameters(runParameters));
        payload.put("imageVariantSelection", imageVariantSelection);
        payload.put("capabilities", Map.of("incrementalPageResults", true, "customFileResults", true));
        payload.put("secret", rawSecret);
        payload.put("pullUrl", publicApiBaseUrl + "/public/actions/runs/" + run.getId() + "/input");
        payload.put("heartbeatUrl", publicApiBaseUrl + "/public/actions/runs/" + run.getId() + "/heartbeat");
        payload.put("resultUrl", publicApiBaseUrl + "/public/actions/runs/" + run.getId() + "/results");

        definitionService.requireEndpointUrlAllowed(definition.getEndpointUrl());
        URI endpointUri = URI.create(definition.getEndpointUrl());
        String body = payloadService.writeJson(payload);
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
        if (run.getStatus() == Status.CANCELLED) {
            dispatchQueuedRunsAsync();
            return;
        }
        if (run.isCancelRequested() || run.getStatus() == Status.CANCEL_REQUESTED) {
            run.setStatus(Status.CANCEL_REQUESTED);
            run.setStatusMessage("Cancellation requested");
            runRepository.save(run);
            publishActionRunUpdatedAfterCommit(run);
            return;
        }
        run.setStatus(Status.RUNNING);
        run.setStatusMessage("Dispatched");
        run.setLastHeartbeatAt(LocalDateTime.now());
        runRepository.save(run);
        publishActionRunUpdatedAfterCommit(run);
        actionAuditService.record("ACTION_RUN_DISPATCH", "SUCCESS", run.getCreatedByUserId(), definition.getId(), run.getId(),
                run.getWorkspaceId(), run.getProjectId(), Map.of("attempt", attempt));
    }

    private void markDispatchFailed(String runId, Exception e) {
        ActionRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        if (run.getStatus() == Status.CANCELLED) {
            return;
        }
        if (run.isCancelRequested() || run.getStatus() == Status.CANCEL_REQUESTED) {
            run.setStatus(Status.CANCEL_REQUESTED);
            run.setStatusMessage("Cancellation requested");
            runRepository.save(run);
            publishActionRunUpdatedAfterCommit(run);
            return;
        }
        run.setStatus(Status.FAILED);
        run.setErrorMessage(limit(describeException(e), 4000));
        run.setStatusMessage("Dispatch failed");
        run.setCompletedAt(LocalDateTime.now());
        expireRunSecret(run);
        releaseLocks(run);
        actionOutputService.discardDraft(run.getId());
        runRepository.save(run);
        publishActionRunUpdatedAfterCommit(run);
        actionAuditService.record("ACTION_RUN_DISPATCH_FAILED", "FAILURE", run.getCreatedByUserId(), run.getProcessorDefinition().getId(), run.getId(),
                run.getWorkspaceId(), run.getProjectId(), Map.of("error", limit(describeException(e), 1000)));
        dispatchQueuedRunsAsync();
    }

    private boolean isRunCancelled(String runId) {
        return runRepository.findById(runId)
                .map(run -> run.isCancelRequested() || run.getStatus() == Status.CANCELLED)
                .orElse(true);
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
        List<Page> pages = pageRepository.findAllByIdIn(payloadService.readPageIds(run));
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

    private void releasePageLock(ActionRun run, String pageId) {
        Page page = pageRepository.findByIdAndProjectId(pageId, run.getProjectId()).orElse(null);
        if (page == null || !run.getId().equals(page.getLockedByActionRunId())) {
            return;
        }
        page.setLocked(false);
        page.setLockedReason(null);
        page.setLockedByActionRunId(null);
        page.setLockedAt(null);
        pageRepository.save(page);
    }

    private void publishActionRunUpdatedAfterCommit(ActionRun run) {
        realtimePublisher.publishRunUpdated(run);
    }

    private void publishActionPageResultAfterCommit(ActionRun run, String pageId, Set<String> resultTypes) {
        realtimePublisher.publishPageResult(run, pageId, resultTypes);
    }

    private void validateLocks(Project project, List<Page> pages) {
        if (project.isLocked()) {
            throw new IllegalStateException(project.getLockedReason() == null ? "Project is locked" : project.getLockedReason());
        }
        for (Page page : pages) {
            if (page.isEffectivelyLocked()) {
                throw new IllegalStateException("Page is locked: " + page.getName());
            }
        }
    }

    private boolean hasBlockingLocks(Project project, List<Page> pages) {
        if (project.isLocked()) {
            return true;
        }
        return pages.stream().anyMatch(Page::isEffectivelyLocked);
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
        return pageOrderService.sortPages(pages);
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
        return pageOrderService.sortPages(pages);
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

    private String issueRunSecret(ActionRun run) {
        String rawSecret = "lrx_act_" + generateOpaqueToken(32);
        run.setSecretHash(sha256(rawSecret));
        run.setSecretPrefix(rawSecret.substring(0, Math.min(rawSecret.length(), 12)));
        run.setSecretExpiresAt(LocalDateTime.now().plusMinutes(definitionService.defaultTokenTtlMinutes()));
        return rawSecret;
    }

    private ConcurrencyDecision evaluateConcurrency(ActionProcessorDefinition definition, String workspaceId, String projectId) {
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
        return new ConcurrencyDecision(active < maxActiveRuns, active, maxActiveRuns, scope);
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
                List.of(Status.RUNNING, Status.IMPORTING_RESULTS),
                cutoff
        );
        for (ActionRun run : stale) {
            failRunFromWatchdog(run, "Action heartbeat timed out");
        }
    }

    private void expireCancellationRuns(LocalDateTime cutoff) {
        List<ActionRun> stale = runRepository.findByStatusInAndUpdatedBefore(List.of(Status.CANCEL_REQUESTED), cutoff);
        for (ActionRun run : stale) {
            cancelRunFromWatchdog(run, "Action cancellation timed out");
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
        actionOutputService.discardDraft(run.getId());
        runRepository.save(run);
        publishActionRunUpdatedAfterCommit(run);
        appendLogEvent(run, "WARN", message);
        actionAuditService.record("ACTION_RUN_WATCHDOG_FAILED", "FAILURE", run.getCreatedByUserId(),
                run.getProcessorDefinition().getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(), Map.of("message", message));
        dispatchQueuedRunsAfterCommit();
    }

    private void cancelRunFromWatchdog(ActionRun run, String message) {
        if (run.getStatus() == Status.CANCELLED) {
            return;
        }
        finalizeCancelledRun(run, "Cancelled");
        runRepository.save(run);
        publishActionRunUpdatedAfterCommit(run);
        appendLogEvent(run, "WARN", message);
        actionAuditService.record("ACTION_RUN_WATCHDOG_CANCELLED", "SUCCESS", run.getCreatedByUserId(),
                run.getProcessorDefinition().getId(), run.getId(), run.getWorkspaceId(), run.getProjectId(), Map.of("message", message));
        dispatchQueuedRunsAfterCommit();
    }

    private void pruneTerminalRuns(LocalDateTime cutoff) {
        List<ActionRun> expired = runRepository.findByStatusInAndCompletedAtBefore(terminalStatuses(), cutoff);
        if (expired.isEmpty()) {
            return;
        }
        deleteRunsWithLogs(expired);
        log.info("Pruned {} expired LAREX Action run(s)", expired.size());
    }

    private void deleteRunsWithLogs(List<ActionRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return;
        }
        List<String> runIds = runs.stream()
                .map(ActionRun::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!runIds.isEmpty()) {
            runDismissalRepository.deleteByRunIds(runIds);
            logEventRepository.deleteByRunIds(runIds);
        }
        runRepository.deleteAll(runs);
    }

    private Set<String> dismissedTerminalRunIds(String userId, List<ActionRun> runs) {
        List<String> terminalRunIds = runs.stream()
                .filter(run -> terminalStatuses().contains(run.getStatus()))
                .map(ActionRun::getId)
                .filter(Objects::nonNull)
                .toList();
        if (terminalRunIds.isEmpty()) {
            return Set.of();
        }
        return runDismissalRepository.findRunIdsByUserIdAndRunIds(userId, terminalRunIds);
    }

    private int dismissRuns(List<ActionRun> runs, String userId) {
        List<ActionRun> terminalRuns = runs.stream()
                .filter(run -> terminalStatuses().contains(run.getStatus()))
                .filter(run -> run.getId() != null)
                .toList();
        if (terminalRuns.isEmpty()) {
            return 0;
        }
        Set<String> existing = runDismissalRepository.findRunIdsByUserIdAndRunIds(
                userId,
                terminalRuns.stream().map(ActionRun::getId).toList()
        );
        List<ActionRunDismissal> dismissals = terminalRuns.stream()
                .filter(run -> !existing.contains(run.getId()))
                .map(run -> {
                    ActionRunDismissal dismissal = new ActionRunDismissal();
                    dismissal.setRun(run);
                    dismissal.setUserId(userId);
                    return dismissal;
                })
                .toList();
        runDismissalRepository.saveAll(dismissals);
        return dismissals.size();
    }

    private ActionRun authenticateRun(String runId, String authorizationHeader) {
        return authenticateRun(runId, authorizationHeader, false);
    }

    private ActionRun authenticateRunForUpdate(String runId, String authorizationHeader) {
        return authenticateRun(runId, authorizationHeader, true);
    }

    private ActionRun authenticateResultRunForUpdate(String runId, String authorizationHeader) {
        String rawToken = extractBearerToken(authorizationHeader);
        if (rawToken == null) {
            throw new SecurityException("Missing Action run secret");
        }
        ActionRun run = runRepository.findWithProcessorDefinitionByIdForUpdate(runId)
                .orElseThrow(() -> new SecurityException("Invalid Action run secret"));
        if (!Objects.equals(run.getSecretHash(), sha256(rawToken))) {
            throw new SecurityException("Invalid Action run secret");
        }
        if (!terminalStatuses().contains(run.getStatus())
                && (run.getSecretExpiresAt() == null || !run.getSecretExpiresAt().isAfter(LocalDateTime.now()))) {
            throw new SecurityException("Action run secret has expired");
        }
        return run;
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

    private Map<String, Object> resolveRunParameters(ActionProcessorDefinition definition,
                                                     ActionDto.ImageVariantSelection imageVariantSelection) {
        ActionDefinitionDocument document = definitionService.readParsedDocument(definition);
        Map<String, ActionDefinitionDocument.Parameter> definitions = document.parameters() == null ? Map.of() : document.parameters();
        Map<String, Object> resolved = new LinkedHashMap<>();

        for (Map.Entry<String, ActionDefinitionDocument.Parameter> entry : definitions.entrySet()) {
            resolved.put(entry.getKey(), defaultParameterValue(entry.getValue()));
        }
        if (imageVariantSelection != null) {
            resolved.put(ActionRunPayloadService.IMAGE_VARIANT_SELECTION_PARAMETER_KEY, normalizeImageVariantSelection(imageVariantSelection));
        }
        return resolved;
    }

    private ActionDto.ImageVariantSelection normalizeImageVariantSelection(ActionDto.ImageVariantSelection selection) {
        String mode = selection.mode() == null ? "GLOBAL" : selection.mode().trim().toUpperCase(Locale.ROOT);
        if (!"PER_PAGE".equals(mode)) {
            mode = "GLOBAL";
        }
        String variant = selection.variant() == null ? null : selection.variant().trim();
        Map<String, String> pageVariants = selection.pageVariants() == null
                ? Map.of()
                : selection.pageVariants().entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().trim(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return new ActionDto.ImageVariantSelection(mode, variant, pageVariants, Boolean.TRUE.equals(selection.fallbackImage()));
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

    private List<Status> cancelableStatuses() {
        return List.of(
                Status.QUEUED,
                Status.PENDING,
                Status.DISPATCHING,
                Status.RUNNING,
                Status.IMPORTING_RESULTS,
                Status.CANCEL_REQUESTED
        );
    }

    private record ConcurrencyDecision(boolean available, long active, int maxActiveRuns, String scope) {
        private String message() {
            return "Action concurrency limit reached (" + active + "/" + maxActiveRuns + " active " + scope.toLowerCase(Locale.ROOT) + " run(s))";
        }
    }

    private record ImportResult(
            List<Map<String, Object>> stored,
            Set<String> xmlPageIds,
            Set<String> resultTypes
    ) {}

    private Map<String, String> projectLabelsById(String workspaceId) {
        return projectRepository.findByLibraryWorkspaceId(workspaceId).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName, (left, ignored) -> left, LinkedHashMap::new));
    }

    private String resolveProjectLabel(String projectId) {
        return projectRepository.findById(projectId)
                .map(Project::getName)
                .orElse(projectId);
    }

    private void requireCancelAccess(String workspaceId, ActionRun run, String userId) {
        if (!canCancelRun(workspaceId, run, userId)) {
            throw new SecurityException("You do not have permission to cancel this Action run");
        }
    }

    private boolean canCancelRun(String workspaceId, ActionRun run, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (globalAdminService.isGlobalAdmin()) {
            return true;
        }
        if (workspaceAccessService.canManageProjects(workspaceId, userId)) {
            return true;
        }
        return workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)
                && userId.equals(run.getCreatedByUserId());
    }

    private ActionRun cancelRunInternal(ActionRun run, String actorUserId, String auditAction) {
        if (terminalStatuses().contains(run.getStatus())) {
            return run;
        }
        run.setCancelRequested(true);
        if (run.getStatus() == Status.QUEUED || run.getStatus() == Status.PENDING) {
            finalizeCancelledRun(run, "Cancelled");
        } else {
            run.setStatus(Status.CANCEL_REQUESTED);
            run.setStatusMessage("Cancellation requested");
        }
        ActionRun saved = runRepository.save(run);
        publishActionRunUpdatedAfterCommit(saved);
        actionAuditService.record(auditAction, "SUCCESS", actorUserId, run.getProcessorDefinition().getId(), run.getId(),
                run.getWorkspaceId(), run.getProjectId(), Map.of("status", saved.getStatus().name()));
        if (saved.getStatus() == Status.CANCELLED) {
            dispatchQueuedRunsAfterCommit();
        }
        return saved;
    }

    private void finalizeCancelledRun(ActionRun run, String statusMessage) {
        run.setCancelRequested(true);
        run.setStatus(Status.CANCELLED);
        run.setStatusMessage(statusMessage == null || statusMessage.isBlank() ? "Cancelled" : statusMessage);
        run.setCompletedAt(LocalDateTime.now());
        actionOutputService.discardDraft(run.getId());
        expireRunSecret(run);
        releaseLocks(run);
    }

    private ActionDto.TargetSelection normalizeTargetSelection(ActionDto.StartRunRequest request, String projectId) {
        ActionDto.TargetSelection provided = request.targetSelection();
        if (provided == null) {
            List<Page> pages = resolveRunPages(projectId, request.pageIds());
            return new ActionDto.TargetSelection(
                    ActionTarget.PAGE,
                    pages.stream()
                            .map(page -> new ActionDto.TargetSelectionPage(page.getId(), List.of(), List.of()))
                            .toList()
            );
        }
        ActionTarget type = provided.type() == null ? ActionTarget.PAGE : provided.type();
        List<ActionDto.TargetSelectionPage> rawPages = provided.pages() == null ? List.of() : provided.pages();
        if (rawPages.isEmpty()) {
            throw new IllegalArgumentException("Action targetSelection.pages must not be empty");
        }
        List<String> pageIds = rawPages.stream()
                .map(ActionDto.TargetSelectionPage::pageId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (pageIds.size() != rawPages.size()) {
            throw new IllegalArgumentException("Each targetSelection page must include a pageId");
        }
        List<Page> pages = resolveRunPages(projectId, pageIds);
        Set<String> existingPageIds = pages.stream().map(Page::getId).collect(Collectors.toSet());
        List<ActionDto.TargetSelectionPage> normalizedPages = rawPages.stream()
                .filter(page -> existingPageIds.contains(page.pageId()))
                .map(page -> new ActionDto.TargetSelectionPage(
                        page.pageId(),
                        safeList(page.regionIds()).stream().filter(id -> id != null && !id.isBlank()).distinct().toList(),
                        safeList(page.textLineIds()).stream().filter(id -> id != null && !id.isBlank()).distinct().toList()
                ))
                .toList();
        validateTargetSelection(projectId, type, normalizedPages);
        return new ActionDto.TargetSelection(type, normalizedPages);
    }

    private void validateTargetSelection(String projectId, ActionTarget type, List<ActionDto.TargetSelectionPage> pages) {
        if (type == ActionTarget.PAGE) {
            return;
        }
        for (ActionDto.TargetSelectionPage page : pages) {
            PageXml xml = primaryPageXml(page.pageId());
            PageDto pageDto;
            try {
                pageDto = annotationProcessingService.parseXmlToAnnotation(xml.getId());
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read PAGE XML for target selection");
            }
            Set<String> regionIds = new LinkedHashSet<>();
            Set<String> textLineIds = new LinkedHashSet<>();
            resultPageMergeService.collectTargetIds(pageDto.regions(), regionIds, textLineIds);
            if (type == ActionTarget.REGION) {
                if (safeList(page.regionIds()).isEmpty()) {
                    throw new IllegalArgumentException("Region-targeted Actions require at least one region id");
                }
                for (String regionId : page.regionIds()) {
                    if (!regionIds.contains(regionId)) {
                        throw new IllegalArgumentException("Region does not exist on selected page: " + regionId);
                    }
                }
            }
            if (type == ActionTarget.TEXT_LINE) {
                if (safeList(page.textLineIds()).isEmpty()) {
                    throw new IllegalArgumentException("Textline-targeted Actions require at least one textline id");
                }
                for (String textLineId : page.textLineIds()) {
                    if (!textLineIds.contains(textLineId)) {
                        throw new IllegalArgumentException("TextLine does not exist on selected page: " + textLineId);
                    }
                }
            }
        }
    }

    private void requireTargetSupported(ActionProcessorDefinition definition, ActionTarget target) {
        if (!definitionService.readTargetTypes(definition).contains(target)) {
            throw new IllegalArgumentException("Action does not support target: " + target);
        }
    }

    private ActionDto.MachineTargetSelection buildMachineTargetSelection(ActionRun run, Set<String> includedPageIds) {
        ActionDto.TargetSelection selection = payloadService.readTargetSelection(run);
        List<ActionDto.MachineTargetPage> pages = selection.pages().stream()
                .filter(page -> includedPageIds == null || includedPageIds.contains(page.pageId()))
                .map(page -> buildMachineTargetPage(page, selection.type()))
                .toList();
        return new ActionDto.MachineTargetSelection(selection.type(), pages);
    }

    private ActionDto.MachineTargetPage buildMachineTargetPage(ActionDto.TargetSelectionPage targetPage, ActionTarget type) {
        if (type == ActionTarget.PAGE) {
            return new ActionDto.MachineTargetPage(targetPage.pageId(), List.of(), List.of());
        }
        return new ActionDto.MachineTargetPage(
                targetPage.pageId(),
                safeList(targetPage.regionIds()),
                safeList(targetPage.textLineIds())
        );
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private Optional<ActionDto.MachinePageInput> toMachinePageInput(Page page,
                                                                   Map<String, List<PageImage>> imagesByPage,
                                                                   Map<String, PageXml> xmlByPage,
                                                                   ActionDto.ImageVariantSelection imageVariantSelection,
                                                                   ActionProcessorDefinition definition,
                                                                   String publicApiBaseUrl,
                                                                   String runId) {
        List<PageImage> images = imagesByPage.getOrDefault(page.getId(), List.of());
        List<PageImage> selectedImages = definition.isAcceptsImages()
                ? selectImagesForPage(page.getId(), images, imageVariantSelection)
                : List.of();
        if (definition.isAcceptsImages() && imageVariantSelection != null && selectedImages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ActionDto.MachinePageInput(
                page.getId(),
                page.getName(),
                selectedImages.stream()
                        .map(image -> toMachineImageFile(publicApiBaseUrl, runId, image))
                        .toList(),
                Optional.ofNullable(xmlByPage.get(page.getId())).stream()
                        .map(xml -> toMachineXmlFile(publicApiBaseUrl, runId, xml))
                        .toList()
        ));
    }

    private List<String> processorPageIds(ActionRun run,
                                          ActionProcessorDefinition definition,
                                          ActionDto.ImageVariantSelection imageVariantSelection) {
        List<String> pageIds = payloadService.readPageIds(run);
        if (!definition.isAcceptsImages() || imageVariantSelection == null) {
            return pageIds;
        }
        Map<String, List<PageImage>> imagesByPage = pageImageRepository.findByPageIdIn(pageIds).stream()
                .collect(Collectors.groupingBy(image -> image.getPage().getId()));
        return pageIds.stream()
                .filter(pageId -> !selectImagesForPage(
                        pageId,
                        imagesByPage.getOrDefault(pageId, List.of()),
                        imageVariantSelection
                ).isEmpty())
                .toList();
    }

    private List<Page> processorPages(List<Page> pages,
                                      ActionProcessorDefinition definition,
                                      Map<String, Object> parameters) {
        ActionDto.ImageVariantSelection imageVariantSelection = payloadService.readImageVariantSelection(parameters);
        if (!definition.isAcceptsImages() || imageVariantSelection == null) {
            return pages;
        }
        List<String> pageIds = pages.stream().map(Page::getId).toList();
        Map<String, List<PageImage>> imagesByPage = pageImageRepository.findByPageIdIn(pageIds).stream()
                .collect(Collectors.groupingBy(image -> image.getPage().getId()));
        List<Page> includedPages = pages.stream()
                .filter(page -> !selectImagesForPage(
                        page.getId(),
                        imagesByPage.getOrDefault(page.getId(), List.of()),
                        imageVariantSelection
                ).isEmpty())
                .toList();
        if (includedPages.isEmpty()) {
            throw new IllegalArgumentException(
                    "No pages have the selected image variant; choose another variant or enable fallback");
        }
        return includedPages;
    }

    private ActionDto.TargetSelection restrictTargetSelection(ActionDto.TargetSelection targetSelection,
                                                              List<Page> pages) {
        Set<String> includedPageIds = pages.stream()
                .map(Page::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ActionDto.TargetSelection(
                targetSelection.type(),
                targetSelection.pages().stream()
                        .filter(page -> includedPageIds.contains(page.pageId()))
                        .toList()
        );
    }

    private List<PageImage> selectImagesForPage(String pageId,
                                                List<PageImage> images,
                                                ActionDto.ImageVariantSelection selection) {
        List<PageImage> sortedImages = images.stream()
                .sorted(Comparator.comparing(PageImage::getVariant, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PageImage::getFileName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (selection == null) {
            return sortedImages;
        }
        String wantedVariant = wantedImageVariant(pageId, selection);
        if (wantedVariant == null || wantedVariant.isBlank()) {
            return sortedImages;
        }
        List<PageImage> matching = sortedImages.stream()
                .filter(image -> wantedVariant.equals(image.getVariant()))
                .toList();
        if (!matching.isEmpty()) {
            return matching;
        }
        if (Boolean.TRUE.equals(selection.fallbackImage()) && !sortedImages.isEmpty()) {
            return List.of(sortedImages.getFirst());
        }
        return List.of();
    }

    private String wantedImageVariant(String pageId, ActionDto.ImageVariantSelection selection) {
        String mode = selection.mode() == null ? "GLOBAL" : selection.mode().trim().toUpperCase(Locale.ROOT);
        if ("PER_PAGE".equals(mode)) {
            return selection.pageVariants() == null ? null : selection.pageVariants().get(pageId);
        }
        return selection.variant();
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
        if (file == null) {
            throw new IllegalArgumentException("Missing result file part: " + fieldName);
        }
        return file;
    }

    private void validateResultManifest(List<ActionDto.ResultFile> resultFiles,
                                        MultiValueMap<String, MultipartFile> files) {
        int fileCount = resultFiles.size();
        if (fileCount > actionProperties.getResults().getMaxFiles()) {
            throw new IllegalArgumentException("Too many Action result files: " + fileCount + " > " + actionProperties.getResults().getMaxFiles());
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
            String resultType = normalize(resultFile.type());
            boolean genericFile = "file".equals(resultType) || "files".equals(resultType);
            if (file.isEmpty() && !genericFile) {
                throw new IllegalArgumentException("Missing result file part: " + resultFile.fieldName());
            }
            validateResultFileSize(file);
            totalBytes += file.getSize();
            if (totalBytes > actionProperties.getResults().getMaxTotalBytes()) {
                throw new IllegalArgumentException("Action result upload exceeds total size limit");
            }
        }
    }

    private long resultUploadBytes(List<ActionDto.ResultFile> resultFiles,
                                   MultiValueMap<String, MultipartFile> files) {
        return resultFiles.stream().mapToLong(file -> resolveMultipart(files, file.fieldName()).getSize()).sum();
    }

    private void validateResultFileSize(MultipartFile file) {
        if (file.getSize() > actionProperties.getResults().getMaxFileBytes()) {
            throw new IllegalArgumentException("Action result file exceeds size limit");
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

    private int runHistoryLimit(int requestedLimit) {
        return Math.max(1, Math.min(MAX_RUN_HISTORY_LIMIT, requestedLimit));
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
