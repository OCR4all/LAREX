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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedDatasetServiceTest {

    private final DatasetRepository datasets = mock(DatasetRepository.class);
    private final DatasetItemRepository items = mock(DatasetItemRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final PageRepository pages = mock(PageRepository.class);
    private final PageImageRepository images = mock(PageImageRepository.class);
    private final PageXmlRepository xml = mock(PageXmlRepository.class);
    private final DatasetService datasetService = mock(DatasetService.class);
    private final WorkspaceAccessService access = mock(WorkspaceAccessService.class);
    private final DatasetSplitService splits = new DatasetSplitService();
    private final ManagedDatasetService service = new ManagedDatasetService(
            datasets, items, projects, pages, images, xml, splits, datasetService, access);

    private final Project alpha = project("alpha", "Alpha");
    private final Project beta = project("beta", "Beta");

    @BeforeEach
    void setup() {
        when(datasets.findByWorkspaceIdAndManagedKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(datasets.save(any(Dataset.class))).thenAnswer(call -> {
            Dataset dataset = call.getArgument(0);
            dataset.setId("managed");
            return dataset;
        });
        when(items.findByDatasetIdOrderByCreatedAsc("managed")).thenReturn(List.of());
        when(pages.findWithProjectByProjectId(anyString())).thenReturn(List.of());
        when(xml.findByPage_IdIn(anyCollection())).thenReturn(List.of());
        when(images.findByPageIdIn(anyCollection())).thenReturn(List.of());
    }

    @Test
    void linksPagesThatHavePageXmlAndCountsTheRest() {
        Page annotated = page("annotated", alpha);
        Page empty = page("empty", alpha);
        Page altoOnly = page("alto-only", alpha);
        givenProjectPages(alpha, annotated, empty, altoOnly);
        when(xml.findByPage_IdIn(anyCollection())).thenReturn(List.of(xml(annotated, XmlSchema.PAGE_XML), xml(altoOnly, XmlSchema.ALTO_XML)));
        when(images.findByPageIdIn(anyCollection())).thenReturn(List.of(image("image-b", annotated), image("image-a", annotated)));

        DatasetDto.ProjectDatasetResponse response = service.syncProjectDataset("workspace", List.of("alpha"), "user");

        assertThat(response.pagesWithoutXml()).isEqualTo(2);
        List<DatasetItem> saved = savedItems();
        assertThat(saved).extracting(DatasetItem::getSourcePageId).containsExactly("annotated");
        DatasetItem item = saved.getFirst();
        assertThat(item.getMode()).isEqualTo(DatasetItem.Mode.LINK);
        assertThat(item.getSelectedSourceXmlId()).isEqualTo("xml-annotated");
        assertThat(item.getSelectedSourceImageIds()).containsExactly("image-a", "image-b");
        assertThat(item.getAssignedSplit()).isEqualTo(splits.stableSplit("annotated"));
    }

    @Test
    void createsHiddenDatasetWithReadableLabelAndInternalName() {
        givenProjectPages(alpha);
        givenProjectPages(beta);

        service.syncProjectDataset("workspace", List.of("beta", "alpha"), "user");

        ArgumentCaptor<Dataset> dataset = ArgumentCaptor.forClass(Dataset.class);
        verify(datasets).save(dataset.capture());
        assertThat(dataset.getValue().isManaged()).isTrue();
        assertThat(dataset.getValue().getDisplayName()).isEqualTo("Alpha + Beta");
        assertThat(dataset.getValue().getName()).startsWith("managed-").doesNotContain("Alpha");
    }

    @Test
    void sameProjectsReuseTheSameKeyInAnyOrder() {
        givenProjectPages(alpha);
        givenProjectPages(beta);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

        service.syncProjectDataset("workspace", List.of("alpha", "beta"), "user");
        service.syncProjectDataset("workspace", List.of("beta", "alpha", "beta"), "user");

        verify(datasets, org.mockito.Mockito.times(2)).findByWorkspaceIdAndManagedKey(eq("workspace"), key.capture());
        assertThat(key.getAllValues().get(0)).isEqualTo(key.getAllValues().get(1));
    }

    @Test
    void locksProjectsInSortedOrder() {
        givenProjectPages(alpha);
        givenProjectPages(beta);

        service.syncProjectDataset("workspace", List.of("beta", "alpha"), "user");

        var order = org.mockito.Mockito.inOrder(projects);
        order.verify(projects).findByIdAndLibraryWorkspaceIdForUpdate("alpha", "workspace");
        order.verify(projects).findByIdAndLibraryWorkspaceIdForUpdate("beta", "workspace");
    }

    @Test
    void keepsExistingItemsAndTheirSplitsWhileFollowingTheProject() {
        Page kept = page("kept", alpha);
        Page added = page("added", alpha);
        givenProjectPages(alpha, kept, added);
        when(xml.findByPage_IdIn(anyCollection())).thenReturn(List.of(xml(kept, XmlSchema.PAGE_XML), xml(added, XmlSchema.PAGE_XML)));
        DatasetItem existing = existingItem("kept", DatasetItem.Split.TEST);
        DatasetItem removed = existingItem("removed", DatasetItem.Split.TRAIN);
        when(items.findByDatasetIdOrderByCreatedAsc("managed")).thenReturn(List.of(existing, removed));

        service.syncProjectDataset("workspace", List.of("alpha"), "user");

        assertThat(savedItems()).extracting(DatasetItem::getSourcePageId).containsExactly("kept", "added");
        assertThat(existing.getAssignedSplit()).isEqualTo(DatasetItem.Split.TEST);
        assertThat(existing.getSelectedSourceXmlId()).isEqualTo("xml-kept");
        verify(items).deleteAll(List.of(removed));
    }

    @Test
    void rejectsProjectsOutsideTheWorkspace() {
        when(projects.findByIdAndLibraryWorkspaceIdForUpdate("foreign", "workspace")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncProjectDataset("workspace", List.of("foreign"), "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(datasets, never()).save(any());
    }

    @Test
    void rejectsAnEmptySelection() {
        assertThatThrownBy(() -> service.syncProjectDataset("workspace", List.of(), "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one project");
    }

    private void givenProjectPages(Project project, Page... projectPages) {
        when(projects.findByIdAndLibraryWorkspaceIdForUpdate(project.getId(), "workspace")).thenReturn(Optional.of(project));
        when(pages.findWithProjectByProjectId(project.getId())).thenReturn(List.of(projectPages));
    }

    @SuppressWarnings("unchecked")
    private List<DatasetItem> savedItems() {
        ArgumentCaptor<Iterable<DatasetItem>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(items).saveAll(saved.capture());
        List<DatasetItem> result = new ArrayList<>();
        saved.getValue().forEach(result::add);
        return result;
    }

    private static Project project(String id, String name) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        return project;
    }

    private static Page page(String id, Project project) {
        Page page = new Page();
        page.setId(id);
        page.setName(id);
        page.setProject(project);
        return page;
    }

    private static PageXml xml(Page page, XmlSchema schema) {
        PageXml xml = new PageXml();
        xml.setId("xml-" + page.getId());
        xml.setFileName(page.getId() + ".xml");
        xml.setSchema(schema);
        xml.setPage(page);
        return xml;
    }

    private static PageImage image(String id, Page page) {
        PageImage image = new PageImage();
        image.setId(id);
        image.setPage(page);
        return image;
    }

    private static DatasetItem existingItem(String pageId, DatasetItem.Split split) {
        DatasetItem item = new DatasetItem();
        item.setSourcePageId(pageId);
        item.setAssignedSplit(split);
        return item;
    }
}
