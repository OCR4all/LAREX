package de.uniwue.zpd.dachs.larex.backend.service.label;

import de.uniwue.zpd.dachs.larex.backend.dto.BulkDeleteDto;
import de.uniwue.zpd.dachs.larex.backend.dto.LabelSetDto;
import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import de.uniwue.zpd.dachs.larex.backend.util.JsonNodeUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private final AuthorizationPolicyService authorizationPolicyService;

    public LabelSetService(LabelSetRepository labelSetRepository,
                          WorkspaceAccessService workspaceAccessService,
                          ObjectMapper objectMapper,
                          LabelSetDefinitionValidator labelSetDefinitionValidator,
                          AuthorizationPolicyService authorizationPolicyService) {
        this.labelSetRepository = labelSetRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.objectMapper = objectMapper;
        this.labelSetDefinitionValidator = labelSetDefinitionValidator;
        this.authorizationPolicyService = authorizationPolicyService;
    }

    @CacheEvict(value = "labelSets", allEntries = true)
    public LabelSetDto.Response createLabelSet(String userId, String workspaceId, JsonNode requestJson) {
        workspaceAccessService.requireManageToolkitAccess(workspaceId, userId);

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
        return convertToLabelSetResponse(labelSet, userId);
    }

    @CacheEvict(value = "labelSets", allEntries = true)
    public LabelSetDto.Response updateLabelSet(String userId, String workspaceId, String labelSetId, JsonNode requestJson) {
        workspaceAccessService.requireManageToolkitAccess(workspaceId, userId);

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
        return convertToLabelSetResponse(labelSet, userId);
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
        } catch (JacksonException e) {
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
            String normalizedCustomSubType = pageXml.customSubType();
            if (normalizedCustomSubType == null) {
                normalizedCustomSubType = "";
            }
            LabelSetDto.PageRegionType normalizedRegionType = pageXml.regionType();
            if (normalizedRegionType == null && "custom".equals(normalizedCustomSubType)) {
                normalizedRegionType = LabelSetDto.PageRegionType.UnknownRegion;
            }

            if (normalizedCustomSubType.equals(pageXml.customSubType()) && normalizedRegionType == pageXml.regionType()) {
                normalizedLabels.add(label);
                continue;
            }

            LabelSetDto.PageXml normalizedPageXml = new LabelSetDto.PageXml(
                    normalizedRegionType,
                    pageXml.textType(),
                    normalizedCustomSubType,
                    pageXml.customKey(),
                    pageXml.customData()
            );
            LabelSetDto.Mapping normalizedMapping = new LabelSetDto.Mapping(normalizedPageXml);
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
        workspaceAccessService.requireManageToolkitAccess(workspaceId, userId);

        LabelSet labelSet = labelSetRepository.findByIdAndWorkspaceId(labelSetId, workspaceId)
            .orElseThrow(() -> new ResourceNotFoundException("Label set not found: " + labelSetId));

        if (labelSet.isSystem()) {
            throw new IllegalStateException("Cannot delete system label set: " + labelSet.getName());
        }

        labelSetRepository.deleteById(Objects.requireNonNull(labelSetId, "labelSetId"));
    }

    public BulkDeleteDto.BulkDeleteResponse bulkDeleteLabelSets(String userId, String workspaceId, List<String> ids) {
        List<String> deletedIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String labelSetId : new LinkedHashSet<>(ids)) {
            if (labelSetId == null || labelSetId.isBlank()) {
                failedIds.add(Objects.toString(labelSetId, "<null>"));
                errors.add("Cannot delete label set with a blank ID.");
                continue;
            }

            try {
                deleteLabelSet(userId, workspaceId, labelSetId);
                deletedIds.add(labelSetId);
            } catch (RuntimeException ex) {
                failedIds.add(labelSetId);
                errors.add("Failed to delete label set " + labelSetId + ": " + describeError(ex));
            }
        }

        return new BulkDeleteDto.BulkDeleteResponse(
                deletedIds.size(),
                failedIds.size(),
                deletedIds,
                failedIds,
                errors
        );
    }

    @Cacheable(value = "labelSets", key = "#workspaceId + ':list'")
    @Transactional(readOnly = true)
    public List<LabelSetDto.SummaryResponse> getLabelSets(String userId, String workspaceId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        List<LabelSet> labelSets = labelSetRepository.findByWorkspaceId(workspaceId);
        return labelSets.stream()
                .map(labelSet -> convertToLabelSetSummaryResponse(labelSet, userId))
                .collect(Collectors.toList());
    }

    @Cacheable(value = "labelSets", key = "#workspaceId + ':' + #labelSetId")
    @Transactional(readOnly = true)
    public LabelSetDto.Response getLabelSet(String userId, String workspaceId, String labelSetId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        LabelSet labelSet = labelSetRepository.findByIdAndWorkspaceId(labelSetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Label set not found: " + labelSetId));

        return convertToLabelSetResponse(labelSet, userId);
    }

    @Cacheable(value = "labelSets", key = "#workspaceId + ':search:' + #query")
    @Transactional(readOnly = true)
    public List<LabelSetDto.SummaryResponse> searchLabelSets(String userId, String workspaceId, String query) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        List<LabelSet> labelSets = labelSetRepository.findLabelSetsInWorkspaceBySearch(workspaceId, query);
        return labelSets.stream()
                .map(labelSet -> convertToLabelSetSummaryResponse(labelSet, userId))
                .collect(Collectors.toList());
    }

    private LabelSetDto.CreateOrUpdateRequest readDefinition(LabelSet labelSet) {
        JsonNode sanitized = JsonNodeUtils.removeFieldRecursively(labelSet.getDefinition(), "icon").node();
        return objectMapper.convertValue(sanitized, LabelSetDto.CreateOrUpdateRequest.class);
    }

    private LabelSetDto.Response convertToLabelSetResponse(LabelSet labelSet, String userId) {
        LabelSetDto.CreateOrUpdateRequest definition = readDefinition(labelSet);

        List<String> tags = labelSet.getTags() != null ? labelSet.getTags() : new ArrayList<>();
        LabelSetDto.Meta metaWithTags = new LabelSetDto.Meta(
            definition.meta().name(),
            definition.meta().description(),
            tags,
            labelSet.isSystem()
        );

        AuthorizationCapabilitiesDto.ResourceCapabilities capabilities = authorizationPolicyService
                .resolveWorkspaceResourceCapabilities(labelSet.getWorkspaceId(), userId);
        if (labelSet.isSystem()) {
            capabilities = new AuthorizationCapabilitiesDto.ResourceCapabilities(false, false, false);
        }

        return new LabelSetDto.Response(
                labelSet.getId(),
            metaWithTags,
            definition.labels(),
                labelSet.getCreated(),
                labelSet.getUpdated(),
                capabilities
        );
    }

    private LabelSetDto.SummaryResponse convertToLabelSetSummaryResponse(LabelSet labelSet, String userId) {
        LabelSetDto.CreateOrUpdateRequest definition = readDefinition(labelSet);

        List<String> tags = labelSet.getTags() != null ? labelSet.getTags() : new ArrayList<>();
        LabelSetDto.Meta metaWithTags = new LabelSetDto.Meta(
            definition.meta().name(),
            definition.meta().description(),
            tags,
            labelSet.isSystem()
        );

        AuthorizationCapabilitiesDto.ResourceCapabilities capabilities = authorizationPolicyService
                .resolveWorkspaceResourceCapabilities(labelSet.getWorkspaceId(), userId);
        if (labelSet.isSystem()) {
            capabilities = new AuthorizationCapabilitiesDto.ResourceCapabilities(false, false, false);
        }

        return new LabelSetDto.SummaryResponse(
                labelSet.getId(),
            metaWithTags,
            definition.labels() == null ? 0 : definition.labels().size(),
                labelSet.getCreated(),
                labelSet.getUpdated(),
                capabilities
        );
    }

    private String describeError(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? "Unexpected error" : ex.getMessage();
    }
}
