package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionWorkflowDto;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget;
import de.uniwue.zpd.dachs.larex.backend.repository.action.*;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class ActionWorkflowService {
    private static final TypeReference<List<ActionWorkflowDto.Step>> STEPS = new TypeReference<>() {};
    private static final TypeReference<List<String>> IDS = new TypeReference<>() {};
    private final ActionWorkflowRepository workflows;
    private final ActionWorkflowRunRepository workflowRuns;
    private final ActionRunRepository runs;
    private final ActionProcessorDefinitionRepository processors;
    private final ProjectRepository projects;
    private final WorkspaceAccessService access;
    private final ActionRunService actions;
    private final ActionDefinitionService definitions;
    private final ActionRunPayloadService payload;
    private final ObjectMapper mapper;
    private final GlobalAdminService admin;
    private final ActionWorkspaceActivationService activation;
    private final ActionProcessorWorkspaceAvailabilityRepository availability;
    private final ActionProcessorAssignmentRepository assignments;
    private final ActionAuditService audit;
    private final ActionWorkflowRunDismissalRepository dismissals;

    public ActionWorkflowService(ActionWorkflowRepository workflows, ActionWorkflowRunRepository workflowRuns,
                                 ActionRunRepository runs, ActionProcessorDefinitionRepository processors,
                                 ProjectRepository projects, WorkspaceAccessService access, ActionRunService actions,
                                 ActionDefinitionService definitions, ActionRunPayloadService payload, ObjectMapper mapper,
                                 GlobalAdminService admin, ActionWorkspaceActivationService activation,
                                 ActionProcessorWorkspaceAvailabilityRepository availability, ActionProcessorAssignmentRepository assignments, ActionAuditService audit, ActionWorkflowRunDismissalRepository dismissals) {
        this.workflows = workflows;
        this.workflowRuns = workflowRuns;
        this.runs = runs;
        this.processors = processors;
        this.projects = projects;
        this.access = access;
        this.actions = actions;
        this.definitions = definitions;
        this.payload = payload;
        this.mapper = mapper;
        this.admin = admin;
        this.activation = activation;
        this.availability = availability;
        this.assignments = assignments;
        this.audit = audit;
        this.dismissals = dismissals;
    }

    @Transactional(readOnly = true)
    public List<ActionWorkflowDto.Definition> list(String workspaceId, String userId) {
        if (workspaceId == null) {
            requireAdmin();
            return processors.findAllByOrderByNameAsc().stream().filter(ActionProcessorDefinition::isWorkflow)
                    .map(definition -> definition(definition.getWorkflow())).toList();
        }
        access.requireWorkspaceAccess(workspaceId, userId);
        return workflows.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .filter(workflow -> {
                    var action = action(workflow.getId());
                    return !action.isGlobalAvailable() && availability.findByProcessorDefinitionIdOrderByWorkspaceIdAsc(action.getId()).stream()
                            .noneMatch(grant -> grant.isEnabled() && !workspaceId.equals(grant.getWorkspaceId()));
                }).map(this::definition).toList();
    }

    @Transactional(readOnly = true)
    public ActionWorkflowDto.Definition get(String workspaceId, String id, String userId) {
        if (workspaceId == null) requireAdmin();
        else access.requireManageProjectsAccess(workspaceId, userId);
        var workflow = requireWorkflow(workspaceId, id);
        return definition(workflow);
    }

    @Transactional(readOnly = true)
    public ActionWorkflowDto.TestResponse test(ActionWorkflowDto.TestRequest request) {
        requireAdmin();
        requireSteps(request.steps());
        var checks = new ArrayList<ActionWorkflowDto.StepCheck>();
        var endpoints = new HashMap<String, ActionDto.HealthCheckResponse>();
        for (var step : request.steps()) {
            String name = step.processorDefinitionId();
            try {
                var processor = requireProcessor(step.processorDefinitionId());
                name = processor.getName();
                if (!processor.isEnabled()) throw new IllegalArgumentException("Processor is disabled");
                validateStepImageTypes(step);
                actions.resolveRunParameters(processor, step.parameters(), step.imageVariantSelection(), step.imageTypes());
                var endpoint = endpoints.computeIfAbsent(processor.getId(), definitions::testEndpoint);
                checks.add(new ActionWorkflowDto.StepCheck(checks.size() + 1, processor.getId(), name,
                        endpoint.ok(), endpoint.message(), endpoint));
            } catch (RuntimeException exception) {
                checks.add(new ActionWorkflowDto.StepCheck(checks.size() + 1, step.processorDefinitionId(), name,
                        false, ActionRunService.redactProcessorSecrets(exception.getMessage()), null));
            }
        }
        return new ActionWorkflowDto.TestResponse(checks.stream().allMatch(ActionWorkflowDto.StepCheck::ok), List.copyOf(checks));
    }

    public ActionWorkflowDto.Definition save(String workspaceId, String id, ActionWorkflowDto.SaveRequest request, String userId) {
        if (workspaceId == null) requireAdmin();
        else access.requireManageProjectsAccess(workspaceId, userId);
        if (request.name() == null || request.name().isBlank() || request.name().trim().length() > 120)
            throw new IllegalArgumentException("A workflow name of at most 120 characters is required");
        requireSteps(request.steps());
        Set<String> available = new HashSet<>((workspaceId == null ? definitions.listDefinitions() : actions.listAvailableDefinitions(workspaceId, userId)).stream()
                .map(ActionDto.DefinitionResponse::id).toList());
        for (var step : request.steps()) {
            validateStepImageTypes(step);
            if (!available.contains(step.processorDefinitionId())) throw new IllegalArgumentException("Workflow processor is unavailable");
        }
        var action = id == null ? new ActionProcessorDefinition() : processors.findByWorkflowIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
        var workflow = id == null ? new ActionWorkflow() : requireWorkflow(workspaceId, id);
        if (id != null && workspaceId != null) requireWorkspaceEditable(workspaceId, workflow, action);
        if (id != null && !Objects.equals(workflow.getVersion(), request.version()))
            throw new IllegalStateException("This workflow changed. Reload before saving.");
        if (id == null) workflow.setWorkspaceId(workspaceId);
        workflow.setName(request.name().trim());
        workflow.setDescription(request.description());
        workflow.setEnabled(request.enabled());
        workflow.setStepsJson(payload.writeJson(request.steps()));
        workflows.saveAndFlush(workflow);
        action.setWorkflow(workflow);
        action.setName(workflow.getName());
        action.setDescription(workflow.getDescription());
        action.setEnabled(request.enabled());
        // Only a snapshot: reads derive the role from the current steps (see effectiveExecuteRole).
        action.setExecuteRole(definitions.effectiveExecuteRole(action));
        action.setUpdatedByUserId(userId);
        if (id == null) {
            action.setProcessorKey("workflow-" + workflow.getId());
            action.setCreatedByUserId(userId);
            action.setEndpointUrl("urn:larex:workflow");
            action.setYamlSource("");
            action.setLockMode(ActionProcessorDefinition.LockMode.NONE);
        }
        // The catalogue represents the workflow; execution uses its individual step definitions.
        action.setParsedJson(payload.writeJson(Map.of("tags", List.of(), "targets", List.of("PAGE"))));
        if (action.isGlobalAvailable()) definitions.requireWorkflowAvailability(action, null);
        else if (action.getId() != null) availability.findByProcessorDefinitionIdOrderByWorkspaceIdAsc(action.getId()).stream()
                .filter(ActionProcessorWorkspaceAvailability::isEnabled)
                .forEach(grant -> definitions.requireWorkflowAvailability(action, grant.getWorkspaceId()));
        processors.saveAndFlush(action);
        if (id == null && workspaceId != null) {
            var grant = new ActionProcessorWorkspaceAvailability();
            grant.setProcessorDefinition(action);
            grant.setWorkspaceId(workspaceId);
            grant.setCreatedByUserId(userId);
            availability.save(grant);
            var assignment = new ActionProcessorAssignment();
            assignment.setProcessorDefinition(action);
            assignment.setWorkspaceId(workspaceId);
            assignment.setCreatedByUserId(userId);
            assignments.save(assignment);
        }
        audit.record(id == null ? "ACTION_WORKFLOW_CREATE" : "ACTION_WORKFLOW_UPDATE", "SUCCESS", userId,
                action.getId(), null, workspaceId, null, Map.of("stepCount", request.steps().size()));
        return definition(workflow);
    }

    public void delete(String workspaceId, String id, String userId) {
        if (workspaceId == null) {
            requireAdmin();
            definitions.deleteDefinition(action(id).getId());
            return;
        }
        access.requireManageProjectsAccess(workspaceId, userId);
        var action = processors.findByWorkflowIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
        requireWorkspaceEditable(workspaceId, requireWorkflow(workspaceId, id), action);
        if (workflowRuns.existsByWorkflowIdAndStatusIn(id, List.of("RUNNING", "CANCEL_REQUESTED")))
            throw new IllegalStateException("Workflow has active runs and cannot be deleted");
        assignments.deleteAll(assignments.findByDefinitionIds(List.of(action.getId())));
        availability.deleteAll(availability.findByProcessorDefinitionIdOrderByWorkspaceIdAsc(action.getId()));
        processors.delete(action);
    }

    @Transactional(readOnly = true)
    public List<ActionWorkflowDto.Definition> listExecutable(String workspaceId, String projectId, String userId) {
        requireProject(workspaceId, projectId, userId);
        return activation.listActivatedDefinitions(workspaceId, projectId).stream()
                .filter(ActionProcessorDefinition::isWorkflow).map(action -> definition(action.getWorkflow())).toList();
    }

    @Transactional(readOnly = true)
    public List<ActionDto.ExecutableProcessorResponse> stepProcessors(String workspaceId, String projectId, String workflowId, String userId) {
        requireProject(workspaceId, projectId, userId);
        var workflow = requirePublished(workspaceId, projectId, workflowId);
        return steps(workflow.getStepsJson()).stream().map(ActionWorkflowDto.Step::processorDefinitionId).distinct()
                .map(id -> actions.workflowProcessor(workspaceId, projectId, workflowId, id, userId)).toList();
    }

    @Transactional(readOnly = true)
    public ActionDto.RunImpactResponse preview(String workspaceId, String projectId, ActionWorkflowDto.StartRequest request, String userId) {
        var steps = prepare(workspaceId, projectId, request, userId);
        var impact = actions.previewWorkflowStepImpact(workspaceId, projectId, request.workflowId(), stepRequest(steps.getFirst(), request.pageIds()), userId);
        // A later step may consume inputs produced by an earlier one. Review all overwrite declarations
        // without rejecting future steps because their inputs do not exist yet.
        Set<ActionDto.AnnotationLevel> overwritten = EnumSet.noneOf(ActionDto.AnnotationLevel.class);
        boolean unknown = false;
        for (var step : steps) {
            var processor = requireProcessor(step.processorDefinitionId());
            if (!processor.isOutputsXml()) continue;
            var declarations = definitions.readOverwriteDeclarations(processor).get(ActionTarget.PAGE);
            if (declarations == null || declarations.isEmpty()) unknown = true;
            else overwritten.addAll(ActionAnnotationImpact.expand(declarations));
        }
        final boolean unknownImpact = unknown;
        return new ActionDto.RunImpactResponse(ActionTarget.PAGE, impact.pages().stream().map(page ->
                new ActionDto.ImpactPage(page.pageId(), page.name(), page.targetSelection(),
                        List.copyOf(overwritten), unknownImpact ? ActionDto.WarningPrecision.UNKNOWN : ActionDto.WarningPrecision.DECLARED,
                        unknownImpact || !overwritten.isEmpty() || page.affected())).toList(), impact.skippedPages());
    }

    public ActionWorkflowDto.Run start(String workspaceId, String projectId, ActionWorkflowDto.StartRequest request,
                                      String userId, String publicApiBaseUrl) {
        requireProject(workspaceId, projectId, userId);
        var published = processors.findByWorkflowIdForUpdate(request.workflowId())
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
        activation.requireActivated(published, workspaceId, projectId);
        var steps = prepare(workspaceId, projectId, request, userId);
        var impact = actions.previewWorkflowStepImpact(workspaceId, projectId, request.workflowId(), stepRequest(steps.getFirst(), request.pageIds()), userId);
        var pageIds = impact.pages().stream().map(ActionDto.ImpactPage::pageId).toList();
        if (pageIds.isEmpty()) throw new IllegalArgumentException("No eligible pages remain for this workflow");
        if (request.pageIds() != null && !request.pageIds().isEmpty()
                && !new HashSet<>(pageIds).equals(new HashSet<>(request.pageIds())))
            throw new IllegalStateException("Page eligibility changed. Review the workflow again.");
        var workflow = requirePublished(workspaceId, projectId, request.workflowId());
        var run = new ActionWorkflowRun();
        run.setWorkspaceId(workspaceId);
        run.setProjectId(projectId);
        run.setWorkflowId(workflow.getId());
        run.setWorkflowName(workflow.getName());
        run.setCreatedByUserId(userId);
        run.setStepsJson(payload.writeJson(steps));
        run.setPageIdsJson(payload.writeJson(pageIds));
        run.setPublicApiBaseUrl(publicApiBaseUrl);
        workflowRuns.save(run);
        advance(run);
        return response(run, userId);
    }

    @Transactional(readOnly = true)
    public List<ActionWorkflowDto.Run> listAdminRuns(String workflowId, String userId) {
        requireAdmin();
        action(workflowId);
        return workflowRuns.findByWorkflowIdOrderByCreatedDesc(workflowId, PageRequest.of(0, 100)).stream()
                .map(run -> response(run, userId)).toList();
    }

    public ActionWorkflowDto.Run cancelAdminRun(String workflowId, String runId, String userId) {
        requireAdmin();
        var run = workflowRuns.findById(runId).orElseThrow(() -> new IllegalArgumentException("Workflow run not found"));
        if (!workflowId.equals(run.getWorkflowId())) throw new IllegalArgumentException("Workflow run not found");
        return cancel(run.getWorkspaceId(), run.getProjectId(), runId, userId);
    }

    @Transactional(readOnly = true)
    public List<ActionWorkflowDto.Run> listRuns(String workspaceId, String projectId, String userId) {
        requireProject(workspaceId, projectId, userId);
        return visibleRunHistory(workspaceId, projectId).stream().map(run -> response(run, userId)).toList();
    }

    @Transactional(readOnly = true)
    public List<ActionWorkflowDto.Run> listWorkspaceRuns(String workspaceId, String userId) {
        access.requireWorkspaceAccess(workspaceId, userId);
        var history = visibleRunHistory(workspaceId, null);
        var parentIds = history.stream().map(ActionWorkflowRun::getId).toList();
        var dismissed = parentIds.isEmpty() ? Set.<String>of() : dismissals.findRunIdsByUserIdAndRunIds(userId, parentIds);
        return history.stream().filter(run -> active(run) || !dismissed.contains(run.getId()))
                .map(run -> response(run, userId)).toList();
    }

    private List<ActionWorkflowRun> visibleRunHistory(String workspaceId, String projectId) {
        var activeStatuses = List.of("RUNNING", "CANCEL_REQUESTED");
        var active = workflowRuns.findByWorkspaceIdAndStatusInOrderByCreatedDesc(workspaceId, activeStatuses).stream()
                .filter(run -> projectId == null || projectId.equals(run.getProjectId()));
        var terminal = projectId == null
                ? workflowRuns.findByWorkspaceIdAndStatusNotInOrderByCreatedDesc(workspaceId, activeStatuses, PageRequest.of(0, 100))
                : workflowRuns.findByWorkspaceIdAndProjectIdAndStatusNotInOrderByCreatedDesc(workspaceId, projectId, activeStatuses, PageRequest.of(0, 100));
        return java.util.stream.Stream.concat(active, terminal.stream())
                .sorted(Comparator.comparing(ActionWorkflowRun::getCreated).reversed()).toList();
    }

    public void dismiss(String workspaceId, String projectId, String id, String userId) {
        requireProject(workspaceId, projectId, userId);
        var run = workflowRuns.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Workflow run not found"));
        if (!workspaceId.equals(run.getWorkspaceId()) || !projectId.equals(run.getProjectId())) throw new IllegalArgumentException("Workflow run not found");
        if (active(run)) throw new IllegalStateException("Only completed workflows can be dismissed");
        if (!dismissals.existsByRunIdAndUserId(run.getId(), userId)) {
            var dismissal = new ActionWorkflowRunDismissal();
            dismissal.setRun(run);
            dismissal.setUserId(userId);
            dismissals.save(dismissal);
        }
    }

    public ActionWorkflowDto.Run cancel(String workspaceId, String projectId, String id, String userId) {
        requireProject(workspaceId, projectId, userId);
        var run = workflowRuns.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Workflow run not found"));
        if (!workspaceId.equals(run.getWorkspaceId()) || !projectId.equals(run.getProjectId())) throw new IllegalArgumentException("Workflow run not found");
        if (!canCancel(run, userId)) throw new SecurityException("Only the workflow initiator or a curator can cancel this workflow");
        if (active(run)) {
            run.setStatus("CANCEL_REQUESTED");
            var ids = ids(run.getStepRunIdsJson());
            if (!ids.isEmpty()) {
                var child = runs.findById(ids.getLast()).orElse(null);
                if (child != null && !child.getStatus().isTerminal()) actions.cancelRun(workspaceId, projectId, child.getId(), userId, false);
            }
            advance(run);
        }
        return response(run, userId);
    }

    /** One transaction per workflow. The row lock serializes continuation/cancellation across backend
     * instances; creating a child and recording its ID commit atomically. */
    public void advance(String id) {
        advance(id, null);
    }

    public void advance(String id, Long expectedVersion) {
        workflowRuns.findByIdForUpdate(id)
                .filter(run -> expectedVersion == null || Objects.equals(run.getVersion(), expectedVersion))
                .ifPresent(this::advance);
    }

    private void advance(ActionWorkflowRun run) {
        if (!active(run)) return;
        var stepIds = new ArrayList<>(ids(run.getStepRunIdsJson()));
        if (!stepIds.isEmpty()) {
            ActionRun previous = runs.findById(stepIds.getLast()).orElseThrow(() -> new IllegalStateException("A workflow step run was removed"));
            if (!previous.getStatus().isTerminal()) return;
            if ("CANCEL_REQUESTED".equals(run.getStatus()) || previous.getStatus() == ActionRun.Status.CANCELLED) {
                finish(run, "CANCELLED", null);
                return;
            }
            if (!new HashSet<>(payload.readPageIds(previous)).equals(new HashSet<>(ids(run.getPageIdsJson())))) {
                finish(run, "FAILED", "Step " + stepIds.size() + " changed the workflow page selection. No later steps were started.");
                return;
            }
            if (previous.getStatus() != ActionRun.Status.COMPLETED) {
                finish(run, "FAILED", "Step " + stepIds.size() + " stopped with " + previous.getStatus() + ". No later steps were started.");
                return;
            }
        }
        if ("CANCEL_REQUESTED".equals(run.getStatus())) { finish(run, "CANCELLED", null); return; }
        var steps = steps(run.getStepsJson());
        if (stepIds.size() == steps.size()) { finish(run, "COMPLETED", null); return; }
        var pageIds = ids(run.getPageIdsJson());
        var step = steps.get(stepIds.size());
        // Steps run as the user who started the workflow, so their role is checked again for every step: it may
        // have been demoted, or the step's Action may have become curator-only, since the workflow started.
        var processor = requireProcessor(step.processorDefinitionId());
        if (!actions.canExecute(processor, run.getWorkspaceId(), run.getCreatedByUserId())) {
            String role = processor.getExecuteRole() == ActionProcessorDefinition.ExecuteRole.CURATOR ? "curator access" : "workspace access";
            finish(run, "FAILED", "Step " + (stepIds.size() + 1) + " (" + processor.getName() + ") requires " + role
                    + ", which the user who started this workflow no longer has. No later steps were started.");
            return;
        }
        // startRun rechecks permission, activation, input availability and parameters for every step.
        var child = actions.startWorkflowStep(run, stepRequest(step, pageIds)).run();
        if (!new HashSet<>(child.pageIds()).equals(new HashSet<>(pageIds)))
            throw new IllegalStateException("Not all workflow pages satisfy step " + (stepIds.size() + 1) + " inputs");
        stepIds.add(child.id());
        run.setStepRunIdsJson(payload.writeJson(stepIds));
    }

    public void fail(String id, Long expectedVersion, String message) {
        workflowRuns.findByIdForUpdate(id).filter(ActionWorkflowService::active)
                .filter(run -> Objects.equals(run.getVersion(), expectedVersion))
                .ifPresent(run -> finish(run, "FAILED", ActionRunService.redactProcessorSecrets(message)));
    }

    private List<ActionWorkflowDto.Step> prepare(String workspaceId, String projectId, ActionWorkflowDto.StartRequest request, String userId) {
        requireProject(workspaceId, projectId, userId);
        var workflow = requirePublished(workspaceId, projectId, request.workflowId());
        if (!Objects.equals(workflow.getVersion(), request.version())) throw new IllegalStateException("This workflow changed. Reload before running.");
        var stored = steps(workflow.getStepsJson());
        var requested = request.steps() == null ? stored : request.steps();
        requireSteps(requested);
        if (requested.size() != stored.size()) throw new IllegalArgumentException("Workflow step overrides must preserve the saved sequence");
        var available = stepProcessors(workspaceId, projectId, request.workflowId(), userId);
        var result = new ArrayList<ActionWorkflowDto.Step>();
        for (int i = 0; i < stored.size(); i++) {
            var step = requested.get(i);
            if (!stored.get(i).processorDefinitionId().equals(step.processorDefinitionId())) throw new IllegalArgumentException("Workflow step overrides must preserve the saved sequence");
            var executable = available.stream().filter(item -> item.processor().id().equals(step.processorDefinitionId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("A workflow processor is not activated for this project"));
            if (!executable.executable()) throw new SecurityException("Step " + (i + 1) + " (" + executable.processor().name() + "): " + executable.blockedReason());
            var parameters = new LinkedHashMap<String, Object>();
            if (stored.get(i).parameters() != null) parameters.putAll(stored.get(i).parameters());
            if (step.parameters() != null) parameters.putAll(step.parameters());
            validateStepImageTypes(step);
            var resolved = actions.resolveRunParameters(requireProcessor(step.processorDefinitionId()), parameters, step.imageVariantSelection(), step.imageTypes());
            result.add(new ActionWorkflowDto.Step(step.processorDefinitionId(), payload.processorParameters(resolved), payload.readImageVariantSelection(resolved), step.imageTypes()));
        }
        return result;
    }

    private void validateStepImageTypes(ActionWorkflowDto.Step step) {
        var processor = requireProcessor(step.processorDefinitionId());
        if (step.imageTypes() != null && !definitions.readInputRequirements(processor).images().accepts(ActionTarget.PAGE)) {
            throw new IllegalArgumentException("Workflow step imageTypes requires enabled page image input");
        }
        actions.resolveImageRequirement(processor, step.imageTypes());
    }

    private ActionDto.StartRunRequest stepRequest(ActionWorkflowDto.Step step, List<String> pageIds) {
        return new ActionDto.StartRunRequest(step.processorDefinitionId(), pageIds, step.parameters(), null, step.imageVariantSelection(), true, step.imageTypes());
    }
    private ActionProcessorDefinition requireProcessor(String id) {
        var processor = processors.findById(id).orElseThrow(() -> new IllegalArgumentException("Workflow processor not found"));
        if (processor.isWorkflow() || processor.getActionKind() != ActionProcessorDefinition.ActionKind.PROCESSING || !definitions.readTargetTypes(processor).contains(ActionTarget.PAGE))
            throw new IllegalArgumentException("Workflows require page-processing Actions");
        return processor;
    }
    private void requireSteps(List<ActionWorkflowDto.Step> steps) {
        if (steps == null || steps.isEmpty() || steps.size() > 30 || steps.stream().anyMatch(step ->
                step == null || step.processorDefinitionId() == null || step.processorDefinitionId().isBlank()))
            throw new IllegalArgumentException("A workflow requires 1 to 30 ordered processor steps");
    }
    private void requireProject(String workspaceId, String projectId, String userId) {
        access.requireWorkspaceAccess(workspaceId, userId);
        if (!projects.existsByIdAndLibraryWorkspaceId(projectId, workspaceId)) throw new IllegalArgumentException("Project not found");
    }
    private ActionWorkflow requireWorkflow(String workspaceId, String id) {
        return (workspaceId == null ? workflows.findById(id) : workflows.findByIdAndWorkspaceId(id, workspaceId))
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
    }
    private ActionWorkflow requirePublished(String workspaceId, String projectId, String id) {
        var action = action(id);
        activation.requireActivated(action, workspaceId, projectId);
        return action.getWorkflow();
    }
    private ActionProcessorDefinition action(String workflowId) {
        return processors.findByWorkflow_Id(workflowId).orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
    }
    private void requireAdmin() {
        if (!admin.isGlobalAdmin()) throw new SecurityException("Global admin access is required");
    }
    private void requireWorkspaceEditable(String workspaceId, ActionWorkflow workflow, ActionProcessorDefinition action) {
        if (!workspaceId.equals(workflow.getWorkspaceId()) || action.isGlobalAvailable()
                || availability.findByProcessorDefinitionIdOrderByWorkspaceIdAsc(action.getId()).stream()
                .anyMatch(grant -> grant.isEnabled() && !workspaceId.equals(grant.getWorkspaceId())))
            throw new SecurityException("Shared workflows must be edited in Admin Actions");
    }
    private ActionWorkflowDto.Definition definition(ActionWorkflow workflow) {
        var action = action(workflow.getId());
        return new ActionWorkflowDto.Definition(workflow.getId(), action.getName(), action.getDescription(), action.isEnabled(),
                steps(workflow.getStepsJson()), workflow.getVersion(), action.getId(), action.isGlobalAvailable());
    }
    private ActionWorkflowDto.Run response(ActionWorkflowRun run, String userId) {
        return new ActionWorkflowDto.Run(run.getId(), run.getWorkflowId(), run.getWorkflowName(), run.getStatus(), steps(run.getStepsJson()), ids(run.getPageIdsJson()), ids(run.getStepRunIdsJson()),
                run.getErrorMessage(), active(run) && canCancel(run, userId), run.getCreated(), run.getCompletedAt(), run.getWorkspaceId(), run.getProjectId(), run.getCreatedByUserId(),
                steps(run.getStepsJson()).stream().map(step -> processors.findById(step.processorDefinitionId())
                        .map(ActionProcessorDefinition::getName).orElse("Unavailable processor")).toList());
    }
    private boolean canCancel(ActionWorkflowRun run, String userId) {
        return userId.equals(run.getCreatedByUserId()) || access.canManageProjects(run.getWorkspaceId(), userId);
    }
    private static boolean active(ActionWorkflowRun run) { return "RUNNING".equals(run.getStatus()) || "CANCEL_REQUESTED".equals(run.getStatus()); }
    private void finish(ActionWorkflowRun run, String status, String message) {
        run.setStatus(status);
        run.setErrorMessage(message);
        run.setCompletedAt(LocalDateTime.now());
    }
    private List<ActionWorkflowDto.Step> steps(String json) { return mapper.readValue(json, STEPS); }
    private List<String> ids(String json) { return mapper.readValue(json, IDS); }
}
