package de.uniwue.zpd.dachs.larex.backend.service.action;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ExecuteRole;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.LockMode;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorAssignmentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorWorkspaceAvailabilityRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunDismissalRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunLogEventRepository;
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
import de.uniwue.zpd.dachs.larex.backend.service.search.SearchLexiconService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.ThumbnailService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlValidationService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActionRunServiceTest {

    private static final String WORKSPACE_ID = "workspace-1";
    private static final String OTHER_WORKSPACE_ID = "workspace-2";
    private static final String OWNER_ID = "owner-1";
    private static final String CURATOR_ID = "curator-1";
    private static final String OUTSIDER_ID = "outsider-1";
    private static final String ADMIN_ID = "admin-1";
    private static final String RUN_SECRET = "run-secret";

    @Mock
    private ActionProcessorDefinitionRepository definitionRepository;
    @Mock
    private ActionProcessorAssignmentRepository assignmentRepository;
    @Mock
    private ActionProcessorWorkspaceAvailabilityRepository availabilityRepository;
    @Mock
    private ActionRunRepository runRepository;
    @Mock
    private ActionRunDismissalRepository runDismissalRepository;
    @Mock
    private ActionRunLogEventRepository logEventRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private PageRepository pageRepository;
    @Mock
    private PageImageRepository pageImageRepository;
    @Mock
    private PageXmlRepository pageXmlRepository;
    @Mock
    private WorkspaceAccessService workspaceAccessService;
    @Mock
    private GlobalAdminService globalAdminService;
    @Mock
    private ActionDefinitionService definitionService;
    @Mock
    private ActionEndpointAuthService endpointAuthService;
    @Mock
    private TaskExecutor importTaskExecutor;
    @Mock
    private HierarchicalFileStorageService fileStorageService;
    @Mock
    private ThumbnailService thumbnailService;
    @Mock
    private WorkspaceQuotaGuardService workspaceQuotaGuardService;
    @Mock
    private WorkspaceQuotaRefreshService workspaceQuotaRefreshService;
    @Mock
    private PageXmlValidationService pageXmlValidationService;
    @Mock
    private PageXmlCanonicalizationService pageXmlCanonicalizationService;
    @Mock
    private PageXmlVersionService pageXmlVersionService;
    @Mock
    private PageFilterIndexService pageFilterIndexService;
    @Mock
    private PageIndexStatusTracker pageIndexStatusTracker;
    @Mock
    private SearchLexiconService searchLexiconService;
    @Mock
    private AnnotationReadCache annotationReadCache;
    @Mock
    private AnnotationProcessingService annotationProcessingService;
    @Mock
    private PageXmlToAnnotationParser pageXmlToAnnotationParser;
    @Mock
    private PageOrderService pageOrderService;
    @Mock
    private ActionAuditService actionAuditService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ActionRunService service;
    private ObjectMapper objectMapper;
    private ActionProperties actionProperties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        actionProperties = new ActionProperties();
        ActionRunPayloadService payloadService = new ActionRunPayloadService(objectMapper);
        ActionRunResponseMapper responseMapper = new ActionRunResponseMapper(
                runRepository,
                logEventRepository,
                projectRepository,
                workspaceAccessService,
                globalAdminService,
                definitionService,
                payloadService,
                objectMapper
        );
        ActionResultPageMergeService resultPageMergeService = new ActionResultPageMergeService();
        service = new ActionRunService(
                definitionRepository,
                assignmentRepository,
                availabilityRepository,
                runRepository,
                runDismissalRepository,
                logEventRepository,
                projectRepository,
                pageRepository,
                pageImageRepository,
                pageXmlRepository,
                workspaceAccessService,
                globalAdminService,
                definitionService,
                endpointAuthService,
                importTaskExecutor,
                fileStorageService,
                thumbnailService,
                workspaceQuotaGuardService,
                workspaceQuotaRefreshService,
                pageXmlValidationService,
                pageXmlCanonicalizationService,
                pageXmlVersionService,
                pageFilterIndexService,
                pageIndexStatusTracker,
                searchLexiconService,
                annotationReadCache,
                annotationProcessingService,
                pageXmlToAnnotationParser,
                pageOrderService,
                actionAuditService,
                actionProperties,
                payloadService,
                responseMapper,
                resultPageMergeService,
                transactionTemplate
        );
        when(runDismissalRepository.findRunIdsByUserIdAndRunIds(anyString(), anyCollection())).thenReturn(Set.of());
        when(runRepository.findByStatusInAndLastHeartbeatAtBefore(anyCollection(), any())).thenReturn(List.of());
        when(runRepository.findByStatusInAndCompletedAtBefore(anyCollection(), any())).thenReturn(List.of());
        when(runRepository.findIdsByStatusOrderByCreatedAsc(ActionRun.Status.QUEUED)).thenReturn(List.of());
        lenient().when(pageOrderService.sortPages(anyCollection())).thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
    }

    @Test
    void creatorCanCancelOwnRunWithoutCuratorRole() {
        Project project = project("project-1", WORKSPACE_ID, "Project A");
        ActionProcessorDefinition definition = definition("processor-own-cancel");
        ActionRun run = run(definition, project, OWNER_ID, ActionRun.Status.RUNNING, LockMode.PAGES, List.of("page-1"));

        when(projectRepository.findByIdAndLibraryWorkspaceId(project.getId(), WORKSPACE_ID)).thenReturn(Optional.of(project));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(runRepository.findWithProcessorDefinitionById(run.getId())).thenReturn(Optional.of(run));
        when(runRepository.save(any(ActionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pageRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());
        when(workspaceAccessService.canManageProjects(WORKSPACE_ID, OWNER_ID)).thenReturn(false);
        when(workspaceAccessService.hasWorkspaceAccess(WORKSPACE_ID, OWNER_ID)).thenReturn(true);
        when(globalAdminService.isGlobalAdmin()).thenReturn(false);

        ActionDto.RunResponse response = service.cancelRun(WORKSPACE_ID, project.getId(), run.getId(), OWNER_ID);

        assertThat(response.status()).isEqualTo(ActionRun.Status.CANCEL_REQUESTED);
        assertThat(run.getStatus()).isEqualTo(ActionRun.Status.CANCEL_REQUESTED);
        assertThat(run.isCancelRequested()).isTrue();
    }

    @Test
    void outsiderCannotCancelSomeoneElsesRun() {
        Project project = project("project-1", WORKSPACE_ID, "Project A");
        ActionProcessorDefinition definition = definition("processor-outsider-cancel");
        ActionRun run = run(definition, project, OWNER_ID, ActionRun.Status.RUNNING, LockMode.PAGES, List.of("page-1"));

        when(projectRepository.findByIdAndLibraryWorkspaceId(project.getId(), WORKSPACE_ID)).thenReturn(Optional.of(project));
        when(runRepository.findWithProcessorDefinitionById(run.getId())).thenReturn(Optional.of(run));
        when(workspaceAccessService.canManageProjects(WORKSPACE_ID, OUTSIDER_ID)).thenReturn(false);
        when(workspaceAccessService.hasWorkspaceAccess(WORKSPACE_ID, OUTSIDER_ID)).thenReturn(true);
        when(globalAdminService.isGlobalAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.cancelRun(WORKSPACE_ID, project.getId(), run.getId(), OUTSIDER_ID))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("permission");
    }

    @Test
    void curatorCanCancelAnyWorkspaceRun() {
        Project project = project("project-1", WORKSPACE_ID, "Project A");
        ActionProcessorDefinition definition = definition("processor-curator-cancel");
        ActionRun run = run(definition, project, OWNER_ID, ActionRun.Status.RUNNING, LockMode.PAGES, List.of("page-1"));

        when(projectRepository.findByIdAndLibraryWorkspaceId(project.getId(), WORKSPACE_ID)).thenReturn(Optional.of(project));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(runRepository.findWithProcessorDefinitionById(run.getId())).thenReturn(Optional.of(run));
        when(runRepository.save(any(ActionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pageRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());
        when(workspaceAccessService.canManageProjects(WORKSPACE_ID, CURATOR_ID)).thenReturn(true);
        when(globalAdminService.isGlobalAdmin()).thenReturn(false);

        ActionDto.RunResponse response = service.cancelRun(WORKSPACE_ID, project.getId(), run.getId(), CURATOR_ID);

        assertThat(response.status()).isEqualTo(ActionRun.Status.CANCEL_REQUESTED);
    }

    @Test
    void globalAdminCanBulkCancelActiveRunsForDefinition() {
        Project project = project("project-1", WORKSPACE_ID, "Project A");
        ActionProcessorDefinition definition = definition("processor-admin-bulk-cancel");
        ActionRun runningRun = run(definition, project, OWNER_ID, ActionRun.Status.RUNNING, LockMode.PAGES, List.of("page-1"));
        ActionRun queuedRun = run(definition, project, OWNER_ID, ActionRun.Status.QUEUED, LockMode.PAGES, List.of("page-2"));

        when(globalAdminService.isGlobalAdmin()).thenReturn(true);
        when(definitionRepository.findById(definition.getId())).thenReturn(Optional.of(definition));
        when(runRepository.findByProcessorDefinitionIdAndStatusIn(eq(definition.getId()), anyCollection()))
                .thenReturn(List.of(runningRun, queuedRun));
        when(projectRepository.findByIdAndLibraryWorkspaceId(project.getId(), WORKSPACE_ID)).thenReturn(Optional.of(project));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(runRepository.findWithProcessorDefinitionById(runningRun.getId())).thenReturn(Optional.of(runningRun));
        when(runRepository.findWithProcessorDefinitionById(queuedRun.getId())).thenReturn(Optional.of(queuedRun));
        when(runRepository.save(any(ActionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pageRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());

        ActionDto.BulkCancelRunsResponse response = service.cancelActiveAdminRuns(definition.getId(), ADMIN_ID);

        assertThat(response.cancelledCount()).isEqualTo(2);
        assertThat(runningRun.getStatus()).isEqualTo(ActionRun.Status.CANCEL_REQUESTED);
        assertThat(queuedRun.getStatus()).isEqualTo(ActionRun.Status.CANCELLED);
    }

    @Test
    void cancelledHeartbeatFinalizesRunAndReleasesProjectLock() {
        Project project = project("project-1", WORKSPACE_ID, "Locked Project");
        ActionProcessorDefinition definition = definition("processor-heartbeat-cancelled");
        ActionRun run = run(definition, project, OWNER_ID, ActionRun.Status.RUNNING, LockMode.PROJECT, List.of());
        project.setLocked(true);
        project.setLockedReason("LAREX Action running");
        project.setLockedByActionRunId(run.getId());
        project.setLockedAt(LocalDateTime.now().minusMinutes(1));

        when(runRepository.findWithProcessorDefinitionById(run.getId())).thenReturn(Optional.of(run));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(runRepository.save(any(ActionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pageRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());

        ActionDto.HeartbeatResponse response = service.heartbeat(
                run.getId(),
                "Bearer " + RUN_SECRET,
                new ActionDto.HeartbeatRequest(87, "Cancelled by operator", "Stopping now", "INFO", "cancelled", null)
        );

        assertThat(response.cancelRequested()).isTrue();
        assertThat(run.getStatus()).isEqualTo(ActionRun.Status.CANCELLED);
        assertThat(run.isCancelRequested()).isTrue();
        assertThat(run.getStatusMessage()).isEqualTo("Cancelled by operator");
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(run.getSecretExpiresAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(project.isLocked()).isFalse();
        assertThat(project.getLockedByActionRunId()).isNull();
    }

    @Test
    void cancelledRunsRejectResultUploads() {
        Project project = project("project-1", WORKSPACE_ID, "Project A");
        ActionProcessorDefinition definition = definition("processor-result-reject");
        ActionRun run = run(definition, project, OWNER_ID, ActionRun.Status.CANCEL_REQUESTED, LockMode.PAGES, List.of("page-1"));
        run.setCancelRequested(true);

        when(runRepository.findWithProcessorDefinitionByIdForUpdate(run.getId())).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.receiveResults(
                run.getId(),
                "Bearer " + RUN_SECRET,
                new ActionDto.ResultManifest(1, "completed", "Done", List.of(), List.of()),
                new LinkedMultiValueMap<>()
        )).isInstanceOf(SecurityException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void staleCancellationRequestsAreFinalizedByWatchdog() {
        Project project = project("project-1", WORKSPACE_ID, "Project A");
        ActionProcessorDefinition definition = definition("processor-watchdog-cancel");
        ActionRun run = run(definition, project, OWNER_ID, ActionRun.Status.CANCEL_REQUESTED, LockMode.PAGES, List.of("page-1"));
        run.setCancelRequested(true);

        when(runRepository.findByStatusInAndUpdatedBefore(anyCollection(), any()))
                .thenReturn(List.of(run));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(runRepository.save(any(ActionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pageRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());

        service.reconcileStaleRuns();

        assertThat(run.getStatus()).isEqualTo(ActionRun.Status.CANCELLED);
        assertThat(run.getCompletedAt()).isNotNull();
    }

    @Test
    void workspaceRunListingBatchesQueuePositionLookupsPerDefinition() {
        Project projectOne = project("project-1", WORKSPACE_ID, "Project A");
        Project projectTwo = project("project-2", WORKSPACE_ID, "Project B");
        Project otherProject = project("project-3", OTHER_WORKSPACE_ID, "Project C");
        ActionProcessorDefinition definition = definition("processor-workspace-queue");
        ActionRun first = run(definition, projectOne, OWNER_ID, ActionRun.Status.QUEUED, LockMode.PAGES, List.of("page-1"));
        ActionRun second = run(definition, projectTwo, OWNER_ID, ActionRun.Status.QUEUED, LockMode.PAGES, List.of("page-2"));
        ActionRun otherWorkspaceRun = run(definition, otherProject, OWNER_ID, ActionRun.Status.QUEUED, LockMode.PAGES, List.of("page-3"));

        when(runRepository.findByWorkspaceIdOrderByCreatedDesc(WORKSPACE_ID)).thenReturn(List.of(second, first));
        when(projectRepository.findByLibraryWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(projectOne, projectTwo));
        when(runRepository.findByProcessorDefinitionIdAndStatusOrderByCreatedAsc(definition.getId(), ActionRun.Status.QUEUED))
                .thenReturn(List.of(first, second, otherWorkspaceRun));
        when(definitionService.readParsedDocument(definition)).thenReturn(parsedDefinition(definition.getProcessorKey(), "WORKSPACE"));
        when(workspaceAccessService.hasWorkspaceAccess(WORKSPACE_ID, OWNER_ID)).thenReturn(true);
        when(workspaceAccessService.canManageProjects(WORKSPACE_ID, OWNER_ID)).thenReturn(false);
        when(globalAdminService.isGlobalAdmin()).thenReturn(false);

        List<ActionDto.RunResponse> runs = service.listWorkspaceRuns(WORKSPACE_ID, OWNER_ID);

        assertThat(runs).filteredOn(run -> run.id().equals(first.getId())).singleElement()
                .extracting(ActionDto.RunResponse::queuePosition)
                .isEqualTo(1);
        assertThat(runs).filteredOn(run -> run.id().equals(second.getId())).singleElement()
                .extracting(ActionDto.RunResponse::queuePosition)
                .isEqualTo(2);
        verify(runRepository, times(1))
                .findByProcessorDefinitionIdAndStatusOrderByCreatedAsc(definition.getId(), ActionRun.Status.QUEUED);
        verify(runRepository, never()).findByWorkspaceIdAndProjectIdOrderByCreatedDesc(anyString(), anyString());
    }

    private ActionProcessorDefinition definition(String processorKey) {
        ActionProcessorDefinition definition = new ActionProcessorDefinition();
        definition.setId(processorKey + "-id");
        definition.setProcessorKey(processorKey);
        definition.setName(processorKey);
        definition.setExecuteRole(ExecuteRole.CURATOR);
        definition.setLockMode(LockMode.PAGES);
        return definition;
    }

    private Project project(String projectId, String workspaceId, String name) {
        Library library = new Library(workspaceId, "Library " + workspaceId);
        library.setId("lib-" + workspaceId);
        Project project = new Project(name, null, library);
        project.setId(projectId);
        return project;
    }

    private ActionRun run(
            ActionProcessorDefinition definition,
            Project project,
            String createdByUserId,
            ActionRun.Status status,
            LockMode lockMode,
            List<String> pageIds
    ) {
        ActionRun run = new ActionRun();
        run.setId(definition.getProcessorKey() + "-" + project.getId() + "-" + status.name());
        run.setProcessorDefinition(definition);
        run.setWorkspaceId(project.getLibrary().getWorkspaceId());
        run.setProjectId(project.getId());
        run.setCreatedByUserId(createdByUserId);
        run.setStatus(status);
        run.setLockMode(lockMode);
        run.setPageIdsJson(toJson(pageIds));
        run.setParametersJson("{}");
        run.setSecretHash(sha256(RUN_SECRET));
        run.setSecretPrefix("lrx_act_test");
        run.setSecretExpiresAt(LocalDateTime.now().plusHours(1));
        run.setStatusMessage(status.name());
        run.setCreated(LocalDateTime.now());
        run.setUpdated(LocalDateTime.now());
        return run;
    }

    private ActionDefinitionDocument parsedDefinition(String processorKey, String scope) {
        return new ActionDefinitionDocument(
                1,
                processorKey,
                processorKey,
                "test",
                "WORKFLOW",
                List.of("PAGE"),
                new ActionDefinitionDocument.Endpoint("https://processor.example/dispatch", 30, null, null),
                new ActionDefinitionDocument.Access("CURATOR"),
                new ActionDefinitionDocument.Locking("PAGES"),
                new ActionDefinitionDocument.Inputs(false, true),
                new ActionDefinitionDocument.Outputs(
                        new ActionDefinitionDocument.OutputTarget(true, "REPLACE_PAGE"),
                        null
                ),
                new ActionDefinitionDocument.Concurrency(1, scope),
                null,
                Map.of()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
