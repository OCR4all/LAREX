package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.config.action.DevActionBootstrap;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionWorkflowDto;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.repository.action.*;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.action.*;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "larex.actions.endpoint-secrets.mock-processor-v1=workflow-mock-test-secret")
class ActionWorkflowIntegrationTest {
    @Autowired ActionWorkflowService workflows;
    @Autowired ActionDefinitionService definitions;
    @Autowired ActionWorkspaceActivationService activation;
    @Autowired ActionWorkflowScheduler scheduler;
    @Autowired ActionRunService actions;
    @Autowired ActionWorkflowRunRepository workflowRuns;
    @Autowired ActionRunRepository runs;
    @Autowired ActionProcessorDefinitionRepository processors;
    @Autowired ActionProcessorAssignmentRepository assignments;
    @Autowired LibraryRepository libraries;
    @Autowired ProjectRepository projects;
    @Autowired PageRepository pages;
    @Autowired PageXmlRepository xmls;
    @Autowired de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository images;
    @Autowired de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService storage;
    @Autowired de.uniwue.zpd.dachs.larex.backend.service.image.ImageTypeService imageTypes;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService uploadPaths;
    @MockitoBean GlobalAdminService admin;
    @MockitoBean WorkspaceAccessService access;
    // Keep dispatched jobs pending, so tests explicitly drive the real callback lifecycle.
    @MockitoBean(name = "importTaskExecutor") TaskExecutor dispatch;

