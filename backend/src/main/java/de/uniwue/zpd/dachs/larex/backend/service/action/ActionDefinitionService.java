package de.uniwue.zpd.dachs.larex.backend.service.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ExecuteRole;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.LockMode;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class ActionDefinitionService {

    private static final int SUPPORTED_VERSION = 1;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_TOKEN_TTL_MINUTES = 1440;

    private final ActionProcessorDefinitionRepository definitionRepository;
    private final GlobalAdminService globalAdminService;
    private final ActionEndpointAuthService endpointAuthService;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public ActionDefinitionService(ActionProcessorDefinitionRepository definitionRepository,
                                   GlobalAdminService globalAdminService,
                                   ActionEndpointAuthService endpointAuthService,
                                   ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.globalAdminService = globalAdminService;
        this.endpointAuthService = endpointAuthService;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.jsonMapper = objectMapper;
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
        return toDefinitionResponse(definitionRepository.save(definition));
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
        return toDefinitionResponse(definitionRepository.save(definition));
    }

    public ActionDto.DefinitionResponse setEnabled(String id, boolean enabled, String userId) {
        requireGlobalAdmin();
        ActionProcessorDefinition definition = definitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Action processor definition not found"));
        definition.setEnabled(enabled);
        definition.setUpdatedByUserId(userId);
        return toDefinitionResponse(definitionRepository.save(definition));
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
                definition.isAcceptsImages(),
                definition.isAcceptsXml(),
                definition.isOutputsImages(),
                definition.isOutputsXml(),
                definition.isEnabled(),
                definition.getCreated(),
                definition.getUpdated()
        );
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
        definition.setAcceptsImages(preview.acceptsImages());
        definition.setAcceptsXml(preview.acceptsXml());
        definition.setOutputsImages(preview.outputsImages());
        definition.setOutputsXml(preview.outputsXml());
        definition.setUpdatedByUserId(userId);
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
        }
    }

    private void validateEndpointUrl(String rawUrl, List<ActionDto.ValidationDiagnostic> diagnostics) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        try {
            URI uri = new URI(rawUrl);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                diagnostics.add(error("endpoint.url", "endpoint.url must use http or https"));
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                diagnostics.add(error("endpoint.url", "endpoint.url must include a host"));
            }
        } catch (URISyntaxException e) {
            diagnostics.add(error("endpoint.url", "endpoint.url is not a valid URI"));
        }
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
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return true;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost)
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")
                || !normalizedHost.contains(".")) {
            return false;
        }
        if (normalizedHost.startsWith("127.")
                || normalizedHost.startsWith("10.")
                || normalizedHost.startsWith("192.168.")) {
            return false;
        }
        if (normalizedHost.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
            return false;
        }
        return true;
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
