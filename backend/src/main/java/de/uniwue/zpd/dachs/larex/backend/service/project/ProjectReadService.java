package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.dto.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectDto;
import de.uniwue.zpd.dachs.larex.backend.dto.TagSetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.tag.TagLookupService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectReadService {

    private final ProjectStarService projectStarService;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final TagLookupService tagLookupService;
    private final AuthorizationPolicyService authorizationPolicyService;

    public ProjectReadService(ProjectStarService projectStarService,
                              PageRepository pageRepository,
                              PageImageRepository pageImageRepository,
                              PageXmlRepository pageXmlRepository,
                              TagLookupService tagLookupService,
                              AuthorizationPolicyService authorizationPolicyService) {
        this.projectStarService = projectStarService;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.tagLookupService = tagLookupService;
        this.authorizationPolicyService = authorizationPolicyService;
    }

    public ProjectDto.Response toResponse(Project project, String userId) {
        List<ProjectDto.Response> responses = toResponses(List.of(project), userId);
        return responses.isEmpty() ? null : responses.getFirst();
    }

    public List<ProjectDto.Response> toResponses(List<Project> projects, String userId) {
        if (projects == null || projects.isEmpty()) {
            return List.of();
        }

        List<String> projectIds = projects.stream().map(Project::getId).toList();
        Set<String> starredProjectIds = projectStarService.getAllStarredProjectIds(userId);
        Map<String, Long> pageCountByProjectId = toLongMap(pageRepository.countByProjectIds(projectIds));
        Map<String, Long> imageBytesByProjectId = toLongMap(pageImageRepository.sumFileSizeByProjectIds(projectIds));
        Map<String, Long> xmlBytesByProjectId = toLongMap(pageXmlRepository.sumFileSizeByProjectIds(projectIds));
        Map<String, Map<String, TagSetDto.TagNode>> tagLookupByProjectId = tagLookupService.buildTagLookupForProjects(projects);

        List<ProjectDto.Response> responses = new ArrayList<>(projects.size());
        for (Project project : projects) {
            Long imageBytes = imageBytesByProjectId.getOrDefault(project.getId(), 0L);
            Long xmlBytes = xmlBytesByProjectId.getOrDefault(project.getId(), 0L);
            Long storageUsedBytes = imageBytes + xmlBytes;

            Map<String, TagSetDto.TagNode> projectTagLookup = tagLookupByProjectId.getOrDefault(project.getId(), Map.of());
            List<PageDto.ResolvedTag> resolvedTags = resolveTags(project.getTags(), projectTagLookup);

            responses.add(ProjectDto.Response.of(
                    project.getId(),
                    project.getName(),
                    project.getDescription(),
                    project.getTags(),
                    resolvedTags,
                    project.getCreated(),
                    project.getUpdated(),
                    pageCountByProjectId.getOrDefault(project.getId(), 0L).intValue(),
                    starredProjectIds.contains(project.getId()),
                    storageUsedBytes,
                    project.isLocked(),
                    project.getLockedReason(),
                    project.getCodec() != null ? project.getCodec().getId() : null,
                    project.getLabelSet() != null ? project.getLabelSet().getId() : null,
                    project.getDictionary() != null ? project.getDictionary().getId() : null,
                    project.getTagSet() != null ? project.getTagSet().getId() : null,
                    project.getEffectiveDefaultGtIndex(),
                    project.getDefaultRecognitionIndicesList(),
                    authorizationPolicyService.resolveProjectCapabilities(project, userId)
            ));
        }
        return responses;
    }

    private Map<String, Long> toLongMap(Collection<Object[]> rows) {
        return rows.stream()
                .filter(row -> row != null && row.length >= 2 && row[0] != null && row[1] != null)
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a,
                        HashMap::new
                ));
    }

    private List<PageDto.ResolvedTag> resolveTags(List<String> tagIds, Map<String, TagSetDto.TagNode> tagLookup) {
        if (tagLookup == null || tagLookup.isEmpty() || tagIds == null) {
            return null;
        }
        return tagIds.stream()
                .map(tagId -> {
                    TagSetDto.TagNode tag = tagLookup.get(tagId);
                    if (tag != null) {
                        return new PageDto.ResolvedTag(tagId, tag.title(), tag.color());
                    }
                    return new PageDto.ResolvedTag(tagId, tagId, null);
                })
                .toList();
    }
}
