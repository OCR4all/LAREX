package de.uniwue.zpd.dachs.larex.backend.service.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorAssignment;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionCategory;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ExecuteRole;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.LockMode;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorWorkspaceAvailability;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorAssignmentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorWorkspaceAvailabilityRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunDismissalRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunLogEventRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
@Transactional
public class ActionDefinitionService {

    private static final int SUPPORTED_VERSION = 1;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_TOKEN_TTL_MINUTES = 1440;
    private static final TypeReference<List<ActionTarget>> ACTION_TARGET_LIST = new TypeReference<>() {};

    private final ActionProcessorDefinitionRepository definitionRepository;
    private final ActionProcessorWorkspaceAvailabilityRepository availabilityRepository;
    private final ActionProcessorAssignmentRepository assignmentRepository;
    private final ActionRunRepository runRepository;
    private final ActionRunDismissalRepository runDismissalRepository;
    private final ActionRunLogEventRepository logEventRepository;
    private final GlobalAdminService globalAdminService;
    private final ActionEndpointAuthService endpointAuthService;
    private final ActionAuditService actionAuditService;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;
    private final HttpClient httpClient;
    private final ActionProperties actionProperties;

    public ActionDefinitionService(ActionProcessorDefinitionRepository definitionRepository,
                                   ActionProcessorWorkspaceAvailabilityRepository availabilityRepository,
                                   ActionProcessorAssignmentRepository assignmentRepository,
                                   ActionRunRepository runRepository,
                                   ActionRunDismissalRepository runDismissalRepository,
                                   ActionRunLogEventRepository logEventRepository,
                                   GlobalAdminService globalAdminService,
                                   ActionEndpointAuthService endpointAuthService,
                                   ActionAuditService actionAuditService,
                                   ObjectMapper objectMapper,
                                   ActionProperties actionProperties) {
        this.definitionRepository = definitionRepository;
        this.availabilityRepository = availabilityRepository;
        this.assignmentRepository = assignmentRepository;
        this.runRepository = runRepository;
        this.runDismissalRepository = runDismissalRepository;
        this.logEventRepository = logEventRepository;
        this.globalAdminService = globalAdminService;
        this.endpointAuthService = endpointAuthService;
        this.actionAuditService = actionAuditService;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.jsonMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.actionProperties = actionProperties;
    }

    @Transactional(readOnly = true)
    public List<ActionDto.DefinitionResponse> listDefinitions() {
        requireGlobalAdmin();
        return definitionRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDefinitionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ActionDto.DefinitionResponse getDefinition(String id) {
        requireGlobalAdmin();
        return toDefinitionResponse(definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found")));
    }

    @Transactional(readOnly = true)
    public List<ActionDto.AuditEventResponse> listAuditEvents(String id) {
        requireGlobalAdmin();
        requireDefinition(id);
        return actionAuditService.listForDefinition(id);
    }

    public ActionDto.DefinitionResponse createDefinition(ActionDto.DefinitionRequest request, String userId) {
        requireGlobalAdmin();
        ParsedDefinition parsed = parseAndValidate(request.yaml(), null);
        if (definitionRepository.existsByProcessorKey(parsed.preview().processorKey())) {
            throw new IllegalArgumentException("Processor id already exists: " + parsed.preview().processorKey());
        }

        ActionProcessorDefinition definition = new ActionProcessorDefinition();
        applyParsedDefinition(definition, parsed, request.yaml(), userId);
        definition.setCreatedByUserId(userId);
        definition.setUpdatedByUserId(userId);
        definition.setEnabled(request.enabled() == null || request.enabled());
        ActionProcessorDefinition saved = definitionRepository.save(definition);
        actionAuditService.record("ACTION_DEFINITION_CREATE", "SUCCESS", userId, saved.getId(), null, null, null,
                Map.of("processorKey", saved.getProcessorKey()));
        return toDefinitionResponse(saved);
    }

    public ActionDto.DefinitionResponse updateDefinition(String id, ActionDto.DefinitionRequest request, String userId) {
        requireGlobalAdmin();
        ActionProcessorDefinition definition = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found"));
        ParsedDefinition parsed = parseAndValidate(request.yaml(), id);
        definitionRepository.findByProcessorKey(parsed.preview().processorKey())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Processor id already exists: " + parsed.preview().processorKey());
                });

        applyParsedDefinition(definition, parsed, request.yaml(), userId);
        definition.setUpdatedByUserId(userId);
        if (request.enabled() != null) {
            definition.setEnabled(request.enabled());
        }
        ActionProcessorDefinition saved = definitionRepository.save(definition);
        actionAuditService.record("ACTION_DEFINITION_UPDATE", "SUCCESS", userId, saved.getId(), null, null, null,
                Map.of("processorKey", saved.getProcessorKey()));
        return toDefinitionResponse(saved);
    }

