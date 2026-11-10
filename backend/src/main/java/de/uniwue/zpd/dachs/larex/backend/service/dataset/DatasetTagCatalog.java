package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import de.uniwue.zpd.dachs.larex.backend.dto.DatasetDto;
import de.uniwue.zpd.dachs.larex.backend.dto.TagSetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.service.tag.TagLookupService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the tag IDs stored on dataset items against the tag sets of their source projects.
 * Pages store tag IDs, so names, hierarchy and colors only exist in the project's tag set.
 */
final class DatasetTagCatalog {

    private record ResolvedTag(TagSetDto.TagNode node, List<String> path, List<String> selfAndAncestorIds, String tagSetName) {}

    private final Map<String, Map<String, ResolvedTag>> tagsByProject;

    private DatasetTagCatalog(Map<String, Map<String, ResolvedTag>> tagsByProject) {
        this.tagsByProject = tagsByProject;
    }

    static DatasetTagCatalog of(Collection<Project> projects, TagLookupService tagLookupService) {
        Map<String, Map<String, ResolvedTag>> byTagSet = new HashMap<>();
        Map<String, Map<String, ResolvedTag>> byProject = new HashMap<>();
        for (Project project : projects) {
            TagSet tagSet = project.getTagSet();
            if (tagSet == null || tagSet.getId() == null) continue;
            byProject.put(project.getId(), byTagSet.computeIfAbsent(tagSet.getId(),
                    id -> resolve(tagLookupService.buildTagLookupForProject(project), tagSet.getName())));
        }
        return new DatasetTagCatalog(byProject);
    }

    private static Map<String, ResolvedTag> resolve(Map<String, TagSetDto.TagNode> lookup, String tagSetName) {
        Map<String, String> parentIds = new HashMap<>();
        for (TagSetDto.TagNode node : lookup.values()) {
            if (node.children() == null) continue;
            for (TagSetDto.TagNode child : node.children()) {
                parentIds.put(child.id(), node.id());
            }
        }

        Map<String, ResolvedTag> resolved = new HashMap<>();
        for (TagSetDto.TagNode node : lookup.values()) {
            List<String> selfAndAncestorIds = new ArrayList<>();
            LinkedList<String> path = new LinkedList<>();
            Set<String> visited = new HashSet<>();
            for (String current = node.id(); current != null && visited.add(current); current = parentIds.get(current)) {
                selfAndAncestorIds.add(current);
                TagSetDto.TagNode currentNode = lookup.get(current);
                path.addFirst(currentNode == null ? current : currentNode.title());
            }
            resolved.put(node.id(), new ResolvedTag(node, List.copyOf(path), List.copyOf(selfAndAncestorIds), tagSetName));
        }
        return resolved;
    }

    /** The item's own tags plus every ancestor of them, so a parent tag also covers pages tagged with its children. */
    Set<String> tagsWithAncestors(DatasetItem item) {
        Map<String, ResolvedTag> projectTags = tagsByProject.getOrDefault(item.getSourceProjectId(), Map.of());
        Set<String> result = new LinkedHashSet<>();
        for (String tagId : item.getSourcePageTags() == null ? List.<String>of() : item.getSourcePageTags()) {
            ResolvedTag tag = projectTags.get(tagId);
            if (tag == null) {
                result.add(tagId);
            } else {
                result.addAll(tag.selfAndAncestorIds());
            }
        }
        return result;
    }

    /** Every tag the items carry, directly or through a child tag, plus the selected ones even when no page carries them. */
    List<DatasetDto.TagOption> options(List<DatasetItem> items, Collection<String> selectedTagIds) {
        Map<String, Integer> pageCounts = new LinkedHashMap<>();
        Map<String, ResolvedTag> resolved = new HashMap<>();
        for (DatasetItem item : items) {
            Map<String, ResolvedTag> projectTags = tagsByProject.getOrDefault(item.getSourceProjectId(), Map.of());
            for (String tagId : tagsWithAncestors(item)) {
                pageCounts.merge(tagId, 1, Integer::sum);
                ResolvedTag tag = projectTags.get(tagId);
                if (tag != null) resolved.putIfAbsent(tagId, tag);
            }
        }
        for (String tagId : selectedTagIds == null ? List.<String>of() : selectedTagIds) {
            pageCounts.putIfAbsent(tagId, 0);
        }

        return pageCounts.entrySet().stream()
                .map(entry -> {
                    String tagId = entry.getKey();
                    ResolvedTag tag = resolved.containsKey(tagId) ? resolved.get(tagId) : findInAnyProject(tagId);
                    if (tag == null) {
                        return new DatasetDto.TagOption(tagId, tagId, List.of(tagId), null, null, entry.getValue());
                    }
                    return new DatasetDto.TagOption(tagId, tag.node().title(), tag.path(), tag.node().color(), tag.tagSetName(), entry.getValue());
                })
                .sorted(Comparator.comparing((DatasetDto.TagOption option) -> String.join("\u0000", option.path()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(option -> option.tagSetName() == null ? "" : option.tagSetName())
                        .thenComparing(DatasetDto.TagOption::id))
                .toList();
    }

    private ResolvedTag findInAnyProject(String tagId) {
        for (Map<String, ResolvedTag> projectTags : tagsByProject.values()) {
            ResolvedTag tag = projectTags.get(tagId);
            if (tag != null) return tag;
        }
        return null;
    }
}
