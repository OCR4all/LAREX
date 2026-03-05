package de.uniwue.zpd.dachs.larex.backend.service.tag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.TagSetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.util.JsonNodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TagLookupService {

    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public TagLookupService(ProjectRepository projectRepository, ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, TagSetDto.TagNode> buildTagLookupForProject(String projectId) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) return Collections.emptyMap();

        return buildTagLookupForProject(projectOpt.get());
    }

    public Map<String, TagSetDto.TagNode> buildTagLookupForProject(Project project) {
        if (project == null) return Collections.emptyMap();

        TagSet tagSet = project.getTagSet();
        if (tagSet == null) return Collections.emptyMap();

        JsonNode sanitized = JsonNodeUtils.removeFieldRecursively(tagSet.getDefinition(), "icon").node();
        TagSetDto.CreateOrUpdateRequest def = objectMapper.convertValue(sanitized, TagSetDto.CreateOrUpdateRequest.class);
        if (def == null || def.tags() == null) return Collections.emptyMap();

        Map<String, TagSetDto.TagNode> lookup = new HashMap<>();
        collectTags(def.tags(), lookup);
        return lookup;
    }

    /**
     * Build lookups for multiple projects with per-tag-set memoization.
     */
    public Map<String, Map<String, TagSetDto.TagNode>> buildTagLookupForProjects(Collection<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return Map.of();
        }

        Map<String, Map<String, TagSetDto.TagNode>> result = new HashMap<>();
        Map<String, Map<String, TagSetDto.TagNode>> byTagSetId = new HashMap<>();

        for (Project project : projects) {
            if (project == null || project.getId() == null) {
                continue;
            }
            TagSet tagSet = project.getTagSet();
            if (tagSet == null || tagSet.getId() == null) {
                result.put(project.getId(), Collections.emptyMap());
                continue;
            }

            Map<String, TagSetDto.TagNode> cached = byTagSetId.computeIfAbsent(
                    tagSet.getId(),
                    key -> buildTagLookupForProject(project)
            );
            result.put(project.getId(), cached);
        }

        return result;
    }

    private void collectTags(List<TagSetDto.TagNode> tags, Map<String, TagSetDto.TagNode> lookup) {
        if (tags == null) return;
        for (TagSetDto.TagNode tag : tags) {
            lookup.put(tag.id(), tag);
            if (tag.children() != null) {
                collectTags(tag.children(), lookup);
            }
        }
    }
}