    public ActionDto.DefinitionResponse upsertSystemDefinition(String processorKey,
                                                               String yaml,
                                                               boolean enabled,
                                                               boolean globalAvailable,
                                                               String userId) {
        ActionProcessorDefinition definition = definitionRepository.findByProcessorKey(processorKey)
                .orElseGet(ActionProcessorDefinition::new);
        String existingId = definition.getId();
        ParsedDefinition parsed = parseAndValidate(yaml, existingId);
        if (!processorKey.equals(parsed.preview().processorKey())) {
            throw new IllegalArgumentException("System Action YAML id does not match expected processor key: " + processorKey);
        }

        applyParsedDefinition(definition, parsed, yaml, userId);
        if (definition.getCreatedByUserId() == null) {
            definition.setCreatedByUserId(userId);
        }
        definition.setUpdatedByUserId(userId);
        definition.setEnabled(enabled);
        definition.setGlobalAvailable(globalAvailable);
        return toDefinitionResponse(definitionRepository.save(definition));
    }

    public ActionDto.DefinitionResponse setEnabled(String id, boolean enabled, String userId) {
        requireGlobalAdmin();
        ActionProcessorDefinition definition = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found"));
        definition.setEnabled(enabled);
        definition.setUpdatedByUserId(userId);
        ActionProcessorDefinition saved = definitionRepository.save(definition);
        actionAuditService.record(enabled ? "ACTION_DEFINITION_ENABLE" : "ACTION_DEFINITION_DISABLE", "SUCCESS", userId,
                saved.getId(), null, null, null, Map.of("processorKey", saved.getProcessorKey()));
        return toDefinitionResponse(saved);
    }

    public ActionDto.DefinitionResponse setGlobalAvailable(String id, boolean globalAvailable, String userId) {
        requireGlobalAdmin();
        ActionProcessorDefinition definition = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found"));
        definition.setGlobalAvailable(globalAvailable);
        definition.setUpdatedByUserId(userId);
        ActionProcessorDefinition saved = definitionRepository.save(definition);
        actionAuditService.record(globalAvailable ? "ACTION_DEFINITION_GLOBAL_ENABLE" : "ACTION_DEFINITION_GLOBAL_DISABLE",
                "SUCCESS", userId, saved.getId(), null, null, null, Map.of("processorKey", saved.getProcessorKey()));
        return toDefinitionResponse(saved);
    }

    public void deleteDefinition(String id) {
        requireGlobalAdmin();
        ActionProcessorDefinition definition = requireDefinition(id);
        List<ActionRun> activeRuns = runRepository.findByProcessorDefinitionIdAndStatusIn(id, activeStatuses());
        if (!activeRuns.isEmpty()) {
            throw new IllegalStateException("Action has active runs and cannot be deleted");
        }

        List<ActionRun> terminalRuns = runRepository.findByProcessorDefinitionIdAndStatusIn(id, terminalStatuses());
        deleteRunsWithLogs(terminalRuns);

        List<ActionProcessorAssignment> assignments = assignmentRepository.findByDefinitionIds(List.of(id));
        assignmentRepository.deleteAll(assignments);

        List<ActionProcessorWorkspaceAvailability> availability = availabilityRepository.findByProcessorDefinitionIdOrderByWorkspaceIdAsc(id);
        availabilityRepository.deleteAll(availability);

        definitionRepository.delete(definition);
        actionAuditService.record("ACTION_DEFINITION_DELETE", "SUCCESS", null, id, null, null, null,
                Map.of("processorKey", definition.getProcessorKey()));
    }

    private void deleteRunsWithLogs(List<ActionRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return;
        }
        List<String> runIds = runs.stream()
                .map(ActionRun::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (!runIds.isEmpty()) {
            runDismissalRepository.deleteByRunIds(runIds);
            logEventRepository.deleteByRunIds(runIds);
        }
        runRepository.deleteAll(runs);
    }

    @Transactional(readOnly = true)
    public List<ActionDto.WorkspaceAvailabilityResponse> listWorkspaceAvailability(String definitionId) {
        requireGlobalAdmin();
        requireDefinition(definitionId);
        return availabilityRepository.findByProcessorDefinitionIdOrderByWorkspaceIdAsc(definitionId)
                .stream()
                .map(this::toWorkspaceAvailabilityResponse)
                .toList();
    }

