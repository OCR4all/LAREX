package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.InputLevel;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
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
                                         WorkspaceQuotaRefreshService workspaceQuotaRefreshService) {
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
                                                    ActionDto.TrainingSplitRequirements requirements) throws IOException {
        return createSnapshot(workspaceId, dataset, run,
                new ActionDto.StartTrainingRunRequest(
                        request.processorDefinitionId(), request.selection(), request.imageSelection(),
                        request.parameters(), request.enqueueIfBusy()), requirements);
    }

    public SnapshotResult createSnapshot(String workspaceId,
                                         Dataset dataset,
                                         ActionRun run,
                                         ActionDto.StartTrainingRunRequest request,
                                         ActionDto.TrainingSplitRequirements requirements) throws IOException {
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
            materials.add(resolveMaterial(workspaceId, item, request.imageSelection()));
        }
        requireSplits(materials, requirements);

        long totalBytes = materials.stream().mapToLong(material -> material.image().size() + material.xml().size()).sum();
        long reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(workspaceId, totalBytes, "action-training-snapshot");
        Path runRoot = snapshotRoot(run);
        try {
            Files.createDirectories(runRoot);
            List<ActionTrainingInput> saved = new ArrayList<>();
            for (ResolvedMaterial material : materials) {
                Path itemRoot = runRoot.resolve(safeSegment(material.item().getId()));
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
            return new SnapshotResult(saved.size(), splitCounts(saved));
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
        if (!isDatasetAction(run) || run.getSnapshotCleanedAt() != null) {
            return;
        }
        deleteTree(snapshotRoot(run));
        run.setSnapshotCleanedAt(LocalDateTime.now());
        workspaceQuotaRefreshService.scheduleUsageRefresh(run.getWorkspaceId());
    }

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
        return switch (split) {
            case TRAIN -> requirements.train();
            case VAL -> requirements.val();
            case TEST -> requirements.test();
        };
    }

    private ResolvedMaterial resolveMaterial(String workspaceId,
                                             DatasetItem item,
                                             ActionDto.TrainingImageSelection selection) {
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
        ResolvedFile image = selectImage(item, images, selection);
        if (!Files.isRegularFile(image.path())) {
            throw new IllegalArgumentException("Selected image file is missing for " + item.getSourcePageName());
        }
        return new ResolvedMaterial(item, image, xml);
    }

    private ResolvedFile selectImage(DatasetItem item,
                                     List<ResolvedFile> images,
                                     ActionDto.TrainingImageSelection selection) {
        if (images.isEmpty()) {
            throw new IllegalArgumentException("No selected image is available for " + item.getSourcePageName());
        }
        Map<String, String> overrides = selection == null || selection.itemImageIds() == null
                ? Map.of() : selection.itemImageIds();
        String override = overrides.get(item.getId());
        if (override != null && !override.isBlank()) {
            return images.stream().filter(image -> override.equals(image.id())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Image override is not part of dataset item " + item.getSourcePageName()));
        }
        String globalVariant = selection == null ? null : selection.globalVariant();
        if (globalVariant != null && !globalVariant.isBlank()) {
            return images.stream().filter(image -> globalVariant.trim().equals(image.variant())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Image variant " + globalVariant + " is missing for " + item.getSourcePageName()));
        }
        if (images.size() == 1) {
            return images.getFirst();
        }
        throw new IllegalArgumentException("Choose an image variant for " + item.getSourcePageName());
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
                    .map(file -> new ActionDto.TrainingImageChoice(file.getSourceFileId(), file.getFileName(), file.getVariant()))
                    .toList();
            return new ResolvedOverview(xmlAvailable, images);
        }
        Page page = pageRepository.findById(item.getSourcePageId()).orElseThrow();
        if (page.getProject() == null || page.getProject().getLibrary() == null
                || !workspaceId.equals(page.getProject().getLibrary().getWorkspaceId())) {
            throw new IllegalArgumentException("Source page is unavailable");
        }
        boolean xmlAvailable = pageXmlRepository.findById(item.getSelectedSourceXmlId())
                .filter(xml -> Files.isRegularFile(uploadPathService.resolve(xml.getFilePath())))
                .isPresent();
        Set<String> selectedImageIds = new LinkedHashSet<>(item.getSelectedSourceImageIds());
        List<ActionDto.TrainingImageChoice> images = pageImageRepository.findByPageId(page.getId()).stream()
                .filter(image -> selectedImageIds.contains(image.getId()))
                .filter(image -> Files.isRegularFile(uploadPathService.resolve(image.getFilePath())))
                .map(image -> new ActionDto.TrainingImageChoice(image.getId(), image.getFileName(), image.getVariant()))
                .toList();
        return new ResolvedOverview(xmlAvailable, images);
    }

    private Dataset requireDataset(String workspaceId, String datasetId) {
        return datasetRepository.findByIdAndWorkspaceId(datasetId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));
    }

    private ResolvedFile resolved(DatasetItemCopyFile file) {
        return new ResolvedFile(file.getSourceFileId(), file.getFileName(), file.getVariant(), file.getMimeType(),
                file.getFileSize(), uploadPathService.resolve(file.getFilePath()));
    }

    private ResolvedFile resolved(PageImage file) {
        return new ResolvedFile(file.getId(), file.getFileName(), file.getVariant(), file.getMimeType(),
                file.getFileSize() == null ? 0L : file.getFileSize(), uploadPathService.resolve(file.getFilePath()));
    }

    private ResolvedFile resolved(PageXml file) {
        return new ResolvedFile(file.getId(), file.getFileName(), file.getVariant(), file.getMimeType(),
                file.getFileSize() == null ? 0L : file.getFileSize(), uploadPathService.resolve(file.getFilePath()));
    }

    private Path snapshotRoot(ActionRun run) {
        String scope = run.getKind() == ActionRun.Kind.EVALUATION ? "evaluation" : "training";
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
        inputs.forEach(input -> result.merge(input.getSplit().name(), 1L, Long::sum));
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
    private record ResolvedFile(String id, String fileName, String variant, String mimeType, long size, Path path) {}
    private record ResolvedOverview(boolean xmlAvailable, List<ActionDto.TrainingImageChoice> images) {}
}
