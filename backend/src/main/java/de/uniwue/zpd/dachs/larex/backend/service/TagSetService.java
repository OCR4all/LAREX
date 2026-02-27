package de.uniwue.zpd.dachs.larex.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.TagSetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.util.JsonNodeUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class TagSetService {

    private final TagSetRepository tagSetRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ObjectMapper objectMapper;
    private final TagSetDefinitionValidator tagSetDefinitionValidator;

    public TagSetService(TagSetRepository tagSetRepository,
                          ProjectRepository projectRepository,
                          WorkspaceAccessService workspaceAccessService,
                          ObjectMapper objectMapper,
                          TagSetDefinitionValidator tagSetDefinitionValidator) {
        this.tagSetRepository = tagSetRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.objectMapper = objectMapper;
        this.tagSetDefinitionValidator = tagSetDefinitionValidator;
    }

    @CacheEvict(value = "tagSets", allEntries = true)
    public TagSetDto.Response createTagSet(String userId, String workspaceId, JsonNode requestJson) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        TagSetDto.CreateOrUpdateRequest request = parseAndValidateRequest(requestJson);

        String name = request.meta().name();
        String description = request.meta().description();
        List<String> tags = request.meta().tags() != null ? request.meta().tags() : new ArrayList<>();

        if (tagSetRepository.existsByNameAndWorkspaceId(name, workspaceId)) {
            throw new IllegalArgumentException("Tag set with name '" + name + "' already exists in this workspace");
        }

        TagSet tagSet = new TagSet(workspaceId, name, description, objectMapper.valueToTree(request));
        tagSet.setTags(tags);
        tagSet = tagSetRepository.save(tagSet);
        return convertToTagSetResponse(tagSet);
    }

    @CacheEvict(value = "tagSets", allEntries = true)
    public TagSetDto.Response updateTagSet(String userId, String workspaceId, String tagSetId, JsonNode requestJson) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        TagSetDto.CreateOrUpdateRequest request = parseAndValidateRequest(requestJson);

        TagSet tagSet = tagSetRepository.findByIdAndWorkspaceId(tagSetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag set not found: " + tagSetId));

        String name = request.meta().name();
        String description = request.meta().description();
        List<String> tags = request.meta().tags() != null ? request.meta().tags() : new ArrayList<>();

        if (!tagSet.getName().equals(name) &&
            tagSetRepository.existsByNameAndWorkspaceId(name, workspaceId)) {
            throw new IllegalArgumentException("Tag set with name '" + name + "' already exists in this workspace");
        }

        tagSet.setName(name);
        tagSet.setDescription(description);
        tagSet.setTags(tags);
        tagSet.setDefinition(objectMapper.valueToTree(request));

        tagSet = tagSetRepository.save(tagSet);
        return convertToTagSetResponse(tagSet);
    }

    private TagSetDto.CreateOrUpdateRequest parseAndValidateRequest(JsonNode requestJson) {
        if (requestJson == null || requestJson.isNull()) {
            throw new IllegalArgumentException("Request body is required");
        }

        tagSetDefinitionValidator.rejectUnknownFields(requestJson);

        try {
            TagSetDto.CreateOrUpdateRequest request = objectMapper.treeToValue(requestJson, TagSetDto.CreateOrUpdateRequest.class);
            tagSetDefinitionValidator.validate(request);
            return request;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid tag set payload: " + e.getOriginalMessage());
        }
    }

    @CacheEvict(value = "tagSets", allEntries = true)
    public void deleteTagSet(String userId, String workspaceId, String tagSetId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        tagSetRepository.findByIdAndWorkspaceId(tagSetId, workspaceId)
            .orElseThrow(() -> new ResourceNotFoundException("Tag set not found: " + tagSetId));

        List<Project> projects = projectRepository.findByTagSetId(tagSetId);
        for (Project project : projects) {
            project.setTagSet(null);
        }
        projectRepository.saveAll(projects);

        tagSetRepository.deleteById(Objects.requireNonNull(tagSetId, "tagSetId"));
    }

    @Cacheable(value = "tagSets", key = "#workspaceId + ':list'")
    @Transactional(readOnly = true)
    public List<TagSetDto.SummaryResponse> getTagSets(String userId, String workspaceId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        List<TagSet> tagSets = tagSetRepository.findByWorkspaceId(workspaceId);
        return tagSets.stream()
                .map(this::convertToTagSetSummaryResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "tagSets", key = "#workspaceId + ':' + #tagSetId")
    @Transactional(readOnly = true)
    public TagSetDto.Response getTagSet(String userId, String workspaceId, String tagSetId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        TagSet tagSet = tagSetRepository.findByIdAndWorkspaceId(tagSetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag set not found: " + tagSetId));

        return convertToTagSetResponse(tagSet);
    }

    @Cacheable(value = "tagSets", key = "#workspaceId + ':search:' + #query")
    @Transactional(readOnly = true)
    public List<TagSetDto.SummaryResponse> searchTagSets(String userId, String workspaceId, String query) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        List<TagSet> tagSets = tagSetRepository.findTagSetsInWorkspaceBySearch(workspaceId, query);
        return tagSets.stream()
                .map(this::convertToTagSetSummaryResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "tagSets", key = "#workspaceId + ':flattened:' + #tagSetId")
    @Transactional(readOnly = true)
    public List<TagSetDto.FlattenedTag> getFlattenedTags(String userId, String workspaceId, String tagSetId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        TagSet tagSet = tagSetRepository.findByIdAndWorkspaceId(tagSetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag set not found: " + tagSetId));

        TagSetDto.CreateOrUpdateRequest definition = readDefinition(tagSet);

        List<TagSetDto.FlattenedTag> flattened = new ArrayList<>();
        Map<String, List<String>> descendantsMap = buildDescendantsMap(definition.tags());

        for (TagSetDto.TagNode tag : definition.tags()) {
            flattenTag(tag, "", new ArrayList<>(), flattened, descendantsMap);
        }

        return flattened;
    }

    @Transactional(readOnly = true)
    public TagSetDto.ValidationResult validateTags(String userId, String workspaceId, String tagSetId, List<String> tagIds) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        TagSet tagSet = tagSetRepository.findByIdAndWorkspaceId(tagSetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag set not found: " + tagSetId));

        TagSetDto.CreateOrUpdateRequest definition = readDefinition(tagSet);
        Set<String> validIds = tagSetDefinitionValidator.collectAllTagIds(definition.tags());

        List<String> validTags = new ArrayList<>();
        List<String> invalidTags = new ArrayList<>();

        for (String tagId : tagIds) {
            if (validIds.contains(tagId)) {
                validTags.add(tagId);
            } else {
                invalidTags.add(tagId);
            }
        }

        boolean valid = invalidTags.isEmpty();
        String message = valid ? "All tags are valid" : "Found " + invalidTags.size() + " invalid tag(s)";

        return new TagSetDto.ValidationResult(valid, invalidTags, validTags, message);
    }

    @Transactional(readOnly = true)
    public List<String> pruneTags(String userId, String workspaceId, String tagSetId, List<String> tagIds) {
        TagSetDto.ValidationResult result = validateTags(userId, workspaceId, tagSetId, tagIds);
        return result.validTags();
    }

    @Transactional(readOnly = true)
    public Set<String> getDescendantTagIds(String userId, String workspaceId, String tagSetId, String tagId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        TagSet tagSet = tagSetRepository.findByIdAndWorkspaceId(tagSetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag set not found: " + tagSetId));

        TagSetDto.CreateOrUpdateRequest definition = readDefinition(tagSet);
        Map<String, List<String>> descendantsMap = buildDescendantsMap(definition.tags());

        return new HashSet<>(descendantsMap.getOrDefault(tagId, Collections.emptyList()));
    }

    private TagSetDto.CreateOrUpdateRequest readDefinition(TagSet tagSet) {
        JsonNode sanitized = JsonNodeUtils.removeFieldRecursively(tagSet.getDefinition(), "icon").node();
        return objectMapper.convertValue(sanitized, TagSetDto.CreateOrUpdateRequest.class);
    }

    private TagSetDto.Response convertToTagSetResponse(TagSet tagSet) {
        TagSetDto.CreateOrUpdateRequest definition = readDefinition(tagSet);

        List<String> tags = tagSet.getTags() != null ? tagSet.getTags() : new ArrayList<>();
        TagSetDto.Meta metaWithTags = new TagSetDto.Meta(
            definition.meta().name(),
            definition.meta().description(),
            tags
        );

        int totalTagCount = tagSetDefinitionValidator.countTags(definition.tags());

        return new TagSetDto.Response(
                tagSet.getId(),
            metaWithTags,
            definition.tags(),
            totalTagCount,
                tagSet.getCreated(),
                tagSet.getUpdated()
        );
    }

    private TagSetDto.SummaryResponse convertToTagSetSummaryResponse(TagSet tagSet) {
        TagSetDto.CreateOrUpdateRequest definition = readDefinition(tagSet);

        List<String> tags = tagSet.getTags() != null ? tagSet.getTags() : new ArrayList<>();
        TagSetDto.Meta metaWithTags = new TagSetDto.Meta(
            definition.meta().name(),
            definition.meta().description(),
            tags
        );

        int tagCount = tagSetDefinitionValidator.countTags(definition.tags());

        return new TagSetDto.SummaryResponse(
                tagSet.getId(),
            metaWithTags,
            tagCount,
                tagSet.getCreated(),
                tagSet.getUpdated()
        );
    }

    private void flattenTag(TagSetDto.TagNode tag, String parentPath, List<String> ancestorIds,
                           List<TagSetDto.FlattenedTag> result, Map<String, List<String>> descendantsMap) {
        String path = parentPath.isEmpty() ? tag.title() : parentPath + " > " + tag.title();

        List<String> descendants = descendantsMap.getOrDefault(tag.id(), Collections.emptyList());

        result.add(new TagSetDto.FlattenedTag(
            tag.id(),
            tag.title(),
            path,
            tag.color(),
            new ArrayList<>(ancestorIds),
            descendants
        ));

        if (tag.children() != null) {
            List<String> newAncestors = new ArrayList<>(ancestorIds);
            newAncestors.add(tag.id());
            for (TagSetDto.TagNode child : tag.children()) {
                flattenTag(child, path, newAncestors, result, descendantsMap);
            }
        }
    }

    private Map<String, List<String>> buildDescendantsMap(List<TagSetDto.TagNode> tags) {
        Map<String, List<String>> descendantsMap = new HashMap<>();

        for (TagSetDto.TagNode tag : tags) {
            collectDescendants(tag, descendantsMap);
        }

        return descendantsMap;
    }

    private List<String> collectDescendants(TagSetDto.TagNode tag, Map<String, List<String>> descendantsMap) {
        List<String> descendants = new ArrayList<>();

        if (tag.children() != null) {
            for (TagSetDto.TagNode child : tag.children()) {
                descendants.add(child.id());
                descendants.addAll(collectDescendants(child, descendantsMap));
            }
        }

        descendantsMap.put(tag.id(), descendants);
        return descendants;
    }
}
