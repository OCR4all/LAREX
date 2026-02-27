package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.LabelSetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.uniwue.zpd.dachs.larex.backend.util.JsonNodeUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class LabelSetService {

    private final LabelSetRepository labelSetRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ObjectMapper objectMapper;
    private final LabelSetDefinitionValidator labelSetDefinitionValidator;

    public LabelSetService(LabelSetRepository labelSetRepository,
                          WorkspaceAccessService workspaceAccessService,
                          ObjectMapper objectMapper,
                          LabelSetDefinitionValidator labelSetDefinitionValidator) {
        this.labelSetRepository = labelSetRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.objectMapper = objectMapper;
        this.labelSetDefinitionValidator = labelSetDefinitionValidator;
    }

    @CacheEvict(value = "labelSets", allEntries = true)
    public LabelSetDto.Response createLabelSet(String userId, String workspaceId, JsonNode requestJson) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        LabelSetDto.CreateOrUpdateRequest request = parseAndValidateRequest(requestJson);

        String name = request.meta().name();
        String description = request.meta().description();
        List<String> tags = request.meta().tags() != null ? request.meta().tags() : new ArrayList<>();

        if (labelSetRepository.existsByNameAndWorkspaceId(name, workspaceId)) {
            throw new IllegalArgumentException("Label set with name '" + name + "' already exists in this workspace");
        }

        LabelSet labelSet = new LabelSet(workspaceId, name, description, objectMapper.valueToTree(request));
        labelSet.setTags(tags);
        labelSet = labelSetRepository.save(labelSet);
        return convertToLabelSetResponse(labelSet);
    }

    @CacheEvict(value = "labelSets", allEntries = true)
    public LabelSetDto.Response updateLabelSet(String userId, String workspaceId, String labelSetId, JsonNode requestJson) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        LabelSetDto.CreateOrUpdateRequest request = parseAndValidateRequest(requestJson);

        LabelSet labelSet = labelSetRepository.findByIdAndWorkspaceId(labelSetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Label set not found: " + labelSetId));

        if (labelSet.isSystem()) {
            throw new IllegalStateException("Cannot modify system label set: " + labelSet.getName());
        }

        String name = request.meta().name();
        String description = request.meta().description();
        List<String> tags = request.meta().tags() != null ? request.meta().tags() : new ArrayList<>();

        if (!labelSet.getName().equals(name) &&
            labelSetRepository.existsByNameAndWorkspaceId(name, workspaceId)) {
            throw new IllegalArgumentException("Label set with name '" + name + "' already exists in this workspace");
        }

        labelSet.setName(name);
        labelSet.setDescription(description);
        labelSet.setTags(tags);
        labelSet.setDefinition(objectMapper.valueToTree(request));

        labelSet = labelSetRepository.save(labelSet);
        return convertToLabelSetResponse(labelSet);
    }

    private LabelSetDto.CreateOrUpdateRequest parseAndValidateRequest(JsonNode requestJson) {
        if (requestJson == null || requestJson.isNull()) {
            throw new IllegalArgumentException("Request body is required");
        }

        labelSetDefinitionValidator.rejectUnknownFields(requestJson);

        try {
            LabelSetDto.CreateOrUpdateRequest request = objectMapper.treeToValue(requestJson, LabelSetDto.CreateOrUpdateRequest.class);
            LabelSetDto.CreateOrUpdateRequest normalizedRequest = normalizeRegionCustomSubTypes(request);
            labelSetDefinitionValidator.validate(normalizedRequest);
            return normalizedRequest;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid label set payload: " + e.getOriginalMessage());
        }
    }

    private LabelSetDto.CreateOrUpdateRequest normalizeRegionCustomSubTypes(LabelSetDto.CreateOrUpdateRequest request) {
        if (request == null || request.labels() == null || request.labels().isEmpty()) {
            return request;
        }

        boolean changed = false;
        List<LabelSetDto.Label> normalizedLabels = new ArrayList<>(request.labels().size());

        for (LabelSetDto.Label label : request.labels()) {
            if (label == null || label.scope() != LabelSetDto.LabelScope.REGION || label.mapping() == null || label.mapping().pageXml() == null) {
                normalizedLabels.add(label);
                continue;
            }

            LabelSetDto.PageXml pageXml = label.mapping().pageXml();
            if (pageXml.customSubType() != null) {
                normalizedLabels.add(label);
                continue;
            }

            LabelSetDto.PageXml normalizedPageXml = new LabelSetDto.PageXml(
                    pageXml.regionType(),
                    pageXml.textType(),
                    "",
                    pageXml.customKey(),
                    pageXml.customData()
            );
            LabelSetDto.Mapping normalizedMapping = new LabelSetDto.Mapping(label.mapping().altoXml(), normalizedPageXml);
            LabelSetDto.Label normalizedLabel = new LabelSetDto.Label(
                    label.id(),
                    label.scope(),
                    label.name(),
                    label.description(),
                    label.color(),
                    label.hasText(),
                    label.isContainer(),
                    label.group(),
                    normalizedMapping
            );

            normalizedLabels.add(normalizedLabel);
            changed = true;
        }

        if (!changed) {
            return request;
        }
        return new LabelSetDto.CreateOrUpdateRequest(request.meta(), normalizedLabels);
    }

    @CacheEvict(value = "labelSets", allEntries = true)
    public void deleteLabelSet(String userId, String workspaceId, String labelSetId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        LabelSet labelSet = labelSetRepository.findByIdAndWorkspaceId(labelSetId, workspaceId)
            .orElseThrow(() -> new ResourceNotFoundException("Label set not found: " + labelSetId));

        if (labelSet.isSystem()) {
            throw new IllegalStateException("Cannot delete system label set: " + labelSet.getName());
        }

        labelSetRepository.deleteById(Objects.requireNonNull(labelSetId, "labelSetId"));
    }

    @Cacheable(value = "labelSets", key = "#workspaceId + ':list'")
    @Transactional(readOnly = true)
    public List<LabelSetDto.SummaryResponse> getLabelSets(String userId, String workspaceId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        List<LabelSet> labelSets = labelSetRepository.findByWorkspaceId(workspaceId);
        return labelSets.stream()
                .map(this::convertToLabelSetSummaryResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "labelSets", key = "#workspaceId + ':' + #labelSetId")
    @Transactional(readOnly = true)
    public LabelSetDto.Response getLabelSet(String userId, String workspaceId, String labelSetId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        LabelSet labelSet = labelSetRepository.findByIdAndWorkspaceId(labelSetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Label set not found: " + labelSetId));

        return convertToLabelSetResponse(labelSet);
    }

    @Cacheable(value = "labelSets", key = "#workspaceId + ':search:' + #query")
    @Transactional(readOnly = true)
    public List<LabelSetDto.SummaryResponse> searchLabelSets(String userId, String workspaceId, String query) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        List<LabelSet> labelSets = labelSetRepository.findLabelSetsInWorkspaceBySearch(workspaceId, query);
        return labelSets.stream()
                .map(this::convertToLabelSetSummaryResponse)
                .collect(Collectors.toList());
    }

    private LabelSetDto.CreateOrUpdateRequest readDefinition(LabelSet labelSet) {
        JsonNode sanitized = JsonNodeUtils.removeFieldRecursively(labelSet.getDefinition(), "icon").node();
        return objectMapper.convertValue(sanitized, LabelSetDto.CreateOrUpdateRequest.class);
    }

    private LabelSetDto.Response convertToLabelSetResponse(LabelSet labelSet) {
        LabelSetDto.CreateOrUpdateRequest definition = readDefinition(labelSet);

        List<String> tags = labelSet.getTags() != null ? labelSet.getTags() : new ArrayList<>();
        LabelSetDto.Meta metaWithTags = new LabelSetDto.Meta(
            definition.meta().name(),
            definition.meta().description(),
            tags,
            definition.meta().altoEnabled(),
            labelSet.isSystem()
        );

        return new LabelSetDto.Response(
                labelSet.getId(),
            metaWithTags,
            definition.labels(),
                labelSet.getCreated(),
                labelSet.getUpdated()
        );
    }

    private LabelSetDto.SummaryResponse convertToLabelSetSummaryResponse(LabelSet labelSet) {
        LabelSetDto.CreateOrUpdateRequest definition = readDefinition(labelSet);

        List<String> tags = labelSet.getTags() != null ? labelSet.getTags() : new ArrayList<>();
        LabelSetDto.Meta metaWithTags = new LabelSetDto.Meta(
            definition.meta().name(),
            definition.meta().description(),
            tags,
            definition.meta().altoEnabled(),
            labelSet.isSystem()
        );

        return new LabelSetDto.SummaryResponse(
                labelSet.getId(),
            metaWithTags,
            definition.labels() == null ? 0 : definition.labels().size(),
                labelSet.getCreated(),
                labelSet.getUpdated()
        );
    }
}
