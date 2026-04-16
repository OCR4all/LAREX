package de.uniwue.zpd.dachs.larex.backend.service.machine;

import de.uniwue.zpd.dachs.larex.backend.dto.PatProjectDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PatProjectReadService {

    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;

    public PatProjectReadService(ProjectRepository projectRepository,
                                 PageRepository pageRepository) {
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
    }

    public List<PatProjectDto.ProjectSummaryResponse> listProjects(String workspaceId) {
        List<Project> projects = projectRepository.findByLibraryWorkspaceId(workspaceId);
        if (projects.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> pageCountByProjectId = resolvePageCountByProjectId(
                projects.stream().map(Project::getId).toList()
        );

        return projects.stream()
                .sorted(Comparator.comparing(project -> normalizeForSort(project.getName())))
                .map(project -> new PatProjectDto.ProjectSummaryResponse(
                        project.getId(),
                        workspaceId,
                        project.getName(),
                        project.getDescription(),
                        project.getTags() == null ? List.of() : List.copyOf(project.getTags()),
                        pageCountByProjectId.getOrDefault(project.getId(), 0),
                        project.getCreated(),
                        project.getUpdated()
                ))
                .toList();
    }

    public Optional<PatProjectDto.ProjectDetailResponse> getProjectDetail(String workspaceId, String projectId) {
        Optional<Project> projectOpt = projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId);
        if (projectOpt.isEmpty()) {
            return Optional.empty();
        }

        Project project = projectOpt.get();
        List<Page> pages = pageRepository.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(page -> normalizeForSort(page.getName())))
                .toList();

        List<PatProjectDto.PageDetailResponse> pageDetails = pages.stream()
                .map(this::toPageDetailResponse)
                .toList();

        return Optional.of(new PatProjectDto.ProjectDetailResponse(
                project.getId(),
                workspaceId,
                project.getName(),
                project.getDescription(),
                project.getTags() == null ? List.of() : List.copyOf(project.getTags()),
                pageDetails.size(),
                project.getCreated(),
                project.getUpdated(),
                pageDetails
        ));
    }

    private PatProjectDto.PageDetailResponse toPageDetailResponse(Page page) {
        List<PageImage> images = page.getImages() == null
                ? List.of()
                : page.getImages().stream()
                .sorted(Comparator.comparing(image -> normalizeForSort(image.getFileName())))
                .toList();
        List<PageXml> xmlFiles = page.getXmlFiles() == null
                ? List.of()
                : page.getXmlFiles().stream()
                .sorted(Comparator.comparing(xml -> normalizeForSort(xml.getFileName())))
                .toList();

        List<String> imageVariants = images.stream()
                .map(PageImage::getVariant)
                .filter(variant -> variant != null && !variant.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();

        List<String> xmlVariants = xmlFiles.stream()
                .map(PageXml::getVariant)
                .filter(variant -> variant != null && !variant.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();

        List<PatProjectDto.ImageFileResponse> imageDetails = images.stream()
                .map(image -> new PatProjectDto.ImageFileResponse(
                        image.getId(),
                        image.getFileName(),
                        image.getVariant(),
                        image.getBaseName(),
                        image.getMimeType(),
                        image.getFileSize()
                ))
                .toList();

        List<PatProjectDto.XmlFileResponse> xmlDetails = xmlFiles.stream()
                .map(xml -> new PatProjectDto.XmlFileResponse(
                        xml.getId(),
                        xml.getFileName(),
                        xml.getVariant(),
                        xml.getSchema() == null ? null : xml.getSchema().name(),
                        xml.getSchemaVersion(),
                        xml.getMimeType(),
                        xml.getFileSize()
                ))
                .toList();

        return new PatProjectDto.PageDetailResponse(
                page.getId(),
                page.getName(),
                page.getDescription(),
                page.getTags() == null ? List.of() : List.copyOf(page.getTags()),
                imageVariants,
                xmlVariants,
                page.getCreated(),
                page.getUpdated(),
                imageDetails,
                xmlDetails
        );
    }

    private Map<String, Integer> resolvePageCountByProjectId(Collection<String> projectIds) {
        Map<String, Integer> result = new HashMap<>();
        if (projectIds == null || projectIds.isEmpty()) {
            return result;
        }

        for (Object[] row : pageRepository.countByProjectIds(projectIds)) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            result.put((String) row[0], ((Number) row[1]).intValue());
        }

        return result;
    }

    private String normalizeForSort(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
