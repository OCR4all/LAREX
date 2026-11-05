package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import de.uniwue.zpd.dachs.larex.backend.dto.DatasetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps one linked dataset per set of source projects, so training and evaluation can start from
 * projects while runs, splits, and baselines keep working on datasets.
 */
@Service
@Transactional
public class ManagedDatasetService {

    private static final int MAX_LABEL_LENGTH = 255;

    private final DatasetRepository datasetRepository;
    private final DatasetItemRepository datasetItemRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final DatasetSplitService datasetSplitService;
    private final DatasetService datasetService;
    private final WorkspaceAccessService workspaceAccessService;

    public ManagedDatasetService(DatasetRepository datasetRepository,
                                 DatasetItemRepository datasetItemRepository,
                                 ProjectRepository projectRepository,
                                 PageRepository pageRepository,
                                 PageImageRepository pageImageRepository,
                                 PageXmlRepository pageXmlRepository,
                                 DatasetSplitService datasetSplitService,
                                 DatasetService datasetService,
                                 WorkspaceAccessService workspaceAccessService) {
        this.datasetRepository = datasetRepository;
        this.datasetItemRepository = datasetItemRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.datasetSplitService = datasetSplitService;
        this.datasetService = datasetService;
        this.workspaceAccessService = workspaceAccessService;
    }

    public DatasetDto.ProjectDatasetResponse syncProjectDataset(String workspaceId, List<String> projectIds, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        List<String> sortedProjectIds = projectIds == null ? List.of() : projectIds.stream()
                .filter(Objects::nonNull).distinct().sorted().toList();
        if (sortedProjectIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one project");
        }

        // Locking in a fixed order serializes concurrent syncs of overlapping project sets.
        List<Project> projects = sortedProjectIds.stream()
                .map(projectId -> projectRepository.findByIdAndLibraryWorkspaceIdForUpdate(projectId, workspaceId)
                        .orElseThrow(() -> new IllegalArgumentException("Project not found in this workspace: " + projectId)))
                .toList();

        String managedKey = managedKey(sortedProjectIds);
        Dataset dataset = datasetRepository.findByWorkspaceIdAndManagedKey(workspaceId, managedKey)
                .orElseGet(() -> newManagedDataset(workspaceId, managedKey));
        dataset.setManagedLabel(label(projects));
        dataset = datasetRepository.save(dataset);

        List<Page> pages = projects.stream()
                .flatMap(project -> pageRepository.findWithProjectByProjectId(project.getId()).stream())
                .toList();
        List<String> pageIds = pages.stream().map(Page::getId).toList();
        Map<String, PageXml> xmlByPage = pageXmlRepository.findByPage_IdIn(pageIds).stream()
                .filter(xml -> xml.getSchema() == XmlSchema.PAGE_XML)
                .collect(Collectors.toMap(xml -> xml.getPage().getId(), Function.identity(), (first, second) -> first));
        Map<String, List<PageImage>> imagesByPage = pageImageRepository.findByPageIdIn(pageIds).stream()
                .collect(Collectors.groupingBy(image -> image.getPage().getId()));

        List<DatasetItem> existingItems = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(dataset.getId());
        Map<String, DatasetItem> itemsByPage = existingItems.stream()
                .collect(Collectors.toMap(DatasetItem::getSourcePageId, Function.identity()));

        Set<String> linkedPageIds = new HashSet<>();
        List<DatasetItem> items = new ArrayList<>();
        for (Page page : pages) {
            PageXml xml = xmlByPage.get(page.getId());
            if (xml == null) continue;
            DatasetItem item = itemsByPage.get(page.getId());
            if (item == null) item = newItem(dataset, page);
            syncItem(item, page, xml, imagesByPage.getOrDefault(page.getId(), List.of()));
            linkedPageIds.add(page.getId());
            items.add(item);
        }

        datasetItemRepository.deleteAll(existingItems.stream()
                .filter(item -> !linkedPageIds.contains(item.getSourcePageId()))
                .toList());
        datasetItemRepository.saveAll(items);
        datasetItemRepository.flush();

        return new DatasetDto.ProjectDatasetResponse(
                datasetService.getDatasetSummary(workspaceId, dataset.getId(), userId),
                pages.size() - items.size());
    }

