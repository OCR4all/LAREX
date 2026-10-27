package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.service.image.ImageTypeService;
import de.uniwue.zpd.dachs.larex.backend.entity.ImageType;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.InputLevel;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionInputSnapshot;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionInputSnapshotRepository;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionTrainingInput;
import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItemCopyFile;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionTrainingInputRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
public class ActionTrainingSnapshotService {

    private final ImageTypeService imageTypes;
    private final ActionInputSnapshotRepository snapshotRepository;
    private final DatasetRepository datasetRepository;
    private final DatasetItemRepository datasetItemRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final ActionTrainingInputRepository trainingInputRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final UploadPathService uploadPathService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final WorkspaceQuotaRefreshService workspaceQuotaRefreshService;

    public ActionTrainingSnapshotService(DatasetRepository datasetRepository,
                                         DatasetItemRepository datasetItemRepository,
                                         PageRepository pageRepository,
                                         PageImageRepository pageImageRepository,
                                         PageXmlRepository pageXmlRepository,
                                         ActionTrainingInputRepository trainingInputRepository,
                                         WorkspaceAccessService workspaceAccessService,
                                         UploadPathService uploadPathService,
                                         WorkspaceQuotaGuardService workspaceQuotaGuardService,
                                         WorkspaceQuotaRefreshService workspaceQuotaRefreshService,
                                         ActionInputSnapshotRepository snapshotRepository, ImageTypeService imageTypes) {
        this.imageTypes = imageTypes;
        this.snapshotRepository = snapshotRepository;
        this.datasetRepository = datasetRepository;
        this.datasetItemRepository = datasetItemRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.trainingInputRepository = trainingInputRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.uploadPathService = uploadPathService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
    }

    @Transactional(readOnly = true)
    public ActionDto.TrainingInputResponse listInputs(String workspaceId, String datasetId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        Dataset dataset = requireDataset(workspaceId, datasetId);
        List<ActionDto.TrainingInputItem> items = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(datasetId)
                .stream()
                .map(item -> overviewItem(workspaceId, item))
                .toList();
        return new ActionDto.TrainingInputResponse(dataset.getId(), dataset.getName(), items);
    }

    public ActionDto.TrainingInputResponse listEvaluationInputs(String workspaceId, String datasetId, String userId) {
        return listInputs(workspaceId, datasetId, userId);
    }

    public SnapshotResult createEvaluationSnapshot(String workspaceId,
                                                    Dataset dataset,
                                                    ActionRun run,
                                                    ActionDto.StartEvaluationRunRequest request,
                                                    ActionDto.TrainingSplitRequirements requirements,
                                                    ActionDto.InputRequirement imageRequirement) throws IOException {
        return createSnapshot(workspaceId, dataset, run,
                new ActionDto.StartTrainingRunRequest(
                        request.processorDefinitionId(), request.selection(), request.imageSelection(),
                        request.parameters(), request.enqueueIfBusy()), requirements, imageRequirement);
    }