    public ActionDto.WorkspaceAvailabilityResponse assignWorkspaceAvailability(String definitionId,
                                                                              ActionDto.WorkspaceAvailabilityRequest request,
                                                                              String userId) {
        requireGlobalAdmin();
        ActionProcessorDefinition definition = requireDefinition(definitionId);
        ActionProcessorWorkspaceAvailability availability = availabilityRepository
                .findByProcessorDefinitionIdAndWorkspaceId(definitionId, request.workspaceId())
                .orElseGet(ActionProcessorWorkspaceAvailability::new);
        availability.setProcessorDefinition(definition);
        availability.setWorkspaceId(request.workspaceId());
        availability.setEnabled(request.enabled() == null || request.enabled());
        if (availability.getCreatedByUserId() == null) {
            availability.setCreatedByUserId(userId);
        }
        ActionProcessorWorkspaceAvailability saved = availabilityRepository.save(availability);
        actionAuditService.record("ACTION_WORKSPACE_AVAILABILITY_ASSIGN", "SUCCESS", userId, definitionId, null,
                request.workspaceId(), null, Map.of("enabled", saved.isEnabled()));
        return toWorkspaceAvailabilityResponse(saved);
    }

    public void removeWorkspaceAvailability(String definitionId, String availabilityId) {
        requireGlobalAdmin();
        ActionProcessorWorkspaceAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Action workspace availability not found"));
        if (!definitionId.equals(availability.getProcessorDefinition().getId())) {
            throw new IllegalArgumentException("Action workspace availability not found");
        }
        String workspaceId = availability.getWorkspaceId();
        availabilityRepository.delete(availability);
        actionAuditService.record("ACTION_WORKSPACE_AVAILABILITY_REMOVE", "SUCCESS", null, definitionId, null,
                workspaceId, null, Map.of());
    }

    @Transactional(readOnly = true)
    public ActionDto.ValidationResponse validateYaml(String yaml, String existingDefinitionId) {
        try {
            ParsedDefinition parsed = parseAndValidate(yaml, existingDefinitionId);
            return new ActionDto.ValidationResponse(true, List.of(), parsed.preview());
        } catch (ValidationException e) {
            return new ActionDto.ValidationResponse(false, e.diagnostics(), null);
        }
    }