    /** Pages keep their splits, and the new dataset stops following the projects from here on. */
    public DatasetDto.DetailResponse saveAsDataset(String workspaceId,
                                                   String datasetId,
                                                   DatasetDto.SaveProjectDatasetRequest request,
                                                   String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        Dataset source = datasetRepository.findByIdAndWorkspaceId(datasetId, workspaceId)
                .filter(Dataset::isManaged)
                .orElseThrow(() -> new IllegalArgumentException("Project pages not found"));

        Set<String> itemIds = new HashSet<>(request.itemIds());
        List<DatasetItem> sourceItems = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(source.getId()).stream()
                .filter(item -> itemIds.contains(item.getId()))
                .toList();
        if (sourceItems.size() != itemIds.size()) {
            throw new IllegalArgumentException("Some of these pages have changed. Reload and try again.");
        }

        DatasetDto.DetailResponse created = datasetService.createDataset(workspaceId, new DatasetDto.CreateOrUpdateRequest(
                request.name(), null, List.of(), source.getSplitTemplate(), source.getSplitAlgorithm(), source.getSplitSeed(),
                source.getTrainPercentage(), source.getValPercentage(), source.getTestPercentage(), List.of()), userId);
        Dataset target = datasetRepository.getReferenceById(created.id());
        datasetItemRepository.saveAll(sourceItems.stream().map(item -> copyItem(target, item)).toList());
        datasetItemRepository.flush();
        return datasetService.getDataset(workspaceId, created.id(), userId);
    }

    private DatasetItem copyItem(Dataset dataset, DatasetItem source) {
        DatasetItem item = new DatasetItem();
        item.setDataset(dataset);
        item.setSourceProjectId(source.getSourceProjectId());
        item.setSourceProjectName(source.getSourceProjectName());
        item.setSourcePageId(source.getSourcePageId());
        item.setSourcePageName(source.getSourcePageName());
        item.setSourcePageTags(new ArrayList<>(source.getSourcePageTags()));
        item.setMode(DatasetItem.Mode.LINK);
        item.setSelectedSourceXmlId(source.getSelectedSourceXmlId());
        item.setSelectedSourceXmlFileName(source.getSelectedSourceXmlFileName());
        item.setSelectedSourceXmlUpdatedAt(source.getSelectedSourceXmlUpdatedAt());
        item.setSelectedSourceImageIds(new ArrayList<>(source.getSelectedSourceImageIds()));
        item.setAssignedSplit(source.getAssignedSplit());
        item.setSourcePageUpdatedAtSnapshot(source.getSourcePageUpdatedAtSnapshot());
        return item;
    }

    private Dataset newManagedDataset(String workspaceId, String managedKey) {
        Dataset dataset = new Dataset();
        dataset.setWorkspaceId(workspaceId);
        dataset.setManagedKey(managedKey);
        dataset.setName("managed-" + managedKey);
        dataset.setSplitTemplate(Dataset.SplitTemplate.TRAIN_VAL_TEST);
        dataset.setSplitAlgorithm(Dataset.SplitAlgorithm.RANDOM_SEEDED);
        dataset.setTrainPercentage(DatasetSplitService.MANAGED_TRAIN_PERCENTAGE);
        dataset.setValPercentage(DatasetSplitService.MANAGED_VAL_PERCENTAGE);
        dataset.setTestPercentage(DatasetSplitService.MANAGED_TEST_PERCENTAGE);
        return dataset;
    }

    private DatasetItem newItem(Dataset dataset, Page page) {
        DatasetItem item = new DatasetItem();
        item.setDataset(dataset);
        item.setSourcePageId(page.getId());
        item.setMode(DatasetItem.Mode.LINK);
        item.setAssignedSplit(datasetSplitService.stableSplit(page.getId()));
        return item;
    }

    private void syncItem(DatasetItem item, Page page, PageXml xml, List<PageImage> images) {
        item.setSourceProjectId(page.getProject().getId());
        item.setSourceProjectName(page.getProject().getName());
        item.setSourcePageName(page.getName());
        item.setSelectedSourceXmlId(xml.getId());
        item.setSelectedSourceXmlFileName(xml.getFileName());
        item.setSelectedSourceXmlUpdatedAt(xml.getUpdated());
        item.setSourcePageUpdatedAtSnapshot(page.getUpdated());
        item.setStatus(DatasetItem.Status.READY);
        item.setBrokenReason(null);

        List<String> tags = page.getTags() == null ? List.of() : List.copyOf(page.getTags());
        if (!tags.equals(item.getSourcePageTags())) item.setSourcePageTags(new ArrayList<>(tags));

        List<String> imageIds = images.stream().map(PageImage::getId).sorted().toList();
        if (!imageIds.equals(item.getSelectedSourceImageIds())) item.setSelectedSourceImageIds(new ArrayList<>(imageIds));
    }

    private String label(List<Project> projects) {
        String first = projects.getFirst().getName();
        String label = switch (projects.size()) {
            case 1 -> first;
            case 2 -> first + " + " + projects.get(1).getName();
            default -> first + " + " + (projects.size() - 1) + " more";
        };
        return label.length() <= MAX_LABEL_LENGTH ? label : label.substring(0, MAX_LABEL_LENGTH);
    }

    private String managedKey(List<String> sortedProjectIds) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", sortedProjectIds).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