    public SnapshotResult createSnapshot(String workspaceId,
                                         Dataset dataset,
                                         ActionRun run,
                                         ActionDto.StartTrainingRunRequest request,
                                         ActionDto.TrainingSplitRequirements requirements,
                                         ActionDto.InputRequirement imageRequirement) throws IOException {
        List<DatasetItem> allItems = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(dataset.getId());
        List<DatasetItem> selectedItems = selectItems(allItems, request.selection(), requirements);
        if (selectedItems.isEmpty()) {
            throw new IllegalArgumentException("No compatible dataset items selected for this Action");
        }

        List<ResolvedMaterial> materials = new ArrayList<>();
        for (DatasetItem item : selectedItems) {
            if (item.getStatus() != DatasetItem.Status.READY) {
                throw new IllegalArgumentException("Dataset item is not ready: " + item.getSourcePageName());
            }
            materials.addAll(resolveMaterials(workspaceId, item, request.imageSelection(), imageRequirement));
        }
        requireSplits(materials, requirements);

        long totalBytes = materials.stream().mapToLong(material -> material.image().size() + material.xml().size()).sum();
        long reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(workspaceId, totalBytes, "action-training-snapshot");
        Path runRoot = snapshotRoot(run);
        try {
            Files.createDirectories(runRoot);
            List<ActionTrainingInput> saved = new ArrayList<>();
            for (ResolvedMaterial material : materials) {
                Path itemRoot = runRoot.resolve(safeSegment(material.item().getId())).resolve(safeSegment(material.image().id()));
                Files.createDirectories(itemRoot);
                Path imageTarget = itemRoot.resolve("image-" + safeFileName(material.image().fileName()));
                Path xmlTarget = itemRoot.resolve("annotation-" + safeFileName(material.xml().fileName()));
                Files.copy(material.image().path(), imageTarget, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(material.xml().path(), xmlTarget, StandardCopyOption.REPLACE_EXISTING);

                ActionTrainingInput input = new ActionTrainingInput();
                input.setRun(run);
                input.setDatasetItemId(material.item().getId());
                input.setSourcePageId(material.item().getSourcePageId());
                input.setPageName(material.item().getSourcePageName());
                input.setSplit(material.item().getAssignedSplit());
                input.setImageFileId(material.image().id());
                input.setImageFileName(material.image().fileName());
                input.setImageVariant(material.image().variant());
                input.setImageType(material.image().imageType());
                input.setImageMimeType(material.image().mimeType());
                input.setImageFileSize(Files.size(imageTarget));
                input.setImageChecksumSha256(sha256(imageTarget));
                input.setImageSnapshotPath(relativePath(imageTarget));
                input.setXmlFileId(material.xml().id());
                input.setXmlFileName(material.xml().fileName());
                input.setXmlMimeType(material.xml().mimeType());
                input.setXmlFileSize(Files.size(xmlTarget));
                input.setXmlChecksumSha256(sha256(xmlTarget));
                input.setXmlSnapshotPath(relativePath(xmlTarget));
                saved.add(input);
            }
            trainingInputRepository.saveAll(saved);
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
            String fingerprint = fingerprint(dataset.getId(), saved);
            run.setDatasetInputFingerprint(fingerprint);
            return new SnapshotResult((int) saved.stream().map(ActionTrainingInput::getDatasetItemId).distinct().count(), splitCounts(saved));
        } catch (IOException | RuntimeException error) {
            deleteTree(runRoot);
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
            throw error;
        }
    }

    @Transactional(readOnly = true)
    public List<ActionTrainingInput> inputsForRun(String runId) {
        return trainingInputRepository.findByRunIdOrderByCreatedAsc(runId);
    }

    public void cleanupSnapshot(ActionRun run) {
        if ((!isDatasetAction(run) && run.getKind() != ActionRun.Kind.INTEGRATION) || run.getSnapshotCleanedAt() != null) {
            return;
        }
        deleteTree(snapshotRoot(run));
        run.setSnapshotCleanedAt(LocalDateTime.now());
        workspaceQuotaRefreshService.scheduleUsageRefresh(run.getWorkspaceId());
    }

    @Transactional(readOnly = true)
    public List<ActionInputSnapshot> integrationInputs(String runId) {
        return snapshotRepository.findByRunIdOrderByPositionAsc(runId);
    }

    @Transactional(readOnly = true)
    public ActionInputSnapshot integrationFile(String runId, String type, String fileId) {
        return snapshotRepository.findById(fileId)
                .filter(file -> runId.equals(file.getRun().getId()) && type.equals(file.getFileType()))
                .orElseThrow(() -> new IllegalArgumentException("Snapshot file is outside this run scope"));
    }

    public void createProjectIntegrationSnapshot(ActionRun run, List<ActionDto.MachinePageInput> pages) throws IOException {
        List<IntegrationMaterial> materials = new ArrayList<>();
        for (ActionDto.MachinePageInput page : pages) {
            List<ResolvedFile> images = page.images().stream().map(file -> resolved(pageImageRepository.findById(file.id())
                    .orElseThrow(() -> new IllegalArgumentException("Image is no longer available")))).toList();
            ResolvedFile xml = page.xml().isEmpty() ? null : resolved(pageXmlRepository.findById(page.xml().getFirst().id())
                    .orElseThrow(() -> new IllegalArgumentException("XML is no longer available")));
            materials.add(new IntegrationMaterial(page.id(), page.name(), page.sourcePageId(), null, images, xml));
        }
        freezeIntegration(run, materials);
    }

    public void createDatasetIntegrationSnapshot(ActionRun run, Dataset dataset,
                                                  ActionDto.StartTrainingRunRequest request,
                                                  ActionDto.InputRequirements requirements) throws IOException {
        ActionDto.TrainingSplitRequirements splits = new ActionDto.TrainingSplitRequirements(
                InputLevel.OPTIONAL, InputLevel.OPTIONAL, InputLevel.OPTIONAL);
        List<DatasetItem> items = selectItems(datasetItemRepository.findByDatasetIdOrderByCreatedAsc(dataset.getId()),
                request.selection(), splits);
        List<IntegrationMaterial> materials = new ArrayList<>();
        boolean explicit = request.selection() != null && "SELECTED".equalsIgnoreCase(request.selection().mode());
        for (DatasetItem item : items) {
            if (item.getStatus() != DatasetItem.Status.READY) {
                throw new IllegalArgumentException("Dataset item is not ready: " + item.getSourcePageName());
            }
            List<ResolvedFile> images = new ArrayList<>();
            ResolvedFile xml = null;
            if (item.getMode() == DatasetItem.Mode.COPY) {
                for (DatasetItemCopyFile file : item.getCopyFiles()) {
                    if (file.getKind() == DatasetItemCopyFile.Kind.IMAGE && item.getSelectedSourceImageIds().contains(file.getSourceFileId())) {
                        images.add(resolved(file));
                    } else if (file.getKind() == DatasetItemCopyFile.Kind.XML && Objects.equals(item.getSelectedSourceXmlId(), file.getSourceFileId())) {
                        xml = resolved(file);
                    }
                }
            } else {
                Page page = pageRepository.findById(item.getSourcePageId()).orElseThrow(() -> new IllegalArgumentException("Source page is missing"));
                if (page.getProject() == null || page.getProject().getLibrary() == null
                        || !run.getWorkspaceId().equals(page.getProject().getLibrary().getWorkspaceId())) {
                    throw new SecurityException("Source page is outside the dataset workspace");
                }
                images.addAll(pageImageRepository.findByPageId(page.getId()).stream()
                        .filter(file -> item.getSelectedSourceImageIds().contains(file.getId())).map(this::resolved).toList());
                if (item.getSelectedSourceXmlId() != null) {
                    xml = pageXmlRepository.findById(item.getSelectedSourceXmlId())
                            .filter(file -> file.getPage() != null && page.getId().equals(file.getPage().getId()))
                            .map(this::resolved).orElse(null);
                }
            }
            images = images.stream().filter(file -> Files.isRegularFile(file.path())).toList();
            if (xml != null && !Files.isRegularFile(xml.path())) xml = null;
            if (!requirements.images().accepts(de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget.PAGE)) {
                images = List.of();
            } else if (!requirements.images().imageTypes().isEmpty()
                    && images.stream().noneMatch(image -> requirements.images().acceptsType(image.imageType()))
                    && !hasImageSelection(item, request.imageSelection())) {
                images = List.of();
            } else if (!images.isEmpty() || hasImageOverride(item, request.imageSelection())) {
                images = selectImages(item, images, request.imageSelection(), requirements.images());
            }
            if (!requirements.xml().accepts(de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget.PAGE)) xml = null;
            boolean compatible = !(requirements.images().required(de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget.PAGE) && images.isEmpty())
                    && !(requirements.xml().required(de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget.PAGE) && xml == null)
                    && (!images.isEmpty() || xml != null);
            if (!compatible) {
                if (explicit) throw new IllegalArgumentException("Dataset item does not satisfy required inputs: " + item.getSourcePageName());
                continue;
            }
            materials.add(new IntegrationMaterial(item.getId(), item.getSourcePageName(), item.getSourcePageId(), item.getAssignedSplit(), images, xml));
        }
        freezeIntegration(run, materials);
    }

    private void freezeIntegration(ActionRun run, List<IntegrationMaterial> materials) throws IOException {
        if (materials.isEmpty()) throw new IllegalArgumentException("No inputs satisfy the Action requirements");
        long bytes = 0;
        for (IntegrationMaterial material : materials) {
            if (material.images().isEmpty() && material.xml() == null) throw new IllegalArgumentException("Each input needs at least one file");
            for (ResolvedFile file : material.images()) bytes += Files.size(file.path());
            if (material.xml() != null) bytes += Files.size(material.xml().path());
        }
        String workspaceId = run.getWorkspaceId();
        long reserved = workspaceQuotaGuardService.reserveBytesOrThrow(workspaceId, bytes, "action-integration-snapshot");
        Path root = snapshotRoot(run);
        try {
            Files.createDirectories(root);
            List<ActionInputSnapshot> rows = new ArrayList<>();
            for (IntegrationMaterial material : materials) {
                for (ResolvedFile image : material.images()) rows.add(freezeFile(run, root, material, image, "images", rows.size()));
                if (material.xml() != null) rows.add(freezeFile(run, root, material, material.xml(), "xml", rows.size()));
            }
            snapshotRepository.saveAll(rows);
            run.setInputCount(materials.size());
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reserved);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            deleteTree(root);
                            workspaceQuotaRefreshService.scheduleUsageRefresh(workspaceId);
                        }
                    }
                });
            }
        } catch (IOException | RuntimeException error) {
            deleteTree(root);
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reserved);
            throw error;
        }
    }

    private ActionInputSnapshot freezeFile(ActionRun run, Path root, IntegrationMaterial material,
                                           ResolvedFile file, String type, int position) throws IOException {
        Path target = root.resolve(position + "-" + safeFileName(file.fileName()));
        Files.copy(file.path(), target, StandardCopyOption.REPLACE_EXISTING);
        ActionInputSnapshot row = new ActionInputSnapshot();
        row.setRun(run);
        row.setInputId(material.id());
        row.setPageName(material.name());
        row.setSourcePageId(material.sourcePageId());
        row.setSplit(material.split() == null ? null : material.split().name());
        row.setFileType(type);
        row.setSourceFileId(file.id());
        row.setFileName(file.fileName());
        row.setVariant(file.variant());
        row.setImageType(file.imageType());
        row.setMimeType(Objects.toString(file.mimeType(), "application/octet-stream"));
        row.setFileSize(Files.size(target));
        row.setChecksumSha256(sha256(target));
        row.setSnapshotPath(relativePath(target));
        row.setPosition(position);
        return row;
    }

    private record IntegrationMaterial(String id, String name, String sourcePageId, DatasetItem.Split split,
                                       List<ResolvedFile> images, ResolvedFile xml) {}

    private List<DatasetItem> selectItems(List<DatasetItem> allItems,
                                          ActionDto.TrainingSelection selection,
                                          ActionDto.TrainingSplitRequirements requirements) {
        String mode = selection == null || selection.mode() == null ? "ALL" : selection.mode().trim().toUpperCase();
        if ("ALL".equals(mode)) {
            return allItems.stream()
                    .filter(item -> splitLevel(item.getAssignedSplit(), requirements) != InputLevel.NONE)
                    .filter(item -> item.getStatus() == DatasetItem.Status.READY)
                    .toList();
        }
        if (!"SELECTED".equals(mode)) {
            throw new IllegalArgumentException("Training selection mode must be ALL or SELECTED");
        }
        List<String> requestedIds = selection.itemIds() == null ? List.of() : selection.itemIds();
        Set<String> selectedIds = new LinkedHashSet<>(requestedIds);
        if (selectedIds.isEmpty()) {
            throw new IllegalArgumentException("At least one dataset item must be selected");
        }
        if (selectedIds.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Selected dataset item IDs must not contain duplicates");
        }
        Map<String, DatasetItem> byId = new LinkedHashMap<>();
        allItems.forEach(item -> byId.put(item.getId(), item));
        List<DatasetItem> result = new ArrayList<>();
        for (String id : selectedIds) {
            DatasetItem item = byId.get(id);
            if (item == null) {
                throw new IllegalArgumentException("Dataset item not found: " + id);
            }
            if (splitLevel(item.getAssignedSplit(), requirements) == InputLevel.NONE) {
                throw new IllegalArgumentException("Dataset split is unsupported by this training Action: " + item.getAssignedSplit());
            }
            result.add(item);
        }
        return result;
    }

    private void requireSplits(List<ResolvedMaterial> materials, ActionDto.TrainingSplitRequirements requirements) {
        Map<DatasetItem.Split, Long> counts = new EnumMap<>(DatasetItem.Split.class);
        materials.forEach(material -> counts.merge(material.item().getAssignedSplit(), 1L, Long::sum));
        for (DatasetItem.Split split : DatasetItem.Split.values()) {
            if (splitLevel(split, requirements) == InputLevel.REQUIRED && counts.getOrDefault(split, 0L) == 0) {
                throw new IllegalArgumentException("At least one " + split + " dataset item is required");
            }
        }
    }

    private InputLevel splitLevel(DatasetItem.Split split, ActionDto.TrainingSplitRequirements requirements) {
        if (split == null) return requirements.train() == InputLevel.OPTIONAL
                && requirements.val() == InputLevel.OPTIONAL && requirements.test() == InputLevel.OPTIONAL
                ? InputLevel.OPTIONAL : InputLevel.NONE;
        return switch (split) {
            case TRAIN -> requirements.train();
            case VAL -> requirements.val();
            case TEST -> requirements.test();
        };
    }

    private List<ResolvedMaterial> resolveMaterials(String workspaceId,
                                                    DatasetItem item,
                                                    ActionDto.TrainingImageSelection selection,
                                                    ActionDto.InputRequirement imageRequirement) {
        List<ResolvedFile> images;
        ResolvedFile xml;
        if (item.getMode() == DatasetItem.Mode.COPY) {
            List<DatasetItemCopyFile> files = item.getCopyFiles() == null ? List.of() : item.getCopyFiles();
            DatasetItemCopyFile copyXml = files.stream()
                    .filter(file -> file.getKind() == DatasetItemCopyFile.Kind.XML)
                    .filter(file -> Objects.equals(file.getSourceFileId(), item.getSelectedSourceXmlId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Frozen XML is missing for " + item.getSourcePageName()));
            xml = resolved(copyXml);
            images = files.stream()
                    .filter(file -> file.getKind() == DatasetItemCopyFile.Kind.IMAGE)
                    .filter(file -> item.getSelectedSourceImageIds().contains(file.getSourceFileId()))
                    .map(this::resolved)
                    .toList();
        } else {
            Page page = pageRepository.findById(item.getSourcePageId())
                    .orElseThrow(() -> new IllegalArgumentException("Source page is missing: " + item.getSourcePageName()));
            if (page.getProject() == null || page.getProject().getLibrary() == null
                    || !workspaceId.equals(page.getProject().getLibrary().getWorkspaceId())) {
                throw new IllegalArgumentException("Source page is outside the dataset workspace");
            }
            PageXml sourceXml = pageXmlRepository.findById(item.getSelectedSourceXmlId())
                    .filter(value -> value.getPage() != null && page.getId().equals(value.getPage().getId()))
                    .orElseThrow(() -> new IllegalArgumentException("Selected XML is missing for " + item.getSourcePageName()));
            xml = resolved(sourceXml);
            Map<String, PageImage> imageById = new LinkedHashMap<>();
            pageImageRepository.findByPageId(page.getId()).forEach(image -> imageById.put(image.getId(), image));
            images = item.getSelectedSourceImageIds().stream()
                    .map(imageById::get)
                    .filter(Objects::nonNull)
                    .map(this::resolved)
                    .toList();
        }
        if (!Files.isRegularFile(xml.path())) {
            throw new IllegalArgumentException("Selected XML file is missing for " + item.getSourcePageName());
        }
        List<ResolvedFile> selected = selectImages(item, images, selection, imageRequirement);
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No compatible image for " + item.getSourcePageName()
                    + "; accepted types: " + imageRequirement.imageTypes());
        }
        for (ResolvedFile image : selected) {
            if (!Files.isRegularFile(image.path())) {
                throw new IllegalArgumentException("Selected image file is missing for " + item.getSourcePageName());
            }
        }
        ResolvedFile selectedXml = xml;
        return selected.stream().map(image -> new ResolvedMaterial(item, image, selectedXml)).toList();
    }

    private boolean hasImageSelection(DatasetItem item, ActionDto.TrainingImageSelection selection) {
        return hasImageOverride(item, selection) || selection != null
                && ((selection.globalVariants() != null && !selection.globalVariants().isEmpty())
                || (selection.globalVariant() != null && !selection.globalVariant().isBlank()));
    }

    private boolean hasImageOverride(DatasetItem item, ActionDto.TrainingImageSelection selection) {
        return selection != null && (selection.itemImageIds() != null && selection.itemImageIds().containsKey(item.getId())
                || selection.itemImageIdLists() != null && selection.itemImageIdLists().containsKey(item.getId()));
    }

    private List<ResolvedFile> selectImages(DatasetItem item,
                                          List<ResolvedFile> images,
                                          ActionDto.TrainingImageSelection selection,
                                          ActionDto.InputRequirement imageRequirement) {
        if (images.isEmpty()) {
            throw new IllegalArgumentException("No selected image is available for " + item.getSourcePageName());
        }
        List<String> overrides = selection == null || selection.itemImageIdLists() == null
                ? null : selection.itemImageIdLists().get(item.getId());
        if (overrides == null && selection != null && selection.itemImageIds() != null) {
            String legacy = selection.itemImageIds().get(item.getId());
            if (legacy != null && !legacy.isBlank()) overrides = List.of(legacy);
        }
        if (overrides != null && !overrides.isEmpty()) {
            imageRequirement.validateImageCount(overrides.stream().distinct().count());
            List<String> wanted = overrides;
            List<ResolvedFile> selected = images.stream().filter(image -> wanted.contains(image.id())).toList();
            if (selected.stream().map(ResolvedFile::id).distinct().count() != wanted.stream().distinct().count()) {
                throw new IllegalArgumentException("Image override is not part of dataset item " + item.getSourcePageName());
            }
            if (selected.stream().anyMatch(image -> !imageRequirement.acceptsType(image.imageType()))) {
                throw new IllegalArgumentException("Selected image type is incompatible for " + item.getSourcePageName()
                        + "; accepted types: " + imageRequirement.imageTypes());
            }
            return selected;
        }
        images = images.stream().filter(image -> imageRequirement.acceptsType(image.imageType()))
                .sorted(Comparator.comparing((ResolvedFile image) -> Objects.toString(image.variant(), ""), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ResolvedFile::fileName, String.CASE_INSENSITIVE_ORDER).thenComparing(ResolvedFile::id)).toList();
        List<String> variants = selection == null ? null : selection.globalVariants();
        if (variants == null && selection != null && selection.globalVariant() != null && !selection.globalVariant().isBlank()) {
            variants = List.of(selection.globalVariant().trim());
        }
        if (variants != null && !variants.isEmpty()) {
            imageRequirement.validateImageCount(variants.stream().distinct().count());
            List<String> wanted = variants;
            List<ResolvedFile> selected = images.stream().filter(image -> wanted.contains(image.variant())).toList();
            if (selected.isEmpty()) {
                throw new IllegalArgumentException("Selected image variants are missing for " + item.getSourcePageName());
            }
            imageRequirement.validateImageCount(selected.size());
            return selected;
        }
        if (!imageRequirement.imageTypes().isEmpty()) {
            if (images.isEmpty() && imageRequirement.required(de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget.PAGE)) {
                throw new IllegalArgumentException("No compatible image for " + item.getSourcePageName() + "; accepted types: " + imageRequirement.imageTypes());
            }
            return imageRequirement.multiple() ? images : images.stream().limit(1).toList();
        }
        if (images.size() == 1) return images;
        throw new IllegalArgumentException("Choose image variants for " + item.getSourcePageName());
    }

    private ActionDto.TrainingInputItem overviewItem(String workspaceId, DatasetItem item) {
        try {
            ResolvedOverview overview = resolveOverview(workspaceId, item);
            return new ActionDto.TrainingInputItem(
                    item.getId(), item.getSourcePageId(), item.getSourcePageName(), item.getAssignedSplit(),
                    item.getStatus(), item.getBrokenReason(), overview.xmlAvailable(), overview.images());
        } catch (RuntimeException error) {
            return new ActionDto.TrainingInputItem(
                    item.getId(), item.getSourcePageId(), item.getSourcePageName(), item.getAssignedSplit(),
                    DatasetItem.Status.BROKEN, error.getMessage(), false, List.of());
        }
    }

    private ResolvedOverview resolveOverview(String workspaceId, DatasetItem item) {
        if (item.getMode() == DatasetItem.Mode.COPY) {
            List<DatasetItemCopyFile> files = item.getCopyFiles() == null ? List.of() : item.getCopyFiles();
            boolean xmlAvailable = files.stream().anyMatch(file -> file.getKind() == DatasetItemCopyFile.Kind.XML
                    && Objects.equals(file.getSourceFileId(), item.getSelectedSourceXmlId())
                    && Files.isRegularFile(uploadPathService.resolve(file.getFilePath())));
            List<ActionDto.TrainingImageChoice> images = files.stream()
                    .filter(file -> file.getKind() == DatasetItemCopyFile.Kind.IMAGE)
                    .filter(file -> item.getSelectedSourceImageIds().contains(file.getSourceFileId()))
                    .filter(file -> Files.isRegularFile(uploadPathService.resolve(file.getFilePath())))
                    .map(file -> new ActionDto.TrainingImageChoice(file.getSourceFileId(), file.getFileName(), file.getVariant(), file.getImageType()))
                    .toList();
            return new ResolvedOverview(xmlAvailable, images);
        }
        Page page = pageRepository.findById(item.getSourcePageId()).orElseThrow();
        if (page.getProject() == null || page.getProject().getLibrary() == null
                || !workspaceId.equals(page.getProject().getLibrary().getWorkspaceId())) {
            throw new IllegalArgumentException("Source page is unavailable");
        }
        boolean xmlAvailable = item.getSelectedSourceXmlId() != null && pageXmlRepository.findById(item.getSelectedSourceXmlId())
                .filter(xml -> Files.isRegularFile(uploadPathService.resolve(xml.getFilePath())))
                .isPresent();
        Set<String> selectedImageIds = new LinkedHashSet<>(item.getSelectedSourceImageIds());
        List<ActionDto.TrainingImageChoice> images = pageImageRepository.findByPageId(page.getId()).stream()
                .filter(image -> selectedImageIds.contains(image.getId()))
                .filter(image -> Files.isRegularFile(uploadPathService.resolve(image.getFilePath())))
                .map(image -> new ActionDto.TrainingImageChoice(image.getId(), image.getFileName(), image.getVariant(), image.getImageType()))
                .toList();
        return new ResolvedOverview(xmlAvailable, images);
    }

    private Dataset requireDataset(String workspaceId, String datasetId) {
        return datasetRepository.findByIdAndWorkspaceId(datasetId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));
    }

    private ResolvedFile resolved(DatasetItemCopyFile file) {
        return new ResolvedFile(file.getSourceFileId(), file.getFileName(), file.getVariant(), file.getMimeType(),
                file.getFileSize(), uploadPathService.resolve(file.getFilePath()), imageTypes.typeOf(file));
    }

    private ResolvedFile resolved(PageImage file) {
        return new ResolvedFile(file.getId(), file.getFileName(), file.getVariant(), file.getMimeType(),
                file.getFileSize() == null ? 0L : file.getFileSize(), uploadPathService.resolve(file.getFilePath()), imageTypes.typeOf(file));
    }

    private ResolvedFile resolved(PageXml file) {
        return new ResolvedFile(file.getId(), file.getFileName(), file.getVariant(), file.getMimeType(),
                file.getFileSize() == null ? 0L : file.getFileSize(), uploadPathService.resolve(file.getFilePath()), null);
    }

    private Path snapshotRoot(ActionRun run) {
        String scope = run.getKind() == ActionRun.Kind.INTEGRATION ? "integration"
                : run.getKind() == ActionRun.Kind.EVALUATION ? "evaluation" : "training";
        return uploadPathService.resolve("actions", scope, safeSegment(run.getId()));
    }

    private String relativePath(Path path) {
        return uploadPathService.root().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private String safeSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid snapshot path segment");
        }
        return value;
    }

    private String safeFileName(String value) {
        String name = Path.of(value == null ? "file" : value).getFileName().toString();
        return name.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private Map<String, Long> splitCounts(List<ActionTrainingInput> inputs) {
        Map<String, Long> result = new LinkedHashMap<>();
        Map<String, ActionTrainingInput> items = new LinkedHashMap<>();
        inputs.forEach(input -> items.putIfAbsent(input.getDatasetItemId(), input));
        items.values().forEach(input -> result.merge(input.getSplit().name(), 1L, Long::sum));
        return result;
    }

    private void deleteTree(Path root) {
        if (root == null || !root.normalize().startsWith(uploadPathService.root()) || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private boolean isDatasetAction(ActionRun run) {
        return run.getKind() == ActionRun.Kind.TRAINING || run.getKind() == ActionRun.Kind.EVALUATION;
    }

    private String fingerprint(String datasetId, List<ActionTrainingInput> inputs) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(datasetId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            inputs.stream()
                    .sorted(Comparator.comparing(ActionTrainingInput::getDatasetItemId))
                    .forEach(input -> {
                        String value = String.join("|", input.getDatasetItemId(), input.getSourcePageId(),
                                input.getSplit().name(), Objects.toString(input.getImageVariant(), ""),
                                input.getImageFileId(), input.getImageChecksumSha256(),
                                input.getXmlFileId(), input.getXmlChecksumSha256());
                        digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    });
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public record SnapshotResult(int inputCount, Map<String, Long> splitCounts) {}
    private record ResolvedMaterial(DatasetItem item, ResolvedFile image, ResolvedFile xml) {}
    private record ResolvedFile(String id, String fileName, String variant, String mimeType, long size, Path path, ImageType imageType) {}
    private record ResolvedOverview(boolean xmlAvailable, List<ActionDto.TrainingImageChoice> images) {}
}