    public ParsedDefinition parseAndValidate(String yaml, String existingDefinitionId) {
        List<ActionDto.ValidationDiagnostic> diagnostics = new ArrayList<>();
        if (yaml == null || yaml.isBlank()) {
            diagnostics.add(error("$", "YAML must not be blank"));
            throw new ValidationException(diagnostics);
        }

        ActionDefinitionDocument document;
        try {
            document = yamlMapper.readValue(yaml, ActionDefinitionDocument.class);
        } catch (JsonMappingException e) {
            diagnostics.add(mappingError(e));
            throw new ValidationException(diagnostics);
        } catch (JsonProcessingException e) {
            diagnostics.add(parseError(e));
            throw new ValidationException(diagnostics);
        }

        if (document.version() == null || document.version() != SUPPORTED_VERSION) {
            diagnostics.add(error("version", "version is required and must be 1"));
        }
        String key = requirePattern(document.id(), "id", "[a-zA-Z0-9][a-zA-Z0-9._-]{1,126}", diagnostics);
        String name = requireText(document.name(), "name", diagnostics);
        String endpointUrl = null;
        int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        if (document.endpoint() == null) {
            diagnostics.add(error("endpoint", "endpoint is required"));
        } else {
            endpointUrl = requireText(document.endpoint().url(), "endpoint.url", diagnostics);
            validateEndpointUrl(endpointUrl, diagnostics);
            validateEndpointUrl(document.endpoint().healthUrl(), "endpoint.healthUrl", diagnostics);
            validateEndpointAuth(document.endpoint(), endpointUrl, diagnostics);
            if (document.endpoint().timeoutSeconds() != null) {
                timeoutSeconds = document.endpoint().timeoutSeconds();
                if (timeoutSeconds < 1 || timeoutSeconds > 300) {
                    diagnostics.add(error("endpoint.timeoutSeconds", "timeoutSeconds must be between 1 and 300"));
                }
            }
        }

        ExecuteRole executeRole = ExecuteRole.CURATOR;
        if (document.access() != null && document.access().execute() != null && !document.access().execute().isBlank()) {
            executeRole = enumValue(ExecuteRole.class, document.access().execute(), "access.execute", diagnostics, ExecuteRole.CURATOR);
        }

        LockMode lockMode = LockMode.PAGES;
        if (document.locking() != null && document.locking().mode() != null && !document.locking().mode().isBlank()) {
            lockMode = enumValue(LockMode.class, document.locking().mode(), "locking.mode", diagnostics, LockMode.PAGES);
        }

        ActionCategory category = ActionCategory.WORKFLOW;
        if (document.category() != null && !document.category().isBlank()) {
            category = enumValue(ActionCategory.class, document.category(), "category", diagnostics, ActionCategory.WORKFLOW);
        }

        List<ActionTarget> targets = parseTargets(document.targets(), diagnostics);

        boolean acceptsImages = document.inputs() != null && Boolean.TRUE.equals(document.inputs().images());
        boolean acceptsXml = document.inputs() != null && Boolean.TRUE.equals(document.inputs().xml());
        if (!acceptsImages && !acceptsXml) {
            diagnostics.add(error("inputs", "At least one input type must be enabled"));
        }

        boolean outputsXml = document.outputs() != null
                && document.outputs().xml() != null
                && Boolean.TRUE.equals(document.outputs().xml().enabled());
        boolean outputsImages = document.outputs() != null
                && document.outputs().images() != null
                && Boolean.TRUE.equals(document.outputs().images().enabled());
        validateOutput(document.outputs() == null ? null : document.outputs().xml(), "outputs.xml", outputsXml, diagnostics);
        validateImageOutput(document.outputs() == null ? null : document.outputs().images(), "outputs.images", outputsImages, diagnostics);
        if (!outputsXml && !outputsImages) {
            diagnostics.add(error("outputs", "At least one output type must be enabled"));
        }
        validateConcurrency(document.concurrency(), diagnostics);

        validateParameters(document.parameters(), diagnostics);

        if (key != null) {
            definitionRepository.findByProcessorKey(key)
                    .filter(existing -> existingDefinitionId == null || !existing.getId().equals(existingDefinitionId))
                    .ifPresent(existing -> diagnostics.add(error("id", "Processor id already exists")));
        }

        if (!diagnostics.isEmpty()) {
            throw new ValidationException(diagnostics);
        }

        try {
            String parsedJson = jsonMapper.writeValueAsString(document);
            ActionDto.DefinitionPreview preview = new ActionDto.DefinitionPreview(
                    key,
                    name,
                    trimToNull(document.description()),
                    endpointUrl,
                    timeoutSeconds,
                    executeRole,
                    lockMode,
                    category,
                    targets,
                    acceptsImages,
                    acceptsXml,
                    outputsImages,
                    outputsXml,
                    document.parameters() == null ? Map.of() : document.parameters()
            );
            return new ParsedDefinition(document, parsedJson, preview);
        } catch (JsonProcessingException e) {
            diagnostics.add(error("$", "Could not serialize parsed definition"));
            throw new ValidationException(diagnostics);
        }
    }