    @BeforeEach
    void authorize() {
        when(admin.isGlobalAdmin()).thenReturn(true);
        when(access.canManageProjects(anyString(), anyString())).thenReturn(true);
        when(access.hasWorkspaceAccess(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void developmentBootstrapPublishesTypedMocksWithValidInputsAndOverwriteDeclarations() {
        new DevActionBootstrap(definitions, processors, new ActionProperties()).run(null);
        var binary = processors.findByProcessorKey("mock-binarization").orElseThrow();
        var region = processors.findByProcessorKey("mock-whole-page-region").orElseThrow();
        var line = processors.findByProcessorKey("mock-single-text-line").orElseThrow();
        var ocr = processors.findByProcessorKey("mock-random-ocr").orElseThrow();
        for (var processor : List.of(binary, region, line, ocr)) {
            assertThat(processor.isEnabled()).isTrue();
            assertThat(processor.isGlobalAvailable()).isTrue();
            assertThat(processor.isWorkflow()).isFalse();
            assertThat(definitions.validateYaml(processor.getYamlSource(), processor.getId()).valid()).isTrue();
        }
        assertThat(definitions.readInputRequirements(binary).images().imageTypes()).containsExactly(ImageType.COLOR, ImageType.GRAYSCALE);
        assertThat(definitions.readInputRequirements(binary).images().required(ActionProcessorDefinition.ActionTarget.PAGE)).isTrue();
        for (var processor : List.of(region, line, ocr)) {
            var images = definitions.readInputRequirements(processor).images();
            assertThat(images.imageTypes()).containsExactly(ImageType.BINARY);
            assertThat(images.required(ActionProcessorDefinition.ActionTarget.PAGE)).isTrue();
        }
        var datasetTypes = Map.of(
                "mock-integration", List.of(ImageType.GRAYSCALE),
                "mock-training", List.of(ImageType.BINARY),
                "mock-ocr-evaluation", List.of(ImageType.BINARY),
                "mock-layout-evaluation", List.of(ImageType.COLOR, ImageType.GRAYSCALE));
        for (var entry : datasetTypes.entrySet()) {
            var processor = processors.findByProcessorKey(entry.getKey()).orElseThrow();
            assertThat(definitions.validateYaml(processor.getYamlSource(), processor.getId()).valid()).isTrue();
            var images = definitions.readInputRequirements(processor).images();
            assertThat(images.imageTypes()).containsExactlyElementsOf(entry.getValue());
            assertThat(images.required(ActionProcessorDefinition.ActionTarget.PAGE)).isTrue();
        }
        var copy = processors.findByProcessorKey("mock-image-copy").orElseThrow();
        assertThat(definitions.readInputRequirements(copy).images().level()).isEqualTo(ActionDto.InputLevel.OPTIONAL);
        assertThat(definitions.readInputRequirements(copy).images().imageTypes()).isEmpty();
        assertThat(binary.isOutputsImages()).isTrue();
        assertThat(binary.isOutputsXml()).isFalse();
        assertThat(definitions.readInputRequirements(region).xml().required(ActionProcessorDefinition.ActionTarget.PAGE)).isFalse();
        assertThat(definitions.readInputRequirements(line).xml().required(ActionProcessorDefinition.ActionTarget.PAGE)).isTrue();
        assertThat(definitions.readInputRequirements(ocr).xml().required(ActionProcessorDefinition.ActionTarget.PAGE)).isTrue();
        assertThat(definitions.readOverwriteDeclarations(region).get(ActionProcessorDefinition.ActionTarget.PAGE)).contains(ActionDto.AnnotationLevel.REGIONS);
        assertThat(definitions.readOverwriteDeclarations(line).get(ActionProcessorDefinition.ActionTarget.PAGE)).contains(ActionDto.AnnotationLevel.TEXT_LINES);
        assertThat(definitions.readOverwriteDeclarations(ocr).get(ActionProcessorDefinition.ActionTarget.PAGE)).contains(ActionDto.AnnotationLevel.TEXT);
    }

    @Test
    void persistedSequenceWaitsForCompletionAndConcurrentContinuationStartsExactlyOneNextStep() throws Exception {
        Fixture f = fixture(false);
        var started = workflows.start(f.workspace, f.project, request(f), "owner", "http://backend/api/v1");
        assertThat(started.stepRunIds()).hasSize(1);
        String first = started.stepRunIds().getFirst();
        for (var status : List.of(ActionRun.Status.PENDING, ActionRun.Status.RUNNING, ActionRun.Status.IMPORTING_RESULTS)) {
            setStatus(first, status);
            workflows.advance(started.id());
            assertThat(run(started.id()).stepRunIds()).containsExactly(first);
        }
        // Editing keeps the running snapshot; active definitions cannot be deleted.
        workflows.save(f.workspace, f.workflow.id(), new ActionWorkflowDto.SaveRequest("Edited", "", true,
                List.of(new ActionWorkflowDto.Step(f.second, Map.of("threshold", 0.9), null)), f.workflow.version()), "owner");
        assertThatThrownBy(() -> workflows.delete(f.workspace, f.workflow.id(), "owner"))
                .isInstanceOf(IllegalStateException.class);
        complete(first, f.page);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> workflows.advance(started.id()));
            var b = pool.submit(() -> workflows.advance(started.id()));
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        }
        var continued = run(started.id());
        assertThat(continued.stepRunIds()).hasSize(2);
        String second = continued.stepRunIds().getLast();
        assertThat(runs.findById(second).orElseThrow().getProcessorDefinition().getId()).isEqualTo(f.second);
        assertThat(runs.findById(second).orElseThrow().getParametersJson()).contains("0.7");
        complete(second, f.page);
        workflows.advance(started.id());
        String third = run(started.id()).stepRunIds().getLast();
        assertThat(run(started.id()).stepRunIds()).hasSize(3);
        assertThat(runs.findById(third).orElseThrow().getParametersJson()).contains("0.2");
        complete(third, f.page);
        workflows.advance(started.id());
        assertThat(run(started.id()).status()).isEqualTo("COMPLETED");
        assertThat(run(started.id()).completedAt()).isNotNull();
        workflows.delete(f.workspace, f.workflow.id(), "owner");
    }

    @ParameterizedTest
    @EnumSource(value = ActionRun.Status.class, names = {"FAILED", "CANCELLED", "COMPLETED_WITH_ISSUES"})
    void stopsAfterAnyUnsuccessfulStep(ActionRun.Status status) {
        Fixture f = fixture(false);
        var started = workflows.start(f.workspace, f.project, request(f), "owner", "http://backend/api/v1");
        setStatus(started.stepRunIds().getFirst(), status);
        workflows.advance(started.id());
        var result = run(started.id());
        assertThat(result.stepRunIds()).hasSize(1);
        assertThat(result.status()).isEqualTo(status == ActionRun.Status.CANCELLED ? "CANCELLED" : "FAILED");
    }

    @Test
    void cancellingWorkflowCancelsActiveChildAndNeverStartsLaterSteps() {
        Fixture f = fixture(false);
        var started = workflows.start(f.workspace, f.project, request(f), "owner", "http://backend/api/v1");
        setStatus(started.stepRunIds().getFirst(), ActionRun.Status.RUNNING);
        assertThat(workflows.cancel(f.workspace, f.project, started.id(), "owner").status()).isEqualTo("CANCEL_REQUESTED");
        assertThat(runs.findById(started.stepRunIds().getFirst()).orElseThrow().isCancelRequested()).isTrue();
        setStatus(started.stepRunIds().getFirst(), ActionRun.Status.CANCELLED);
        workflows.advance(started.id());
        assertThat(run(started.id()).status()).isEqualTo("CANCELLED");
        assertThat(run(started.id()).stepRunIds()).hasSize(1);
    }

    @Test
    void laterStepUsesXmlCommittedAfterFirstStepAndDoesNotRequireItAtWorkflowStart() throws Exception {
        Fixture f = fixture(true);
        assertThat(workflows.preview(f.workspace, f.project, request(f), "owner").pages()).hasSize(1);
        var started = workflows.start(f.workspace, f.project, request(f), "owner", "http://backend/api/v1");
        xmls.saveAndFlush(new PageXml("page.xml", "/unused.xml", "application/xml", 0L, "default", "page",
                XmlSchema.PAGE_XML, "2019-07-15", pages.findById(f.page).orElseThrow()));
        complete(started.stepRunIds().getFirst(), f.page);
        workflows.advance(started.id());
        assertThat(run(started.id()).stepRunIds()).hasSize(2);
        assertThat(runs.findById(run(started.id()).stepRunIds().getLast()).orElseThrow().getPageIdsJson()).contains(f.page);
    }

    @Test
    void missingLaterInputsRollbackChildAndPersistFailureInsteadOfRepeatingForever() throws Exception {
        Fixture f = fixture(true);
        var started = workflows.start(f.workspace, f.project, request(f), "owner", "http://backend/api/v1");
        complete(started.stepRunIds().getFirst(), f.page);
        scheduler.continueWorkflows();
        assertThat(run(started.id()).status()).isEqualTo("FAILED");
        assertThat(run(started.id()).stepRunIds()).hasSize(1);
        assertThat(runs.findByWorkspaceIdAndProjectIdOrderByCreatedDesc(f.workspace, f.project)).hasSize(1);
    }

    @Test
    void staleFailureCannotOverwriteAConcurrentSuccessfulContinuation() throws Exception {
        Fixture f = fixture(false);
        var started = workflows.start(f.workspace, f.project, request(f), "owner", "url");
        Long version = workflowRuns.findById(started.id()).orElseThrow().getVersion();
        complete(started.stepRunIds().getFirst(), f.page);
        workflows.advance(started.id());
        workflows.fail(started.id(), version, "Stale failure");
        assertThat(run(started.id()).status()).isEqualTo("RUNNING");
        assertThat(run(started.id()).stepRunIds()).hasSize(2);
    }

    @Test
    void laterStepCannotSilentlyDropAPageEvenWhenSomeInputsAreAvailable() throws Exception {
        Fixture f = fixture(true);
        var other = pages.saveAndFlush(new Page("Other page", null, projects.findById(f.project).orElseThrow()));
        var pageIds = List.of(f.page, other.getId());
        var started = workflows.start(f.workspace, f.project,
                new ActionWorkflowDto.StartRequest(f.workflow.id(), f.workflow.version(), pageIds, null), "owner", "url");
        xmls.saveAndFlush(new PageXml("page.xml", "/unused.xml", "application/xml", 0L, "default", "page",
                XmlSchema.PAGE_XML, "2019-07-15", pages.findById(f.page).orElseThrow()));
        complete(started.stepRunIds().getFirst(), pageIds);
        scheduler.continueWorkflows();
        assertThat(run(started.id()).status()).isEqualTo("FAILED");
        assertThat(run(started.id()).stepRunIds()).hasSize(1);
        assertThat(runs.findByWorkspaceIdAndProjectIdOrderByCreatedDesc(f.workspace, f.project)).hasSize(1);
    }

    @Test
    void validatesOverridesVersionsActivationAndWorkspaceBoundariesBeforeStartingAnything() {
        Fixture f = fixture(false);
        var swapped = new ArrayList<>(f.workflow.steps());
        Collections.swap(swapped, 0, 1);
        assertThatThrownBy(() -> workflows.start(f.workspace, f.project,
                new ActionWorkflowDto.StartRequest(f.workflow.id(), f.workflow.version(), List.of(f.page), swapped), "owner", "url"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workflows.start(f.workspace, f.project,
                new ActionWorkflowDto.StartRequest(f.workflow.id(), 99L, List.of(f.page), null), "owner", "url"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> workflows.start("another-workspace", f.project, request(f), "owner", "url"))
                .isInstanceOf(IllegalArgumentException.class);
        assignments.deleteAll(assignments.findByWorkspaceIdOrderByCreatedAsc(f.workspace));
        assertThatThrownBy(() -> workflows.start(f.workspace, f.project, request(f), "owner", "url"))
                .isInstanceOf(SecurityException.class);
        assertThat(runs.findByWorkspaceIdAndProjectIdOrderByCreatedDesc(f.workspace, f.project)).isEmpty();
    }

    @Test
    void revokingExecutionPermissionStopsContinuationAndUnauthorizedUsersCannotEditOrCancel() throws Exception {
        Fixture f = fixture(false);
        var started = workflows.start(f.workspace, f.project, request(f), "owner", "url");
        when(access.canManageProjects(f.workspace, "other")).thenReturn(false);
        assertThatThrownBy(() -> workflows.cancel(f.workspace, f.project, started.id(), "other")).isInstanceOf(SecurityException.class);
        doThrow(new SecurityException("Denied")).when(access).requireManageProjectsAccess(f.workspace, "other");
        assertThatThrownBy(() -> workflows.delete(f.workspace, f.workflow.id(), "other")).isInstanceOf(SecurityException.class);
        complete(started.stepRunIds().getFirst(), f.page);
        when(access.canManageProjects(f.workspace, "owner")).thenReturn(false);
        scheduler.continueWorkflows();
        assertThat(run(started.id()).status()).isEqualTo("FAILED");
        assertThat(run(started.id()).stepRunIds()).hasSize(1);
    }

    @Test
    void adminWorkflowsUseNormalGlobalAvailabilityAndRequireTheirOwnActivation() {
        Fixture f = fixture(false);
        var workflow = workflows.save(null, null, new ActionWorkflowDto.SaveRequest("Global workflow", "", true,
                f.workflow.steps(), null), "owner");
        assertThat(definitions.getDefinition(workflow.processorDefinitionId()).workflowId()).isEqualTo(workflow.id());
        assertThat(actions.listAvailableDefinitions(f.workspace, "owner")).extracting(ActionDto.DefinitionResponse::id)
                .doesNotContain(workflow.processorDefinitionId());
        definitions.setGlobalAvailable(workflow.processorDefinitionId(), true, "owner");
        assertThat(actions.listAvailableDefinitions(f.workspace, "owner")).extracting(ActionDto.DefinitionResponse::id)
                .contains(workflow.processorDefinitionId());
        assertThat(workflows.listExecutable(f.workspace, f.project, "owner")).extracting(ActionWorkflowDto.Definition::id)
                .doesNotContain(workflow.id());
        var request = new ActionWorkflowDto.StartRequest(workflow.id(), workflow.version(), List.of(f.page), null);
        assertThatThrownBy(() -> workflows.start(f.workspace, f.project, request, "owner", "url")).isInstanceOf(SecurityException.class);
        assignments.deleteAll(assignments.findByWorkspaceIdOrderByCreatedAsc(f.workspace));
        actions.updateActivation(f.workspace, workflow.processorDefinitionId(),
                new ActionDto.ActivationRequest(ActionDto.ActivationScope.PROJECTS, List.of(f.project), true), "owner");
        assertThat(workflows.stepProcessors(f.workspace, f.project, workflow.id(), "owner")).hasSize(2);
        assertThatThrownBy(() -> actions.startRun(f.workspace, f.project,
                new ActionDto.StartRunRequest(f.first, List.of(f.page), Map.of(), null, null, true), "owner", "url"))
                .isInstanceOf(SecurityException.class);
        var started = workflows.start(f.workspace, f.project, request, "owner", "url");
        assertThat(started.stepRunIds()).hasSize(1);
        assertThat(workflows.listAdminRuns(workflow.id(), "owner")).extracting(ActionWorkflowDto.Run::id).contains(started.id());
        assertThatThrownBy(() -> workflows.cancelAdminRun(f.workflow.id(), started.id(), "owner")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> actions.workflowProcessor(f.workspace, f.project, workflow.id(), f.workflow.processorDefinitionId(), "owner"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> definitions.deleteDefinition(workflow.processorDefinitionId())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> actions.startRun(f.workspace, f.project,
                new ActionDto.StartRunRequest(workflow.processorDefinitionId(), List.of(f.page), Map.of(), null, null, true), "owner", "url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void workspaceScopedWorkflowCannotLeakAcrossWorkspacesAndRevocationStopsContinuation() throws Exception {
        Fixture f = fixture(false);
        Fixture other = fixture(false);
        var workflow = workflows.save(null, null, new ActionWorkflowDto.SaveRequest("Scoped workflow", "", true,
                f.workflow.steps(), null), "owner");
        var grant = definitions.assignWorkspaceAvailability(workflow.processorDefinitionId(),
                new ActionDto.WorkspaceAvailabilityRequest(f.workspace, true), "owner");
        actions.updateActivation(f.workspace, workflow.processorDefinitionId(),
                new ActionDto.ActivationRequest(ActionDto.ActivationScope.WORKSPACE, List.of(), true), "owner");
        assertThat(actions.listAvailableDefinitions(other.workspace, "owner")).extracting(ActionDto.DefinitionResponse::id)
                .doesNotContain(workflow.processorDefinitionId());
        assertThatThrownBy(() -> actions.updateActivation(other.workspace, workflow.processorDefinitionId(),
                new ActionDto.ActivationRequest(ActionDto.ActivationScope.WORKSPACE, List.of(), true), "owner"))
                .isInstanceOf(SecurityException.class);
        var request = new ActionWorkflowDto.StartRequest(workflow.id(), workflow.version(), List.of(other.page), null);
        assertThatThrownBy(() -> workflows.start(other.workspace, other.project, request, "owner", "url"))
                .isInstanceOf(SecurityException.class);
        var started = workflows.start(f.workspace, f.project,
                new ActionWorkflowDto.StartRequest(workflow.id(), workflow.version(), List.of(f.page), null), "owner", "url");
        complete(started.stepRunIds().getFirst(), f.page);
        definitions.removeWorkspaceAvailability(workflow.processorDefinitionId(), grant.id());
        scheduler.continueWorkflows();
        assertThat(run(started.id()).status()).isEqualTo("FAILED");
        assertThat(run(started.id()).stepRunIds()).hasSize(1);
    }

    @Test
    void scopeCannotBeBroaderThanConstituentProcessorsAndNestedWorkflowsAreRejected() {
        Fixture f = fixture(false);
        definitions.setGlobalAvailable(f.first, false, "owner");
        definitions.assignWorkspaceAvailability(f.first, new ActionDto.WorkspaceAvailabilityRequest(f.workspace, true), "owner");
        var workflow = workflows.save(null, null, new ActionWorkflowDto.SaveRequest("Restricted inputs", "", true,
                f.workflow.steps(), null), "owner");
        assertThatThrownBy(() -> definitions.setGlobalAvailable(workflow.processorDefinitionId(), true, "owner"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> definitions.assignWorkspaceAvailability(workflow.processorDefinitionId(),
                new ActionDto.WorkspaceAvailabilityRequest("unavailable", true), "owner"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workflows.save(null, null, new ActionWorkflowDto.SaveRequest("Nested", "", true,
                List.of(new ActionWorkflowDto.Step(workflow.processorDefinitionId(), Map.of(), null)), null), "owner"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminAuthoringRequiresGlobalAdminAndWorkspaceEditorsCannotChangePublishedWorkflows() {
        Fixture f = fixture(false);
        definitions.setGlobalAvailable(f.workflow.processorDefinitionId(), true, "owner");
        assertThatThrownBy(() -> workflows.save(f.workspace, f.workflow.id(), new ActionWorkflowDto.SaveRequest("Changed", "", true,
                f.workflow.steps(), f.workflow.version()), "owner")).isInstanceOf(SecurityException.class);
        when(admin.isGlobalAdmin()).thenReturn(false);
        assertThatThrownBy(() -> workflows.save(null, null, new ActionWorkflowDto.SaveRequest("Denied", "", true,
                f.workflow.steps(), null), "owner")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> workflows.get(null, f.workflow.id(), "owner")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> workflows.list(null, "owner")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> workflows.listAdminRuns(f.workflow.id(), "owner")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> workflows.cancelAdminRun(f.workflow.id(), "unknown", "owner")).isInstanceOf(SecurityException.class);
    }

    @Test
    void normalActionEnableControlsCanReenableDisabledWorkflowDefinitions() {
        Fixture f = fixture(false);
        var workflow = workflows.save(null, null, new ActionWorkflowDto.SaveRequest("Initially disabled", "", false,
                f.workflow.steps(), null), "owner");
        definitions.setGlobalAvailable(workflow.processorDefinitionId(), true, "owner");
        definitions.setEnabled(workflow.processorDefinitionId(), true, "owner");
        actions.updateActivation(f.workspace, workflow.processorDefinitionId(),
                new ActionDto.ActivationRequest(ActionDto.ActivationScope.WORKSPACE, List.of(), true), "owner");
        var started = workflows.start(f.workspace, f.project,
                new ActionWorkflowDto.StartRequest(workflow.id(), workflow.version(), List.of(f.page), null), "owner", "url");
        assertThat(started.stepRunIds()).hasSize(1);
        assertThat(workflows.cancelAdminRun(workflow.id(), started.id(), "owner").status()).isEqualTo("CANCELLED");
    }

    @Test
    void imageTypesPersistAcrossWorkflowStepsAndDetectLaterOutputsAtRuntime() throws Exception {
        Fixture f = fixture(false);
        allowImages(f.first); allowImages(f.second);
        var workflow = workflows.save(f.workspace, f.workflow.id(), new ActionWorkflowDto.SaveRequest("Typed workflow", "", true,
                List.of(new ActionWorkflowDto.Step(f.first, Map.of(), null, List.of(ImageType.COLOR)),
                        new ActionWorkflowDto.Step(f.second, Map.of(), null, List.of(ImageType.BINARY))), f.workflow.version()), "owner");
        var color = image(f, "original", java.awt.image.BufferedImage.TYPE_INT_RGB);
        var request = new ActionWorkflowDto.StartRequest(workflow.id(), workflow.version(), List.of(f.page), null);
        jdbc.update("UPDATE page_images SET image_type = NULL WHERE id = ?", color.getId());
        assertThat(workflows.preview(f.workspace, f.project, request, "owner").pages()).hasSize(1);
        assertThat(images.findById(color.getId()).orElseThrow().getImageType()).isNull();
        var started = workflows.start(f.workspace, f.project, request, "owner", "http://backend/api/v1");
        assertThat(images.findById(color.getId()).orElseThrow().getImageType()).isEqualTo(ImageType.COLOR);
        assertThat(started.steps().getLast().imageTypes()).containsExactly(ImageType.BINARY);
        var firstRun = runs.findById(started.stepRunIds().getFirst()).orElseThrow();
        assertThat(firstRun.getInputRequirementsJson()).contains("COLOR", "REQUIRED");
        // A later image is absent at save/start time, then becomes available after the first step.
        var binary = image(f, "generated", java.awt.image.BufferedImage.TYPE_BYTE_BINARY);
        complete(firstRun.getId(), f.page);
        workflows.advance(started.id());
        var resumed = run(started.id());
        assertThat(resumed.stepRunIds()).hasSize(2);
        var child = runs.findById(resumed.stepRunIds().getLast()).orElseThrow();
        assertThat(child.getInputRequirementsJson()).contains("BINARY", "REQUIRED");
        child.setStatus(ActionRun.Status.RUNNING);
        child.setSecretHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("secret".getBytes(StandardCharsets.UTF_8))));
        child.setSecretExpiresAt(LocalDateTime.now().plusMinutes(10)); runs.saveAndFlush(child);
        var input = actions.buildMachineInput(child.getId(), "Bearer secret", "http://backend/api/v1");
        assertThat(input.pages().getFirst().images()).extracting(ActionDto.MachinePageFile::id).containsExactly(binary.getId());
        assertThat(input.pages().getFirst().images().getFirst().imageType()).isEqualTo(ImageType.BINARY);
        assertThatThrownBy(() -> actions.resolveMachineFile(child.getId(), "Bearer secret", "images", color.getId())).isInstanceOf(SecurityException.class);
        images.deleteById(binary.getId());
        assertThatThrownBy(() -> actions.buildMachineInput(child.getId(), "Bearer secret", "http://backend/api/v1"))
                .hasMessageContaining("BINARY").hasMessageContaining("every page");
    }

    @Test
    void workflowStopsBeforeCreatingChildIfLaterRequiredTypeIsMissing() throws Exception {
        Fixture f = fixture(false); allowImages(f.first); allowImages(f.second);
        var workflow = workflows.save(f.workspace, f.workflow.id(), new ActionWorkflowDto.SaveRequest("Typed workflow", "", true,
                List.of(new ActionWorkflowDto.Step(f.first, Map.of(), null, List.of(ImageType.COLOR)),
                        new ActionWorkflowDto.Step(f.second, Map.of(), null, List.of(ImageType.BINARY))), f.workflow.version()), "owner");
        image(f, "misleading-binary-name", java.awt.image.BufferedImage.TYPE_INT_RGB);
        var started = workflows.start(f.workspace, f.project,
                new ActionWorkflowDto.StartRequest(workflow.id(), workflow.version(), List.of(f.page), null), "owner", "http://backend/api/v1");
        complete(started.stepRunIds().getFirst(), f.page);
        assertThatThrownBy(() -> workflows.advance(started.id())).hasMessageContaining("BINARY").hasMessageContaining("Page");
        assertThat(run(started.id()).stepRunIds()).hasSize(1);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void lazyDetectionCompletesWhenOuterTransactionsFillThePool(boolean readOnly) throws Exception {
        Fixture f = fixture(false);
        var first = image(f, "first", java.awt.image.BufferedImage.TYPE_BYTE_BINARY);
        var second = image(f, "second", java.awt.image.BufferedImage.TYPE_BYTE_BINARY);
        var ids = List.of(first.getId(), second.getId());
        jdbc.update("UPDATE page_images SET image_type = NULL WHERE id IN (?, ?)", first.getId(), second.getId());
        var source = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class);
        var configuration = new com.zaxxer.hikari.HikariConfig();
        configuration.setJdbcUrl(source.getJdbcUrl());
        configuration.setUsername(source.getUsername());
        configuration.setPassword(source.getPassword());
        configuration.setMaximumPoolSize(2);
        configuration.setConnectionTimeout(1000);
        try (var pool = new com.zaxxer.hikari.HikariDataSource(configuration);
             var workers = Executors.newFixedThreadPool(2)) {
            var limitedJdbc = new org.springframework.jdbc.core.JdbcTemplate(pool);
            var transactions = new org.springframework.jdbc.datasource.DataSourceTransactionManager(pool);
            var metadata = new de.uniwue.zpd.dachs.larex.backend.service.image.ImageTypeService(uploadPaths, limitedJdbc);
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            var pendingImages = List.of(images.findById(first.getId()).orElseThrow(), images.findById(second.getId()).orElseThrow());
            var jobs = new ArrayList<java.util.concurrent.Future<ImageType>>();
            for (var pending : pendingImages) {
                jobs.add(workers.submit(() -> {
                    var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
                    transaction.setReadOnly(readOnly);
                    return transaction.execute(status -> {
                        limitedJdbc.queryForObject("SELECT 1", Integer.class);
                        try { barrier.await(5, TimeUnit.SECONDS); }
                        catch (Exception error) { throw new IllegalStateException(error); }
                        return metadata.typeOf(pending);
                    });
                }));
            }
            for (var job : jobs) assertThat(job.get(10, TimeUnit.SECONDS)).isEqualTo(ImageType.BINARY);
        }
        for (String id : ids) {
            assertThat(images.findById(id).orElseThrow().getImageType()).isEqualTo(readOnly ? null : ImageType.BINARY);
        }
        // Read-only callers compute the answer; the existing worker persists it later.
        if (readOnly) {
            imageTypes.backfillBatch();
            for (String id : ids) assertThat(images.findById(id).orElseThrow().getImageType()).isEqualTo(ImageType.BINARY);
        }
    }

    @Test
    void metadataBackfillUpdatesOnlyTypeAndHandlesOldImagesOnDemand() throws Exception {
        Fixture f = fixture(false);
        var image = image(f, "scan", java.awt.image.BufferedImage.TYPE_BYTE_BINARY);
        var updated = images.findById(image.getId()).orElseThrow().getUpdated();
        jdbc.update("UPDATE page_images SET image_type = NULL WHERE id = ?", image.getId());
        var uninspected = images.findById(image.getId()).orElseThrow();
        assertThat(uninspected.getImageType()).isNull();
        assertThat(imageTypes.typeOf(uninspected)).isEqualTo(ImageType.BINARY);
        var inspected = images.findById(image.getId()).orElseThrow();
        assertThat(inspected.getImageType()).isEqualTo(ImageType.BINARY);
        assertThat(inspected.getUpdated()).isEqualTo(updated);
        jdbc.update("UPDATE page_images SET image_type = NULL WHERE id = ?", image.getId());
        imageTypes.backfillBatch();
        assertThat(images.findById(image.getId()).orElseThrow().getImageType()).isEqualTo(ImageType.BINARY);
        assertThat(images.findById(image.getId()).orElseThrow().getUpdated()).isEqualTo(updated);
    }

    @Test
    void reclassificationMigrationRechecksGrayscaleWithoutChangingContentOrCleanedHistory() throws Exception {
        Fixture f = fixture(false);
        var image = image(f, "ocropus", java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
        var updated = images.findById(image.getId()).orElseThrow().getUpdated();
        jdbc.update("UPDATE page_images SET image_type = 'GRAYSCALE' WHERE id = ?", image.getId());
        var activeWorkflow = workflows.start(f.workspace, f.project, request(f), "owner", "http://backend/api/v1");
        var active = runs.findById(activeWorkflow.stepRunIds().getFirst()).orElseThrow();
        Fixture historicalFixture = fixture(false);
        var historicalWorkflow = workflows.start(historicalFixture.workspace, historicalFixture.project, request(historicalFixture), "owner", "http://backend/api/v1");
        var historical = runs.findById(historicalWorkflow.stepRunIds().getFirst()).orElseThrow();
        historical.setSnapshotCleanedAt(LocalDateTime.now()); runs.saveAndFlush(historical);
        String insertSnapshot = """
                INSERT INTO action_input_snapshots
                    (id, run_id, input_id, page_name, source_page_id, file_type, source_file_id,
                     file_name, mime_type, file_size, checksum_sha256, snapshot_path, position, image_type)
                VALUES (?, ?, 'input', 'Page', ?, 'images', ?, 'image.png', 'image/png', 1,
                        'checksum', ?, 0, 'GRAYSCALE')
                """;
        jdbc.update(insertSnapshot, "active-gray", active.getId(), f.page, image.getId(), image.getFilePath());
        jdbc.update(insertSnapshot, "cleaned-gray", historical.getId(), f.page, image.getId(), "deleted.png");
        // Exercise the actual upgrade script against the full application schema and existing rows.
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ClassPathResource("db/migration/V48__recheck_grayscale_image_types.sql"));
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT image_type FROM page_images WHERE id = ?", String.class, image.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT image_type FROM action_input_snapshots WHERE id = 'active-gray'", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT image_type FROM action_input_snapshots WHERE id = 'cleaned-gray'", String.class)).isEqualTo("GRAYSCALE");
        assertThat(imageTypes.typeOf(images.findById(image.getId()).orElseThrow())).isEqualTo(ImageType.BINARY);
        assertThat(images.findById(image.getId()).orElseThrow().getUpdated()).isEqualTo(updated);
    }

    private void allowImages(String id) {
        var processor = processors.findById(id).orElseThrow();
        processor.setParsedJson(processor.getParsedJson().replace("\"inputs\":{", "\"inputs\":{\"images\":{\"level\":\"optional\"},"));
        processor.setAcceptsImages(true); processors.saveAndFlush(processor);
    }

    private PageImage image(Fixture f, String variant, int bufferedType) throws Exception {
        var file = storage.storeBufferedImage(new java.awt.image.BufferedImage(4, 4, bufferedType), "png", variant + ".png", f.workspace, f.project, "owner");
        var image = new PageImage(file.originalFilename(), file.storagePath(), file.mimeType(), file.sizeBytes(), variant, "Page", pages.findById(f.page).orElseThrow());
        image.setImageType(file.imageType()); return images.saveAndFlush(image);
    }

    private Fixture fixture(boolean requireXml) {
        String workspace = UUID.randomUUID().toString();
        var library = libraries.saveAndFlush(new Library(workspace, "Workflow test"));
        var project = projects.saveAndFlush(new Project("Workflow project", null, library));
        var page = pages.saveAndFlush(new Page("Page", null, project));
        String first = processor(workspace, false);
        String second = processor(workspace, requireXml);
        var workflow = workflows.save(workspace, null, new ActionWorkflowDto.SaveRequest("Test workflow", "", true,
                List.of(new ActionWorkflowDto.Step(first, Map.of("threshold", 0.4), null),
                        new ActionWorkflowDto.Step(second, Map.of("threshold", 0.7), null),
                        new ActionWorkflowDto.Step(first, Map.of("threshold", 0.2), null)), null), "owner");
        return new Fixture(workspace, project.getId(), page.getId(), first, second, workflow);
    }

    private String processor(String workspace, boolean requireXml) {
        var processor = new ActionProcessorDefinition();
        processor.setProcessorKey("workflow-" + UUID.randomUUID());
        processor.setName("Workflow test processor");
        processor.setYamlSource("");
        processor.setParsedJson("""
            {"targets":["PAGE"],"inputs":{"xml":{"level":"%s"}},
             "parameters":{"threshold":{"type":"number","default":0.5}},
             "concurrency":{"maxActiveRuns":1,"scope":"PROJECT"}}
            """.formatted(requireXml ? "required" : "optional"));
        processor.setEndpointUrl("http://processor/dispatch");
        processor.setGlobalAvailable(true);
        processor.setLockMode(ActionProcessorDefinition.LockMode.NONE);
        processor.setCreatedByUserId("owner");
        processor.setUpdatedByUserId("owner");
        processor = processors.saveAndFlush(processor);
        var assignment = new ActionProcessorAssignment();
        assignment.setWorkspaceId(workspace);
        assignment.setProcessorDefinition(processor);
        assignment.setCreatedByUserId("owner");
        assignments.saveAndFlush(assignment);
        return processor.getId();
    }
    private ActionWorkflowDto.StartRequest request(Fixture f) {
        return new ActionWorkflowDto.StartRequest(f.workflow.id(), f.workflow.version(), List.of(f.page), null);
    }
    private void setStatus(String id, ActionRun.Status status) {
        var run = runs.findById(id).orElseThrow();
        run.setStatus(status);
        runs.saveAndFlush(run);
    }
    private ActionWorkflowDto.Run run(String id) {
        var saved = workflowRuns.findById(id).orElseThrow();
        return workflows.listRuns(saved.getWorkspaceId(), saved.getProjectId(), "owner").stream()
                .filter(run -> run.id().equals(id)).findFirst().orElseThrow();
    }
    private void complete(String id, String pageId) throws Exception {
        complete(id, List.of(pageId));
    }
    private void complete(String id, List<String> pageIds) throws Exception {
        var child = runs.findById(id).orElseThrow();
        child.setStatus(ActionRun.Status.RUNNING);
        child.setSecretHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("secret".getBytes(StandardCharsets.UTF_8))));
        child.setSecretExpiresAt(LocalDateTime.now().plusMinutes(10));
        runs.saveAndFlush(child);
        for (String pageId : pageIds) {
            actions.receiveResults(id, "Bearer secret", new ActionDto.ResultManifest(1, "running", null, pageId, List.of(), List.of()), new LinkedMultiValueMap<>());
        }
        actions.receiveResults(id, "Bearer secret", new ActionDto.ResultManifest(1, "completed", null, null, List.of(), List.of()), new LinkedMultiValueMap<>());
        assertThat(runs.findById(id).orElseThrow().getStatus()).isEqualTo(ActionRun.Status.COMPLETED);
    }
    private record Fixture(String workspace, String project, String page, String first, String second, ActionWorkflowDto.Definition workflow) {}
}
