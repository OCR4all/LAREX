package de.uniwue.zpd.dachs.larex.backend.service.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ActionRunPayloadService {

    static final String IMAGE_VARIANT_SELECTION_PARAMETER_KEY = "_larexImageVariantSelection";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final TypeReference<ActionDto.TargetSelection> TARGET_SELECTION = new TypeReference<>() {};
    private static final TypeReference<ActionDto.ImageVariantSelection> IMAGE_VARIANT_SELECTION = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ActionRunPayloadService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> readPageIds(ActionRun run) {
        try {
            return objectMapper.readValue(run.getPageIdsJson(), STRING_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public Map<String, Object> readObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    public ActionDto.ImageVariantSelection readImageVariantSelection(Map<String, Object> parameters) {
        Object value = parameters.get(IMAGE_VARIANT_SELECTION_PARAMETER_KEY);
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, IMAGE_VARIANT_SELECTION);
    }

    public Map<String, Object> processorParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey(IMAGE_VARIANT_SELECTION_PARAMETER_KEY)) {
            return parameters;
        }
        Map<String, Object> result = new LinkedHashMap<>(parameters);
        result.remove(IMAGE_VARIANT_SELECTION_PARAMETER_KEY);
        return result;
    }

    public ActionDto.TargetSelection readTargetSelection(ActionRun run) {
        if (run.getTargetSelectionJson() != null && !run.getTargetSelectionJson().isBlank()) {
            try {
                return objectMapper.readValue(run.getTargetSelectionJson(), TARGET_SELECTION);
            } catch (JsonProcessingException ignored) {
            }
        }
        return new ActionDto.TargetSelection(
                ActionTarget.PAGE,
                readPageIds(run).stream()
                        .map(pageId -> new ActionDto.TargetSelectionPage(pageId, List.of(), List.of()))
                        .collect(Collectors.toList())
        );
    }

    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize Action payload", e);
        }
    }
}