    public ActionDefinitionDocument readParsedDocument(ActionProcessorDefinition definition) {
        try {
            return jsonMapper.readValue(definition.getParsedJson(), ActionDefinitionDocument.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored Action definition is invalid", e);
        }
    }

    public void requireEndpointUrlAllowed(String rawUrl) {
        List<ActionDto.ValidationDiagnostic> diagnostics = new ArrayList<>();
        validateEndpointUrl(rawUrl, "endpoint.url", diagnostics);
        if (!diagnostics.isEmpty()) {
            throw new SecurityException(diagnostics.get(0).message());
        }
    }

    public ActionDto.DefinitionResponse toDefinitionResponse(ActionProcessorDefinition definition) {
        return new ActionDto.DefinitionResponse(
                definition.getId(),
                definition.getProcessorKey(),
                definition.getName(),
                definition.getDescription(),
                definition.getYamlSource(),
                definition.getEndpointUrl(),
                definition.getEndpointTimeoutSeconds(),
                definition.getExecuteRole(),
                definition.getLockMode(),
                definition.getCategory(),
                readTargetTypes(definition),
                definition.isAcceptsImages(),
                definition.isAcceptsXml(),
                definition.isOutputsImages(),
                definition.isOutputsXml(),
                definition.isEnabled(),
                definition.isGlobalAvailable(),
                definition.getCreated(),
                definition.getUpdated()
        );
    }

    public ActionDto.WorkspaceAvailabilityResponse toWorkspaceAvailabilityResponse(ActionProcessorWorkspaceAvailability availability) {
        return new ActionDto.WorkspaceAvailabilityResponse(
                availability.getId(),
                availability.getWorkspaceId(),
                availability.isEnabled(),
                toDefinitionResponse(availability.getProcessorDefinition()),
                availability.getCreated(),
                availability.getUpdated()
        );
    }

    public ActionDto.HealthCheckResponse testEndpoint(String definitionId) {
        requireGlobalAdmin();
        ActionProcessorDefinition definition = requireDefinition(definitionId);
        ActionDefinitionDocument document = readParsedDocument(definition);
        String url = document.endpoint() != null && document.endpoint().healthUrl() != null && !document.endpoint().healthUrl().isBlank()
                ? document.endpoint().healthUrl()
                : definition.getEndpointUrl();
        long start = System.nanoTime();
        try {
            requireEndpointUrlAllowed(url);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.min(10, Math.max(1, definition.getEndpointTimeoutSeconds()))))
                    .method(document.endpoint() != null && document.endpoint().healthUrl() != null && !document.endpoint().healthUrl().isBlank() ? "GET" : "HEAD",
                            HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            return new ActionDto.HealthCheckResponse(
                    ok,
                    response.statusCode(),
                    url,
                    ok ? "Endpoint is reachable" : "Endpoint returned HTTP " + response.statusCode(),
                    Duration.ofNanos(System.nanoTime() - start).toMillis()
            );
        } catch (IOException e) {
            return new ActionDto.HealthCheckResponse(false, 0, url, "Could not connect: " + e.getMessage(),
                    Duration.ofNanos(System.nanoTime() - start).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ActionDto.HealthCheckResponse(false, 0, url, "Health check interrupted",
                    Duration.ofNanos(System.nanoTime() - start).toMillis());
        } catch (IllegalArgumentException e) {
            return new ActionDto.HealthCheckResponse(false, 0, url, e.getMessage(),
                    Duration.ofNanos(System.nanoTime() - start).toMillis());
        }
    }

    private ActionProcessorDefinition requireDefinition(String definitionId) {
        return definitionRepository.findById(definitionId)
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found"));
    }

    private List<ActionRun.Status> activeStatuses() {
        return List.of(
                ActionRun.Status.PENDING,
                ActionRun.Status.DISPATCHING,
                ActionRun.Status.RUNNING,
                ActionRun.Status.IMPORTING_RESULTS,
                ActionRun.Status.CANCEL_REQUESTED
        );
    }

    private List<ActionRun.Status> terminalStatuses() {
        return List.of(ActionRun.Status.COMPLETED, ActionRun.Status.FAILED, ActionRun.Status.CANCELLED);
    }

    private void applyParsedDefinition(ActionProcessorDefinition definition,
                                       ParsedDefinition parsed,
                                       String yaml,
                                       String userId) {
        ActionDto.DefinitionPreview preview = parsed.preview();
        definition.setProcessorKey(preview.processorKey());
        definition.setName(preview.name());
        definition.setDescription(preview.description());
        definition.setYamlSource(yaml);
        definition.setParsedJson(parsed.parsedJson());
        definition.setEndpointUrl(preview.endpointUrl());
        definition.setEndpointTimeoutSeconds(preview.endpointTimeoutSeconds());
        definition.setExecuteRole(preview.executeRole());
        definition.setLockMode(preview.lockMode());
        definition.setCategory(preview.category());
        definition.setTargetTypesJson(writeJson(preview.targets()));
        definition.setAcceptsImages(preview.acceptsImages());
        definition.setAcceptsXml(preview.acceptsXml());
        definition.setOutputsImages(preview.outputsImages());
        definition.setOutputsXml(preview.outputsXml());
        definition.setUpdatedByUserId(userId);
    }

    public List<ActionTarget> readTargetTypes(ActionProcessorDefinition definition) {
        if (definition.getTargetTypesJson() == null || definition.getTargetTypesJson().isBlank()) {
            return List.of(ActionTarget.PAGE);
        }
        try {
            List<ActionTarget> targets = jsonMapper.readValue(definition.getTargetTypesJson(), ACTION_TARGET_LIST);
            return targets == null || targets.isEmpty() ? List.of(ActionTarget.PAGE) : targets;
        } catch (JsonProcessingException e) {
            return List.of(ActionTarget.PAGE);
        }
    }

    private void validateOutput(ActionDefinitionDocument.OutputTarget output,
                                String path,
                                boolean enabled,
                                List<ActionDto.ValidationDiagnostic> diagnostics) {
        if (!enabled) {
            return;
        }
        String mode = output.mode() == null ? "upsert" : output.mode().trim().toLowerCase(Locale.ROOT);
        if (!mode.equals("upsert") && !mode.equals("append")) {
            diagnostics.add(error(path + ".mode", "mode must be upsert or append"));
        }
    }

    private void validateImageOutput(ActionDefinitionDocument.ImageOutputTarget output,
                                     String path,
                                     boolean enabled,
                                     List<ActionDto.ValidationDiagnostic> diagnostics) {
        if (!enabled) {
            return;
        }
        requirePattern(output.variant(), path + ".variant", "[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}", diagnostics);
        String mode = output.mode() == null ? "upsert" : output.mode().trim().toLowerCase(Locale.ROOT);
        if (!mode.equals("upsert") && !mode.equals("append")) {
            diagnostics.add(error(path + ".mode", "mode must be upsert or append"));
        }
    }

    private List<ActionTarget> parseTargets(List<String> rawTargets,
                                            List<ActionDto.ValidationDiagnostic> diagnostics) {
        if (rawTargets == null || rawTargets.isEmpty()) {
            return List.of(ActionTarget.PAGE);
        }
        List<ActionTarget> targets = new ArrayList<>();
        Set<ActionTarget> seen = new LinkedHashSet<>();
        for (int index = 0; index < rawTargets.size(); index++) {
            String raw = rawTargets.get(index);
            if (raw == null || raw.isBlank()) {
                diagnostics.add(error("targets[" + index + "]", "target must not be blank"));
                continue;
            }
            ActionTarget target = enumValue(ActionTarget.class, raw, "targets[" + index + "]", diagnostics, null);
            if (target != null && seen.add(target)) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            diagnostics.add(error("targets", "At least one target must be declared"));
        }
        return targets.isEmpty() ? List.of(ActionTarget.PAGE) : targets;
    }

    private void validateParameters(Map<String, ActionDefinitionDocument.Parameter> parameters,
                                    List<ActionDto.ValidationDiagnostic> diagnostics) {
        if (parameters == null) {
            return;
        }
        for (Map.Entry<String, ActionDefinitionDocument.Parameter> entry : parameters.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")) {
                diagnostics.add(error("parameters", "Parameter names must be identifier-like"));
            }
            ActionDefinitionDocument.Parameter parameter = entry.getValue();
            if (parameter == null) {
                diagnostics.add(error("parameters." + key, "Parameter definition must not be empty"));
                continue;
            }
            String type = parameter.type() == null ? "string" : parameter.type().trim().toLowerCase(Locale.ROOT);
            if (!List.of("string", "number", "integer", "boolean").contains(type)) {
                diagnostics.add(error("parameters." + key + ".type", "type must be string, number, integer, or boolean"));
            }
            if (parameter.min() != null && parameter.max() != null && parameter.min() > parameter.max()) {
                diagnostics.add(error("parameters." + key, "min must be less than or equal to max"));
            }
            if (parameter.defaultValue() != null) {
                validateParameterValue("parameters." + key + ".default", parameter, parameter.defaultValue(), diagnostics);
            }
        }
    }

