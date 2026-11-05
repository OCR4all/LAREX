package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.DatasetDto;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionTrainingInput;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionTrainingInputRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionTrainingSnapshotService;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.DatasetService;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.ManagedDatasetService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ManagedDatasetIntegrationTest {
    @Autowired ManagedDatasetService service;
    @Autowired DatasetService datasetService;
    @Autowired ActionTrainingSnapshotService snapshots;
    @Autowired DatasetItemRepository datasetItems;
    @Autowired LibraryRepository libraries;
    @Autowired ProjectRepository projects;
    @Autowired PageRepository pages;
    @Autowired PageImageRepository images;
    @Autowired PageXmlRepository xmls;
    @Autowired ActionProcessorDefinitionRepository definitions;
    @Autowired ActionRunRepository runs;
    @Autowired ActionTrainingInputRepository trainingInputs;
    @MockitoBean WorkspaceAccessService access;

    String workspace;
    Project project;

    @BeforeEach
    void setUp() {
        when(access.hasWorkspaceAccess(anyString(), anyString())).thenReturn(true);
        when(access.canManageProjects(anyString(), anyString())).thenReturn(true);
        workspace = "managed-" + UUID.randomUUID();
        Library library = libraries.save(new Library(workspace, "Library"));
        project = projects.save(new Project("Letters", null, library));
    }

    @Test
    void createsTrainingInputsAndReusesThemOnTheNextSync() {
        Page reviewed = createPage("reviewed", Page.WorkflowState.DONE);
        createPage("open", Page.WorkflowState.OPEN);
        pages.save(new Page("without-xml", null, project));

        DatasetDto.ProjectDatasetResponse first = service.syncProjectDataset(workspace, List.of(project.getId()), "user");

        assertThat(first.dataset().name()).isEqualTo("Letters");
        assertThat(first.pagesWithoutXml()).isEqualTo(1);
        List<DatasetItem> items = datasetItems.findByDatasetIdOrderByCreatedAsc(first.dataset().id());
        assertThat(items).extracting(DatasetItem::getSourcePageName).containsExactlyInAnyOrder("reviewed", "open");

        ActionDto.TrainingInputResponse inputs = snapshots.listInputs(workspace, first.dataset().id(), "user");
        Map<String, Page.WorkflowState> states = inputs.items().stream()
                .collect(Collectors.toMap(ActionDto.TrainingInputItem::pageName, ActionDto.TrainingInputItem::workflowState));
        assertThat(states).containsEntry("reviewed", Page.WorkflowState.DONE).containsEntry("open", Page.WorkflowState.OPEN);

        Map<String, String> itemIds = items.stream().collect(Collectors.toMap(DatasetItem::getSourcePageName, DatasetItem::getId));
        createPage("added", Page.WorkflowState.DONE);
        pages.delete(pages.findById(reviewed.getId()).orElseThrow());
        DatasetDto.ProjectDatasetResponse second = service.syncProjectDataset(workspace, List.of(project.getId()), "user");

        assertThat(second.dataset().id()).isEqualTo(first.dataset().id());
        List<DatasetItem> synced = datasetItems.findByDatasetIdOrderByCreatedAsc(second.dataset().id());
        assertThat(synced).extracting(DatasetItem::getSourcePageName).containsExactlyInAnyOrder("open", "added");
        assertThat(synced).filteredOn(item -> item.getSourcePageName().equals("open"))
                .extracting(DatasetItem::getId).containsExactly(itemIds.get("open"));
    }

    @Test
    void staysOutOfListingsAndLeavesItsNameFree() {
        service.syncProjectDataset(workspace, List.of(project.getId()), "user");

        assertThat(datasetService.listDatasets(workspace, "user")).isEmpty();

        datasetService.createDataset(workspace, new DatasetDto.CreateOrUpdateRequest(
                "Letters", null, List.of(), null, null, null, 70, 15, 15, List.of(), null), "user");
        assertThat(datasetService.listDatasets(workspace, "user")).extracting(DatasetDto.SummaryResponse::name).containsExactly("Letters");
    }

    @Test
    void cannotBeEditedLikeANormalDataset() {
        String datasetId = service.syncProjectDataset(workspace, List.of(project.getId()), "user").dataset().id();

        assertThatThrownBy(() -> datasetService.generateSplit(workspace, datasetId, null, "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("follows its source projects");
    }

    @Test
    void savesChosenPagesAsAnIndependentDatasetWithTheirSplits() {
        createPage("first", Page.WorkflowState.DONE);
        createPage("second", Page.WorkflowState.DONE);
        createPage("left-out", Page.WorkflowState.OPEN);
        String managedId = service.syncProjectDataset(workspace, List.of(project.getId()), "user").dataset().id();
        Map<String, DatasetItem> managedItems = datasetItems.findByDatasetIdOrderByCreatedAsc(managedId).stream()
                .collect(Collectors.toMap(DatasetItem::getSourcePageName, item -> item));

        DatasetDto.DetailResponse saved = service.saveAsDataset(workspace, managedId, new DatasetDto.SaveProjectDatasetRequest(
                "Reviewed letters", List.of(managedItems.get("first").getId(), managedItems.get("second").getId())), "user");

        assertThat(saved.name()).isEqualTo("Reviewed letters");
        assertThat(datasetService.listDatasets(workspace, "user")).extracting(DatasetDto.SummaryResponse::id).containsExactly(saved.id());
        List<DatasetItem> savedItems = datasetItems.findByDatasetIdOrderByCreatedAsc(saved.id());
        assertThat(savedItems).extracting(DatasetItem::getSourcePageName).containsExactlyInAnyOrder("first", "second");
        assertThat(savedItems).allSatisfy(item ->
                assertThat(item.getAssignedSplit()).isEqualTo(managedItems.get(item.getSourcePageName()).getAssignedSplit()));

        createPage("added-later", Page.WorkflowState.DONE);
        service.syncProjectDataset(workspace, List.of(project.getId()), "user");
        assertThat(datasetItems.findByDatasetIdOrderByCreatedAsc(saved.id())).hasSize(2);
        assertThat(saved.items()).hasSize(2).allSatisfy(item -> assertThat(item.selectedSourceImageIds()).hasSize(1));
    }

    @Test
    void onlySavesPagesOfManagedDatasets() {
        createPage("page", Page.WorkflowState.DONE);
        String managedId = service.syncProjectDataset(workspace, List.of(project.getId()), "user").dataset().id();
        String itemId = datasetItems.findByDatasetIdOrderByCreatedAsc(managedId).getFirst().getId();
        String savedId = service.saveAsDataset(workspace, managedId,
                new DatasetDto.SaveProjectDatasetRequest("Saved", List.of(itemId)), "user").id();

        assertThatThrownBy(() -> service.saveAsDataset(workspace, savedId,
                new DatasetDto.SaveProjectDatasetRequest("Again", List.of(itemId)), "user"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.saveAsDataset(workspace, managedId,
                new DatasetDto.SaveProjectDatasetRequest("Missing", List.of("unknown")), "user"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(datasetService.listDatasets(workspace, "user")).extracting(DatasetDto.SummaryResponse::name).containsExactly("Saved");
    }

    @Test
    void flagsPagesFromFinishedTrainingRunsOnly() {
        Page trained = createPage("trained", Page.WorkflowState.DONE);
        Page validated = createPage("validated", Page.WorkflowState.DONE);
        Page tested = createPage("tested", Page.WorkflowState.DONE);
        Page failed = createPage("failed", Page.WorkflowState.DONE);
        String datasetId = service.syncProjectDataset(workspace, List.of(project.getId()), "user").dataset().id();

        ActionRun finished = saveTrainingRun(datasetId, ActionRun.Status.COMPLETED_WITH_ISSUES);
        saveTrainingInput(finished, trained, DatasetItem.Split.TRAIN);
        saveTrainingInput(finished, validated, DatasetItem.Split.VAL);
        saveTrainingInput(finished, tested, DatasetItem.Split.TEST);
        saveTrainingInput(saveTrainingRun(datasetId, ActionRun.Status.FAILED), failed, DatasetItem.Split.TRAIN);

        Map<String, Boolean> overlap = snapshots.listEvaluationInputs(workspace, datasetId, "user").items().stream()
                .collect(Collectors.toMap(ActionDto.TrainingInputItem::pageName, ActionDto.TrainingInputItem::usedForTraining));
        assertThat(overlap).containsExactlyInAnyOrderEntriesOf(Map.of(
                "trained", true, "validated", true, "tested", false, "failed", false));
    }

    private ActionRun saveTrainingRun(String datasetId, ActionRun.Status status) {
        ActionProcessorDefinition definition = new ActionProcessorDefinition();
        definition.setProcessorKey("managed-" + UUID.randomUUID());
        definition.setName("Training");
        definition.setYamlSource("");
        definition.setParsedJson("{}");
        definition.setEndpointUrl("http://processor/dispatch");
        definition.setCreatedByUserId("admin");
        definition.setUpdatedByUserId("admin");
        ActionRun run = new ActionRun();
        run.setProcessorDefinition(definitions.saveAndFlush(definition));
        run.setKind(ActionRun.Kind.TRAINING);
        run.setWorkspaceId(workspace);
        run.setDatasetId(datasetId);
        run.setCreatedByUserId("user");
        run.setPageIdsJson("[]");
        run.setStatus(status);
        run.setSecretHash("0".repeat(64));
        run.setSecretPrefix("managed");
        run.setSecretExpiresAt(LocalDateTime.now().plusMinutes(10));
        return runs.saveAndFlush(run);
    }

    private void saveTrainingInput(ActionRun run, Page page, DatasetItem.Split split) {
        ActionTrainingInput input = new ActionTrainingInput();
        input.setRun(run);
        input.setDatasetItemId(UUID.randomUUID().toString());
        input.setSourcePageId(page.getId());
        input.setPageName(page.getName());
        input.setSplit(split);
        input.setXmlFileId("xml");
        input.setXmlFileName("page.xml");
        input.setXmlMimeType("application/xml");
        input.setXmlFileSize(1);
        input.setXmlChecksumSha256("0".repeat(64));
        input.setXmlSnapshotPath("page.xml");
        trainingInputs.saveAndFlush(input);
    }

    private Page createPage(String name, Page.WorkflowState state) {
        Page page = new Page(name, null, project);
        page.setWorkflowState(state);
        page = pages.save(page);
        PageImage image = new PageImage(name + ".png", name + ".png", "image/png", 1L, "original", name, page);
        images.save(image);
        xmls.save(new PageXml(name + ".xml", name + ".xml", "application/xml", 1L, "original", name, XmlSchema.PAGE_XML, null, page));
        return page;
    }
}