    private void validateConcurrency(ActionDefinitionDocument.Concurrency concurrency,
                                     List<ActionDto.ValidationDiagnostic> diagnostics) {
        if (concurrency == null) {
            return;
        }
        if (concurrency.maxActiveRuns() != null && (concurrency.maxActiveRuns() < 1 || concurrency.maxActiveRuns() > 100)) {
            diagnostics.add(error("concurrency.maxActiveRuns", "maxActiveRuns must be between 1 and 100"));
        }
        if (concurrency.scope() != null && !concurrency.scope().isBlank()) {
            String scope = concurrency.scope().trim().toUpperCase(Locale.ROOT);
            if (!List.of("GLOBAL", "WORKSPACE", "PROJECT").contains(scope)) {
                diagnostics.add(error("concurrency.scope", "scope must be GLOBAL, WORKSPACE, or PROJECT"));
            }
        }
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize Action definition value", e);
        }
    }

    private void validateParameterValue(String path,
                                        ActionDefinitionDocument.Parameter parameter,
                                        Object value,
                                        List<ActionDto.ValidationDiagnostic> diagnostics) {
        String type = parameter.type() == null ? "string" : parameter.type().trim().toLowerCase(Locale.ROOT);
        if ("boolean".equals(type)) {
            if (!(value instanceof Boolean)) {
                diagnostics.add(error(path, "default must be a boolean"));
            }
            return;
        }
        if ("number".equals(type) || "integer".equals(type)) {
            if (!(value instanceof Number number)) {
                diagnostics.add(error(path, "default must be numeric"));
                return;
            }
            double numericValue = number.doubleValue();
            if ("integer".equals(type) && Math.rint(numericValue) != numericValue) {
                diagnostics.add(error(path, "default must be an integer"));
            }
            if (parameter.min() != null && numericValue < parameter.min()) {
                diagnostics.add(error(path, "default must be greater than or equal to min"));
            }
            if (parameter.max() != null && numericValue > parameter.max()) {
                diagnostics.add(error(path, "default must be less than or equal to max"));
            }
            return;
        }
        if (!(value instanceof String)) {
            diagnostics.add(error(path, "default must be a string"));
        }
    }

    private void validateEndpointUrl(String rawUrl, List<ActionDto.ValidationDiagnostic> diagnostics) {
        validateEndpointUrl(rawUrl, "endpoint.url", diagnostics);
    }

    private void validateEndpointUrl(String rawUrl, String path, List<ActionDto.ValidationDiagnostic> diagnostics) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        try {
            URI uri = new URI(rawUrl);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                diagnostics.add(error(path, path + " must use http or https"));
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                diagnostics.add(error(path, path + " must include a host"));
            }
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                diagnostics.add(error(path, path + " must not include credentials"));
            }
            if (uri.getFragment() != null && !uri.getFragment().isBlank()) {
                diagnostics.add(error(path, path + " must not include a fragment"));
            }
            validateEndpointOriginPolicy(uri, path, diagnostics);
        } catch (URISyntaxException e) {
            diagnostics.add(error(path, path + " is not a valid URI"));
        }
    }

    private void validateEndpointOriginPolicy(URI uri, String path, List<ActionDto.ValidationDiagnostic> diagnostics) {
        if (uri.getScheme() == null || uri.getHost() == null) {
            return;
        }
        boolean localOrPrivate = isLocalOrPrivateEndpoint(uri);
        if (actionProperties.isEndpointRequireHttps()
                && !"https".equalsIgnoreCase(uri.getScheme())
                && !(actionProperties.isEndpointAllowInsecureLocal() && localOrPrivate)) {
            diagnostics.add(error(path, path + " must use https unless it targets an allowed local endpoint"));
        }

        Set<String> allowedOrigins = configuredEndpointAllowedOrigins(diagnostics);
        if (!allowedOrigins.isEmpty() && !allowedOrigins.contains(originOf(uri))) {
            diagnostics.add(error(path, "Action endpoint origin is not allowlisted"));
        }
    }

    private Set<String> configuredEndpointAllowedOrigins(List<ActionDto.ValidationDiagnostic> diagnostics) {
        String endpointAllowedOrigins = actionProperties.getEndpointAllowedOrigins();
        if (endpointAllowedOrigins == null || endpointAllowedOrigins.isBlank()) {
            return Set.of();
        }
        Set<String> origins = new LinkedHashSet<>();
        Arrays.stream(endpointAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .forEach(origin -> {
                    try {
                        URI uri = new URI(origin);
                        if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null) {
                            diagnostics.add(error("larex.actions.endpoint-allowed-origins",
                                    "Configured Action endpoint allowed origin is invalid: " + origin));
                            return;
                        }
                        origins.add(originOf(uri));
                    } catch (URISyntaxException e) {
                        diagnostics.add(error("larex.actions.endpoint-allowed-origins",
                                "Configured Action endpoint allowed origin is invalid: " + origin));
                    }
                });
        return origins;
    }

    private String originOf(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port < 0) {
            port = "https".equals(scheme) ? 443 : 80;
        }
        return scheme + "://" + host + ":" + port;
    }

    private void validateEndpointAuth(ActionDefinitionDocument.Endpoint endpoint,
                                      String rawUrl,
                                      List<ActionDto.ValidationDiagnostic> diagnostics) {
        if (endpoint == null || rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        ActionDefinitionDocument.EndpointAuth auth = endpoint.auth();
        String authType = endpointAuthService.normalizeAuthType(auth);
        if (!List.of("none", "hmac").contains(authType)) {
            diagnostics.add(error("endpoint.auth.type", "type must be none or hmac"));
            return;
        }

        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            return;
        }

        if ("none".equals(authType)) {
            if (isExternalEndpoint(uri)) {
                diagnostics.add(error("endpoint.auth", "External Action endpoints must use endpoint.auth.type: hmac"));
            }
            return;
        }
        if (isExternalEndpoint(uri) && !"https".equalsIgnoreCase(uri.getScheme())) {
            diagnostics.add(error("endpoint.url", "External Action endpoints must use https"));
        }

        String secretRef = requirePattern(
                auth == null ? null : auth.secretRef(),
                "endpoint.auth.secretRef",
                "[a-zA-Z0-9][a-zA-Z0-9._-]{1,126}",
                diagnostics
        );
        if (secretRef != null && !endpointAuthService.hasSecret(secretRef)) {
            diagnostics.add(error(
                    "endpoint.auth.secretRef",
                    "No LAREX endpoint secret is configured for " + secretRef
                            + " (" + endpointAuthService.envNameForSecretRef(secretRef) + ")"
            ));
        }
    }

    private boolean isExternalEndpoint(URI uri) {
        return !isLocalOrPrivateEndpoint(uri);
    }

    private boolean isLocalOrPrivateEndpoint(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost)
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")
                || !normalizedHost.contains(".")) {
            return true;
        }
        if (normalizedHost.startsWith("127.")
                || normalizedHost.startsWith("10.")
                || normalizedHost.startsWith("192.168.")
                || "::1".equals(normalizedHost)
                || "0:0:0:0:0:0:0:1".equals(normalizedHost)
                || normalizedHost.startsWith("fe80:")) {
            return true;
        }
        if (normalizedHost.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
            return true;
        }
        return false;
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumClass,
                                           String raw,
                                           String path,
                                           List<ActionDto.ValidationDiagnostic> diagnostics,
                                           E fallback) {
        try {
            return Enum.valueOf(enumClass, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            diagnostics.add(error(path, "Unsupported value: " + raw));
            return fallback;
        }
    }

    private String requireText(String value, String path, List<ActionDto.ValidationDiagnostic> diagnostics) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            diagnostics.add(error(path, path + " is required"));
        }
        return trimmed;
    }

    private String requirePattern(String value, String path, String pattern, List<ActionDto.ValidationDiagnostic> diagnostics) {
        String trimmed = requireText(value, path, diagnostics);
        if (trimmed != null && !trimmed.matches(pattern)) {
            diagnostics.add(error(path, path + " has an invalid format"));
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ActionDto.ValidationDiagnostic mappingError(JsonMappingException e) {
        String path = e.getPath().stream()
                .map(ref -> ref.getFieldName() == null ? String.valueOf(ref.getIndex()) : ref.getFieldName())
                .filter(part -> part != null && !part.isBlank())
                .reduce((left, right) -> left + "." + right)
                .orElse("$");
        JsonLocation location = e.getLocation();
        return new ActionDto.ValidationDiagnostic(
                "error",
                path,
                location == null ? null : (int) location.getLineNr(),
                location == null ? null : (int) location.getColumnNr(),
                e.getOriginalMessage()
        );
    }

    private ActionDto.ValidationDiagnostic parseError(JsonProcessingException e) {
        JsonLocation location = e.getLocation();
        return new ActionDto.ValidationDiagnostic(
                "error",
                "$",
                location == null ? null : (int) location.getLineNr(),
                location == null ? null : (int) location.getColumnNr(),
                e.getOriginalMessage()
        );
    }

    private ActionDto.ValidationDiagnostic error(String path, String message) {
        return new ActionDto.ValidationDiagnostic("error", path, null, null, message);
    }

    private void requireGlobalAdmin() {
        if (!globalAdminService.isGlobalAdmin()) {
            throw new SecurityException("Global admin access is required to manage LAREX Actions");
        }
    }

    public int defaultTokenTtlMinutes() {
        return DEFAULT_TOKEN_TTL_MINUTES;
    }

    public record ParsedDefinition(
            ActionDefinitionDocument document,
            String parsedJson,
            ActionDto.DefinitionPreview preview
    ) {}

    public static class ValidationException extends IllegalArgumentException {
        private final List<ActionDto.ValidationDiagnostic> diagnostics;

        public ValidationException(List<ActionDto.ValidationDiagnostic> diagnostics) {
            super("Action definition validation failed");
            this.diagnostics = diagnostics;
        }

        public List<ActionDto.ValidationDiagnostic> diagnostics() {
            return diagnostics;
        }
    }
}
