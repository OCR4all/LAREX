package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.DatasetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItemCopyFile;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetRelease;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemCopyFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetReleaseRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DatasetItemRepository datasetItemRepository;
    private final DatasetItemCopyFileRepository datasetItemCopyFileRepository;
    private final DatasetReleaseRepository datasetReleaseRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final PageImageRepository pageImageRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final WorkspaceQuotaRefreshService workspaceQuotaRefreshService;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final ArchiveIoService archiveIoService;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public DatasetService(DatasetRepository datasetRepository,
                          DatasetItemRepository datasetItemRepository,
                          DatasetItemCopyFileRepository datasetItemCopyFileRepository,
                          DatasetReleaseRepository datasetReleaseRepository,
                          ProjectRepository projectRepository,
                          PageRepository pageRepository,
                          PageXmlRepository pageXmlRepository,
                          PageImageRepository pageImageRepository,
                          WorkspaceAccessService workspaceAccessService,
                          WorkspaceQuotaGuardService workspaceQuotaGuardService,
                          WorkspaceQuotaRefreshService workspaceQuotaRefreshService,
                          AuthorizationPolicyService authorizationPolicyService,
                          ArchiveIoService archiveIoService,
                          ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.datasetItemRepository = datasetItemRepository;
        this.datasetItemCopyFileRepository = datasetItemCopyFileRepository;
        this.datasetReleaseRepository = datasetReleaseRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.pageImageRepository = pageImageRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
        this.authorizationPolicyService = authorizationPolicyService;
        this.archiveIoService = archiveIoService;
        this.objectMapper = objectMapper;
    }

    public List<DatasetDto.SummaryResponse> listDatasets(String workspaceId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        AuthorizationCapabilitiesDto.DatasetCapabilities capabilities =
                authorizationPolicyService.resolveDatasetCapabilities(workspaceId, userId);
        return datasetRepository.findByWorkspaceIdOrderByUpdatedDesc(workspaceId).stream()
                .map(dataset -> toSummaryResponse(dataset, capabilities))
                .toList();
    }

    public DatasetDto.DetailResponse getDataset(String workspaceId, String datasetId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        Dataset dataset = requireDataset(workspaceId, datasetId);
        return toDetailResponse(dataset, authorizationPolicyService.resolveDatasetCapabilities(workspaceId, userId));
    }

    public DatasetDto.DetailResponse createDataset(String workspaceId,
                                                   DatasetDto.CreateOrUpdateRequest request,
                                                   String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        validateDatasetName(workspaceId, request.name(), null);

        Dataset dataset = new Dataset();
        dataset.setWorkspaceId(workspaceId);
        applyDatasetConfiguration(dataset, request);
        Dataset saved = datasetRepository.save(dataset);
        return toDetailResponse(saved, authorizationPolicyService.resolveDatasetCapabilities(workspaceId, userId));
    }

    public DatasetDto.DetailResponse updateDataset(String workspaceId,
                                                   String datasetId,
                                                   DatasetDto.CreateOrUpdateRequest request,
                                                   String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        Dataset dataset = requireDataset(workspaceId, datasetId);
        validateDatasetName(workspaceId, request.name(), dataset.getId());
        applyDatasetConfiguration(dataset, request);
        Dataset saved = datasetRepository.save(dataset);
        return toDetailResponse(saved, authorizationPolicyService.resolveDatasetCapabilities(workspaceId, userId));
    }

    public void deleteDataset(String workspaceId, String datasetId, String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        Dataset dataset = requireDataset(workspaceId, datasetId);
        deleteDatasetFiles(dataset);
        datasetRepository.delete(dataset);
        workspaceQuotaRefreshService.scheduleUsageRefresh(workspaceId);
    }

    public DatasetDto.DetailResponse addItems(String workspaceId,
                                              String datasetId,
                                              DatasetDto.AddItemsRequest request,
                                              String userId) throws IOException {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        Dataset dataset = requireDataset(workspaceId, datasetId);
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }

        List<DatasetDto.AddItemRequest> normalizedItems = request.items().stream()
                .filter(Objects::nonNull)
                .toList();
        if (normalizedItems.isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }

        Set<String> seenPageIds = new HashSet<>();
        List<PendingItem> pendingItems = new ArrayList<>();
        long copyBytes = 0L;

        for (DatasetDto.AddItemRequest addItemRequest : normalizedItems) {
            if (!seenPageIds.add(addItemRequest.sourcePageId())) {
                throw new IllegalArgumentException("Duplicate source page in request: " + addItemRequest.sourcePageId());
            }
            if (datasetItemRepository.existsByDatasetIdAndSourcePageId(datasetId, addItemRequest.sourcePageId())) {
                throw new IllegalArgumentException("Page already exists in dataset: " + addItemRequest.sourcePageId());
            }

            Page page = requirePageInWorkspace(addItemRequest.sourcePageId(), workspaceId);
            if (!page.getProject().getId().equals(addItemRequest.sourceProjectId())) {
                throw new IllegalArgumentException("Source project does not match page: " + addItemRequest.sourcePageId());
            }

            PageXml xml = requireSourceXml(page, addItemRequest.sourceXmlId());
            List<PageImage> selectedImages = requireSourceImages(page, addItemRequest.sourceImageIds());

            PendingItem pendingItem = new PendingItem(page, xml, selectedImages, addItemRequest.mode());
            pendingItems.add(pendingItem);
            if (addItemRequest.mode() == DatasetItem.Mode.COPY) {
                copyBytes += xml.getFileSize() == null ? 0L : xml.getFileSize();
                for (PageImage image : selectedImages) {
                    copyBytes += image.getFileSize() == null ? 0L : image.getFileSize();
                }
            }
        }

        long reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(workspaceId, copyBytes, "dataset-item-copy");
        try {
            List<DatasetItem> newItems = new ArrayList<>();
            for (PendingItem pendingItem : pendingItems) {
                DatasetItem item = createDatasetItem(dataset, pendingItem);
                newItems.add(datasetItemRepository.save(item));
            }

            List<DatasetItem> allItems = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(datasetId);
            regenerateSplitAssignments(dataset, allItems);

            for (DatasetItem item : newItems) {
                if (item.getMode() == DatasetItem.Mode.COPY) {
                    createCopiedFiles(item, pendingItems.stream()
                            .filter(candidate -> candidate.page().getId().equals(item.getSourcePageId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Pending item not found: " + item.getSourcePageId())));
                    item.setCopiedAt(LocalDateTime.now());
                    datasetItemRepository.save(item);
                }
            }

            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
        } catch (IOException | RuntimeException e) {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
            throw e;
        }

        return getDataset(workspaceId, datasetId, userId);
    }

    public DatasetDto.DetailResponse updateItem(String workspaceId,
                                                String datasetId,
                                                String itemId,
                                                DatasetDto.UpdateItemRequest request,
                                                String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        requireDataset(workspaceId, datasetId);
        DatasetItem item = datasetItemRepository.findByIdAndDatasetId(itemId, datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset item", itemId));
        if (request.assignedSplit() != null) {
            item.setAssignedSplit(normalizeSplitForTemplate(item.getDataset().getSplitTemplate(), request.assignedSplit()));
            item.setManualSplit(true);
        }
        if (request.pinned() != null) {
            item.setPinned(request.pinned());
        }
        datasetItemRepository.save(item);
        return getDataset(workspaceId, datasetId, userId);
    }

    public DatasetDto.DetailResponse deleteItem(String workspaceId,
                                                String datasetId,
                                                String itemId,
                                                String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        requireDataset(workspaceId, datasetId);
        DatasetItem item = datasetItemRepository.findByIdAndDatasetId(itemId, datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset item", itemId));
        deleteCopiedFiles(item.getCopyFiles());
        datasetItemRepository.delete(item);
        workspaceQuotaRefreshService.scheduleUsageRefresh(workspaceId);
        return getDataset(workspaceId, datasetId, userId);
    }

    public DatasetDto.DetailResponse generateSplit(String workspaceId,
                                                   String datasetId,
                                                   DatasetDto.GenerateSplitRequest request,
                                                   String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        Dataset dataset = requireDataset(workspaceId, datasetId);

        if (request != null) {
            DatasetDto.CreateOrUpdateRequest configRequest = new DatasetDto.CreateOrUpdateRequest(
                    dataset.getName(),
                    dataset.getDescription(),
                    dataset.getTags(),
                    request.splitTemplate() != null ? request.splitTemplate() : dataset.getSplitTemplate(),
                    request.splitAlgorithm() != null ? request.splitAlgorithm() : dataset.getSplitAlgorithm(),
                    request.splitSeed() != null ? request.splitSeed() : dataset.getSplitSeed(),
                    request.trainPercentage() != null ? request.trainPercentage() : dataset.getTrainPercentage(),
                    request.valPercentage() != null ? request.valPercentage() : dataset.getValPercentage(),
                    request.testPercentage() != null ? request.testPercentage() : dataset.getTestPercentage(),
                    request.stratifyTagIds() != null ? request.stratifyTagIds() : dataset.getStratifyTagIds()
            );
            applyDatasetConfiguration(dataset, configRequest);
            datasetRepository.save(dataset);
        }

        List<DatasetItem> items = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(datasetId);
        regenerateSplitAssignments(dataset, items);
        return getDataset(workspaceId, datasetId, userId);
    }

    public DatasetDto.ValidationResponse validateDataset(String workspaceId, String datasetId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        Dataset dataset = requireDataset(workspaceId, datasetId);
        List<DatasetItem> items = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(datasetId);
        ValidationSnapshot snapshot = validateItems(dataset, items, true);
        return new DatasetDto.ValidationResponse(snapshot.status(), snapshot.stats(), snapshot.warnings(), snapshot.issues());
    }

    public byte[] exportDatasetPackage(String workspaceId, String datasetId, String userId) throws IOException {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        Dataset dataset = requireDataset(workspaceId, datasetId);
        List<DatasetItem> items = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(datasetId);

        ValidationSnapshot validationSnapshot = validateItems(dataset, items, true);
        if (validationSnapshot.status() == Dataset.ValidationStatus.INVALID) {
            dataset.setLastExportStatus(Dataset.ExportStatus.FAILED);
            datasetRepository.save(dataset);
            throw new IllegalStateException("Dataset contains broken items and cannot be exported.");
        }

        ExportSnapshot exportSnapshot = buildExportSnapshot(dataset, items, validationSnapshot.warnings(), null, LocalDateTime.now());
        byte[] zipBytes = createPackageBytes(exportSnapshot);

        dataset.setLastExportStatus(Dataset.ExportStatus.READY);
        dataset.setLastExportedAt(LocalDateTime.now());
        datasetRepository.save(dataset);
        return zipBytes;
    }

    public List<DatasetDto.ReleaseSummaryResponse> listReleases(String workspaceId, String datasetId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        requireDataset(workspaceId, datasetId);
        return datasetReleaseRepository.findByDatasetIdOrderByVersionNumberDesc(datasetId).stream()
                .map(this::toReleaseSummaryResponse)
                .toList();
    }

    public DatasetDto.ReleaseSummaryResponse createRelease(String workspaceId,
                                                           String datasetId,
                                                           DatasetDto.CreateReleaseRequest request,
                                                           String userId) throws IOException {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        Dataset dataset = requireDataset(workspaceId, datasetId);
        List<DatasetItem> items = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(datasetId);

        ValidationSnapshot validationSnapshot = validateItems(dataset, items, true);
        if (validationSnapshot.status() == Dataset.ValidationStatus.INVALID) {
            dataset.setLastExportStatus(Dataset.ExportStatus.FAILED);
            datasetRepository.save(dataset);
            throw new IllegalStateException("Dataset contains broken items and cannot be released.");
        }

        int nextVersionNumber = defaultInt(datasetReleaseRepository.findMaxVersionNumberByDatasetId(datasetId)) + 1;
        String versionTag = normalizeReleaseTag(request == null ? null : request.versionTag(), nextVersionNumber, datasetId);

        DatasetRelease release = new DatasetRelease();
        release.setDataset(dataset);
        release.setVersionNumber(nextVersionNumber);
        release.setVersionTag(versionTag);
        release.setNotes(request == null ? null : normalizeNullableText(request.notes()));
        release.setCreatedByUserId(userId);
        release.setStatus(DatasetRelease.Status.CREATING);
        release.setValidationStatus(validationSnapshot.status());
        release.setItemCount((long) items.size());
        release.setSourceDatasetUpdatedAt(dataset.getUpdated());
        release = datasetReleaseRepository.save(release);

        Path releaseRoot = datasetReleaseRoot(workspaceId, datasetId, release.getId());
        long reservedBytes = 0L;

        try {
            LocalDateTime releasedAt = LocalDateTime.now();
            ExportSnapshot exportSnapshot = buildExportSnapshot(dataset, items, validationSnapshot.warnings(), release, releasedAt);
            reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    workspaceId,
                    estimatePackageBytes(exportSnapshot),
                    "dataset-release"
            );

            byte[] zipBytes = createPackageBytes(exportSnapshot);
            Files.createDirectories(releaseRoot);

            String fileName = sanitizeSegment(dataset.getName()) + "-" + sanitizeSegment(versionTag) + ".larex-dataset.zip";
            Path packagePath = releaseRoot.resolve(fileName);
            Files.write(packagePath, zipBytes);

            String manifestJson = objectMapper.writeValueAsString(exportSnapshot.manifest());
            String statsJson = objectMapper.writeValueAsString(exportSnapshot.stats());

            release.setStatus(DatasetRelease.Status.READY);
            release.setFailureReason(null);
            release.setPackageFileName(fileName);
            release.setPackageFilePath(relativeToUploadRoot(packagePath));
            release.setPackageFileSize(Files.size(packagePath));
            release.setPackageChecksumSha256(computeSha256(packagePath));
            release.setManifestChecksumSha256(computeSha256(manifestJson.getBytes()));
            release.setManifestJson(manifestJson);
            release.setStatsJson(statsJson);
            release.setWarningsJson(writeWarnings(validationSnapshot.warnings()));
            datasetReleaseRepository.save(release);

            dataset.setLastExportStatus(Dataset.ExportStatus.READY);
            dataset.setLastExportedAt(releasedAt);
            datasetRepository.save(dataset);
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
            return toReleaseSummaryResponse(release);
        } catch (IOException | RuntimeException e) {
            deleteRecursively(releaseRoot);
            datasetReleaseRepository.deleteById(release.getId());
            if (reservedBytes > 0) {
                workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
            }
            dataset.setLastExportStatus(Dataset.ExportStatus.FAILED);
            datasetRepository.save(dataset);
            throw e;
        }
    }

    public ReleaseDownload downloadReleasePackage(String workspaceId,
                                                  String datasetId,
                                                  String releaseId,
                                                  String userId) throws IOException {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        requireDataset(workspaceId, datasetId);
        DatasetRelease release = requireRelease(datasetId, releaseId);
        if (release.getPackageFilePath() == null || release.getPackageFilePath().isBlank()) {
            throw new IllegalStateException("Release package is not available.");
        }
        Path packagePath = resolveStoragePath(release.getPackageFilePath());
        if (!Files.exists(packagePath)) {
            throw new IllegalStateException("Release package file is missing.");
        }
        return new ReleaseDownload(
                release.getPackageFileName() == null ? ("dataset-release-" + releaseId + ".zip") : release.getPackageFileName(),
                Files.readAllBytes(packagePath)
        );
    }

    private Dataset requireDataset(String workspaceId, String datasetId) {
        return datasetRepository.findByIdAndWorkspaceId(datasetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset", datasetId));
    }

    private DatasetRelease requireRelease(String datasetId, String releaseId) {
        return datasetReleaseRepository.findByIdAndDatasetId(releaseId, datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset release", releaseId));
    }

    private Page requirePageInWorkspace(String pageId, String workspaceId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));
        if (page.getProject() == null || page.getProject().getLibrary() == null
                || !workspaceId.equals(page.getProject().getLibrary().getWorkspaceId())) {
            throw new IllegalArgumentException("Page does not belong to workspace: " + pageId);
        }
        return page;
    }

    private PageXml requireSourceXml(Page page, String sourceXmlId) {
        PageXml xml = pageXmlRepository.findById(sourceXmlId)
                .orElseThrow(() -> new IllegalArgumentException("Source XML not found: " + sourceXmlId));
        if (xml.getPage() == null || !page.getId().equals(xml.getPage().getId())) {
            throw new IllegalArgumentException("Source XML does not belong to page: " + sourceXmlId);
        }
        return xml;
    }

    private List<PageImage> requireSourceImages(Page page, Collection<String> sourceImageIds) {
        if (sourceImageIds == null || sourceImageIds.isEmpty()) {
            throw new IllegalArgumentException("At least one image must be selected");
        }
        Set<String> requestedIds = sourceImageIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedIds.isEmpty()) {
            throw new IllegalArgumentException("At least one image must be selected");
        }
        List<PageImage> images = pageImageRepository.findByPageId(page.getId()).stream()
                .filter(image -> requestedIds.contains(image.getId()))
                .sorted(Comparator.comparing(PageImage::getId))
                .toList();
        if (images.size() != requestedIds.size()) {
            throw new IllegalArgumentException("One or more selected images do not belong to the page");
        }
        return images;
    }

    private DatasetItem createDatasetItem(Dataset dataset, PendingItem pendingItem) {
        Page page = pendingItem.page();
        PageXml xml = pendingItem.xml();

        DatasetItem item = new DatasetItem();
        item.setDataset(dataset);
        item.setSourceProjectId(page.getProject().getId());
        item.setSourceProjectName(page.getProject().getName());
        item.setSourcePageId(page.getId());
        item.setSourcePageName(page.getName());
        item.setSourcePageTags(page.getTags() == null ? List.of() : new ArrayList<>(page.getTags()));
        item.setMode(pendingItem.mode());
        item.setSelectedSourceXmlId(xml.getId());
        item.setSelectedSourceXmlFileName(xml.getFileName());
        item.setSelectedSourceXmlUpdatedAt(xml.getUpdated());
        item.setSelectedSourceImageIds(new ArrayList<>(pendingItem.images().stream().map(PageImage::getId).toList()));
        item.setAssignedSplit(DatasetItem.Split.TRAIN);
        item.setManualSplit(false);
        item.setPinned(false);
        item.setStatus(DatasetItem.Status.READY);
        item.setBrokenReason(null);
        item.setSourcePageUpdatedAtSnapshot(page.getUpdated());
        return item;
    }

    private void createCopiedFiles(DatasetItem item, PendingItem pendingItem) throws IOException {
        Path datasetRoot = datasetRoot(item.getDataset().getWorkspaceId(), item.getDataset().getId(), item.getId());
        Files.createDirectories(datasetRoot);

        PageXml xml = pendingItem.xml();
        DatasetItemCopyFile xmlCopy = new DatasetItemCopyFile();
        xmlCopy.setDatasetItem(item);
        xmlCopy.setKind(DatasetItemCopyFile.Kind.XML);
        xmlCopy.setSourceFileId(xml.getId());
        xmlCopy.setFileName(xml.getFileName());
        xmlCopy.setMimeType(xml.getMimeType());
        xmlCopy.setVariant(xml.getVariant());
        xmlCopy.setBaseName(xml.getBaseName());
        xmlCopy.setSourceUpdatedAt(xml.getUpdated());
        copyFile(resolveStoragePath(xml.getFilePath()), datasetRoot.resolve("xml"), xmlCopy);
        datasetItemCopyFileRepository.save(xmlCopy);

        for (PageImage image : pendingItem.images()) {
            DatasetItemCopyFile imageCopy = new DatasetItemCopyFile();
            imageCopy.setDatasetItem(item);
            imageCopy.setKind(DatasetItemCopyFile.Kind.IMAGE);
            imageCopy.setSourceFileId(image.getId());
            imageCopy.setFileName(image.getFileName());
            imageCopy.setMimeType(image.getMimeType());
            imageCopy.setVariant(image.getVariant());
            imageCopy.setBaseName(image.getBaseName());
            imageCopy.setSourceUpdatedAt(image.getUpdated());
            copyFile(resolveStoragePath(image.getFilePath()), datasetRoot.resolve("images"), imageCopy);
            datasetItemCopyFileRepository.save(imageCopy);
        }
    }

    private void copyFile(Path sourcePath, Path targetDir, DatasetItemCopyFile copyFile) throws IOException {
        Files.createDirectories(targetDir);
        String extension = fileExtension(copyFile.getFileName());
        String targetFileName = UUID.randomUUID().toString().replace("-", "") + (extension.isBlank() ? "" : "." + extension);
        Path targetPath = targetDir.resolve(targetFileName);
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        copyFile.setFilePath(relativeToUploadRoot(targetPath));
        copyFile.setFileSize(Files.size(targetPath));
        copyFile.setChecksumSha256(computeSha256(targetPath));
    }

    private void deleteDatasetFiles(Dataset dataset) {
        List<DatasetItem> items = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(dataset.getId());
        for (DatasetItem item : items) {
            deleteCopiedFiles(item.getCopyFiles());
        }
        Path root = datasetRoot(dataset.getWorkspaceId(), dataset.getId(), null);
        deleteRecursively(root);
    }

    private void deleteCopiedFiles(List<DatasetItemCopyFile> copyFiles) {
        if (copyFiles == null) {
            return;
        }
        for (DatasetItemCopyFile copyFile : copyFiles) {
            deleteRecursively(resolveStoragePath(copyFile.getFilePath()));
        }
    }

    private void regenerateSplitAssignments(Dataset dataset, List<DatasetItem> items) {
        validateSplitConfiguration(dataset);
        List<String> warnings = new ArrayList<>();

        Dataset.SplitTemplate template = dataset.getSplitTemplate();
        Map<DatasetItem.Split, Integer> targetCounts = targetCounts(template, dataset.getTrainPercentage(),
                dataset.getValPercentage(), dataset.getTestPercentage(), items.size());

        List<DatasetItem> preservedItems = items.stream()
                .filter(item -> shouldPreserveSplit(item))
                .toList();

        Map<DatasetItem.Split, Integer> preservedCounts = new EnumMap<>(DatasetItem.Split.class);
        for (DatasetItem.Split split : DatasetItem.Split.values()) {
            preservedCounts.put(split, 0);
        }
        for (DatasetItem item : preservedItems) {
            DatasetItem.Split split = normalizeSplitForTemplate(template, item.getAssignedSplit());
            item.setAssignedSplit(split);
            preservedCounts.put(split, preservedCounts.get(split) + 1);
        }

        for (Map.Entry<DatasetItem.Split, Integer> entry : preservedCounts.entrySet()) {
            if (entry.getValue() > targetCounts.getOrDefault(entry.getKey(), 0)) {
                warnings.add("Preserved assignments exceed target size for split " + entry.getKey().name().toLowerCase(Locale.ROOT) + ".");
            }
        }

        List<DatasetItem> mutableItems = items.stream()
                .filter(item -> !preservedItems.contains(item))
                .toList();
        Map<DatasetItem.Split, Integer> remainingCounts = new EnumMap<>(DatasetItem.Split.class);
        for (DatasetItem.Split split : allowedSplits(template)) {
            remainingCounts.put(split, Math.max(0, targetCounts.getOrDefault(split, 0) - preservedCounts.getOrDefault(split, 0)));
        }

        switch (dataset.getSplitAlgorithm()) {
            case RANDOM_SEEDED -> assignRandomly(mutableItems, remainingCounts, template, dataset.getSplitSeed());
            case GROUP_BY_SOURCE_PROJECT -> assignGrouped(
                    mutableItems,
                    remainingCounts,
                    template,
                    dataset.getSplitSeed(),
                    item -> item.getSourceProjectId()
            );
            case MULTILABEL_STRATIFIED_BY_TAGS -> assignMultilabelStratified(mutableItems, remainingCounts, template, dataset.getSplitSeed(),
                    dataset.getStratifyTagIds(), warnings);
        }

        for (DatasetItem item : items) {
            item.setAssignedSplit(normalizeSplitForTemplate(template, item.getAssignedSplit()));
            datasetItemRepository.save(item);
        }

        dataset.setLastValidationWarningsJson(writeWarnings(warnings));
        datasetRepository.save(dataset);
    }

    private boolean shouldPreserveSplit(DatasetItem item) {
        return item.isPinned() || (item.getMode() == DatasetItem.Mode.COPY && item.getCopiedAt() != null);
    }

    private void assignRandomly(List<DatasetItem> items,
                                Map<DatasetItem.Split, Integer> remainingCounts,
                                Dataset.SplitTemplate template,
                                Long seed) {
        List<DatasetItem> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, new Random(seed == null ? 42L : seed));
        List<DatasetItem.Split> splitOrder = allowedSplits(template);
        int cursor = 0;
        for (DatasetItem.Split split : splitOrder) {
            int amount = remainingCounts.getOrDefault(split, 0);
            for (int i = 0; i < amount && cursor < shuffled.size(); i++) {
                shuffled.get(cursor++).setAssignedSplit(split);
            }
        }
        while (cursor < shuffled.size()) {
            shuffled.get(cursor++).setAssignedSplit(fallbackSplit(template));
        }
    }

    private void assignGrouped(List<DatasetItem> items,
                               Map<DatasetItem.Split, Integer> remainingCounts,
                               Dataset.SplitTemplate template,
                               Long seed,
                               java.util.function.Function<DatasetItem, String> grouper) {
        Map<String, List<DatasetItem>> groups = items.stream()
                .collect(Collectors.groupingBy(grouper, LinkedHashMap::new, Collectors.toList()));
        List<Map.Entry<String, List<DatasetItem>>> entries = new ArrayList<>(groups.entrySet());
        Collections.shuffle(entries, new Random(seed == null ? 42L : seed));
        Map<DatasetItem.Split, Integer> assignedCounts = new EnumMap<>(DatasetItem.Split.class);
        for (DatasetItem.Split split : allowedSplits(template)) {
            assignedCounts.put(split, 0);
        }

        for (Map.Entry<String, List<DatasetItem>> entry : entries) {
            DatasetItem.Split bestSplit = bestSplitForGroup(entry.getValue().size(), remainingCounts, assignedCounts, template);
            for (DatasetItem item : entry.getValue()) {
                item.setAssignedSplit(bestSplit);
            }
            assignedCounts.put(bestSplit, assignedCounts.get(bestSplit) + entry.getValue().size());
        }
    }

    private void assignMultilabelStratified(List<DatasetItem> items,
                                            Map<DatasetItem.Split, Integer> remainingCounts,
                                            Dataset.SplitTemplate template,
                                            Long seed,
                                            List<String> stratifyTagIds,
                                            List<String> warnings) {
        List<String> effectiveTags = stratifyTagIds == null ? List.of() : stratifyTagIds.stream()
                .filter(Objects::nonNull)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .toList();
        if (effectiveTags.isEmpty()) {
            warnings.add("Multilabel stratified splitting requested without stratify tags. Falling back to random seeded assignment.");
            assignRandomly(items, remainingCounts, template, seed);
            return;
        }

        Map<String, List<DatasetItem>> buckets = items.stream()
                .collect(Collectors.groupingBy(item -> stratifySignature(item, effectiveTags), LinkedHashMap::new, Collectors.toList()));
        Random random = new Random(seed == null ? 42L : seed);
        Map<DatasetItem.Split, Integer> used = new EnumMap<>(DatasetItem.Split.class);
        for (DatasetItem.Split split : allowedSplits(template)) {
            used.put(split, 0);
        }

        for (List<DatasetItem> bucketItems : buckets.values()) {
            List<DatasetItem> shuffledBucket = new ArrayList<>(bucketItems);
            Collections.shuffle(shuffledBucket, random);
            assignRandomly(shuffledBucket, deriveBucketTargets(shuffledBucket.size(), remainingCounts, template), template, random.nextLong());
            for (DatasetItem item : shuffledBucket) {
                used.put(item.getAssignedSplit(), used.getOrDefault(item.getAssignedSplit(), 0) + 1);
            }
        }
    }

    private Map<DatasetItem.Split, Integer> deriveBucketTargets(int bucketSize,
                                                                Map<DatasetItem.Split, Integer> remainingCounts,
                                                                Dataset.SplitTemplate template) {
        int totalRemaining = remainingCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (totalRemaining <= 0) {
            return Map.of(fallbackSplit(template), bucketSize);
        }
        Map<DatasetItem.Split, Integer> targets = new EnumMap<>(DatasetItem.Split.class);
        int assigned = 0;
        List<DatasetItem.Split> splits = allowedSplits(template);
        for (int i = 0; i < splits.size(); i++) {
            DatasetItem.Split split = splits.get(i);
            if (i == splits.size() - 1) {
                targets.put(split, Math.max(0, bucketSize - assigned));
                continue;
            }
            double fraction = remainingCounts.getOrDefault(split, 0) / (double) totalRemaining;
            int count = (int) Math.round(bucketSize * fraction);
            targets.put(split, count);
            assigned += count;
        }
        return targets;
    }

    private DatasetItem.Split bestSplitForGroup(int groupSize,
                                                Map<DatasetItem.Split, Integer> remainingCounts,
                                                Map<DatasetItem.Split, Integer> assignedCounts,
                                                Dataset.SplitTemplate template) {
        return allowedSplits(template).stream()
                .min(Comparator.comparingInt(split -> {
                    int remaining = remainingCounts.getOrDefault(split, 0) - assignedCounts.getOrDefault(split, 0);
                    if (remaining >= groupSize) {
                        return remaining - groupSize;
                    }
                    return Math.abs(remaining) + groupSize;
                }))
                .orElse(fallbackSplit(template));
    }

    private ValidationSnapshot validateItems(Dataset dataset,
                                             List<DatasetItem> items,
                                             boolean persist) {
        List<DatasetDto.ValidationIssue> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        long broken = 0L;

        for (DatasetItem item : items) {
            String brokenReason = computeBrokenReason(dataset.getWorkspaceId(), item);
            if (brokenReason == null) {
                item.setStatus(DatasetItem.Status.READY);
                item.setBrokenReason(null);
            } else {
                item.setStatus(DatasetItem.Status.BROKEN);
                item.setBrokenReason(brokenReason);
                broken++;
                issues.add(new DatasetDto.ValidationIssue(item.getId(), item.getSourcePageName(), brokenReason));
            }
            if (persist) {
                datasetItemRepository.save(item);
            }
        }

        Dataset.ValidationStatus validationStatus = broken > 0 ? Dataset.ValidationStatus.INVALID : Dataset.ValidationStatus.VALID;
        DatasetDto.StatsResponse stats = buildStats(items);

        if (persist) {
            dataset.setLastValidationStatus(validationStatus);
            dataset.setLastValidationAt(LocalDateTime.now());
            dataset.setLastValidationWarningsJson(writeWarnings(warnings));
            datasetRepository.save(dataset);
        }

        return new ValidationSnapshot(validationStatus, stats, warnings, issues);
    }

    private String computeBrokenReason(String workspaceId, DatasetItem item) {
        if (item.getMode() == DatasetItem.Mode.LINK) {
            Optional<Page> pageOpt = pageRepository.findById(item.getSourcePageId());
            if (pageOpt.isEmpty() || pageOpt.get().getProject() == null || pageOpt.get().getProject().getLibrary() == null
                    || !workspaceId.equals(pageOpt.get().getProject().getLibrary().getWorkspaceId())) {
                return "Source page is no longer available in the workspace.";
            }
            Page page = pageOpt.get();
            Optional<PageXml> xmlOpt = pageXmlRepository.findById(item.getSelectedSourceXmlId());
            if (xmlOpt.isEmpty() || xmlOpt.get().getPage() == null || !page.getId().equals(xmlOpt.get().getPage().getId())) {
                return "Selected source annotation is missing.";
            }
            Set<String> currentImageIds = pageImageRepository.findByPageId(page.getId()).stream()
                    .map(PageImage::getId)
                    .collect(Collectors.toSet());
            for (String imageId : item.getSelectedSourceImageIds()) {
                if (!currentImageIds.contains(imageId)) {
                    return "One or more selected source image variants are missing.";
                }
            }
            return null;
        }

        List<DatasetItemCopyFile> copyFiles = item.getCopyFiles() == null ? List.of() : item.getCopyFiles();
        boolean hasXml = false;
        boolean hasImage = false;
        for (DatasetItemCopyFile copyFile : copyFiles) {
            Path path = resolveStoragePath(copyFile.getFilePath());
            if (!Files.exists(path)) {
                return "Frozen copy file is missing: " + copyFile.getFileName();
            }
            if (copyFile.getKind() == DatasetItemCopyFile.Kind.XML) {
                hasXml = true;
            }
            if (copyFile.getKind() == DatasetItemCopyFile.Kind.IMAGE) {
                hasImage = true;
            }
        }
        if (!hasXml) {
            return "Frozen annotation copy is missing.";
        }
        if (!hasImage) {
            return "Frozen image copy is missing.";
        }
        return null;
    }

    private ExportSnapshot buildExportSnapshot(Dataset dataset,
                                              List<DatasetItem> items,
                                              List<String> validationWarnings,
                                              DatasetRelease release,
                                              LocalDateTime exportedAt) throws IOException {
        Map<DatasetItem.Split, List<Map<String, Object>>> jsonlRows = new EnumMap<>(DatasetItem.Split.class);
        for (DatasetItem.Split split : DatasetItem.Split.values()) {
            jsonlRows.put(split, new ArrayList<>());
        }
        List<Map<String, Object>> manifestItems = new ArrayList<>();
        List<ExportFile> files = new ArrayList<>();

        for (DatasetItem item : items) {
            ExportMaterial material = exportMaterial(dataset.getWorkspaceId(), item);
            List<String> imagePaths = new ArrayList<>();
            for (ResolvedFile resolvedImage : material.images()) {
                String archivePath = "files/images/" + item.getId() + "/" + resolvedImage.fileName();
                files.add(new ExportFile(archivePath, resolvedImage.absolutePath()));
                imagePaths.add(archivePath);
            }

            ResolvedFile xmlFile = material.xml();
            String xmlArchivePath = "files/xml/" + item.getId() + "/" + xmlFile.fileName();
            files.add(new ExportFile(xmlArchivePath, xmlFile.absolutePath()));

            Map<String, Object> line = new LinkedHashMap<>();
            line.put("itemId", item.getId());
            line.put("datasetId", dataset.getId());
            line.put("mode", item.getMode().name());
            line.put("split", item.getAssignedSplit().name());
            line.put("sourceProjectId", item.getSourceProjectId());
            line.put("sourceProjectName", item.getSourceProjectName());
            line.put("sourcePageId", item.getSourcePageId());
            line.put("sourcePageName", item.getSourcePageName());
            line.put("tags", item.getSourcePageTags());
            line.put("xmlPath", xmlArchivePath);
            line.put("imagePaths", imagePaths);
            jsonlRows.get(item.getAssignedSplit()).add(line);

            Map<String, Object> manifestItem = new LinkedHashMap<>();
            manifestItem.put("itemId", item.getId());
            manifestItem.put("mode", item.getMode().name());
            manifestItem.put("split", item.getAssignedSplit().name());
            manifestItem.put("sourceProjectId", item.getSourceProjectId());
            manifestItem.put("sourcePageId", item.getSourcePageId());
            manifestItem.put("sourcePageName", item.getSourcePageName());
            manifestItem.put("selectedSourceXmlId", item.getSelectedSourceXmlId());
            manifestItem.put("selectedSourceImageIds", item.getSelectedSourceImageIds());
            manifestItem.put("sourceTags", item.getSourcePageTags());
            manifestItem.put("effectiveXml", Map.of(
                    "archivePath", xmlArchivePath,
                    "fileName", xmlFile.fileName(),
                    "checksumSha256", xmlFile.checksumSha256(),
                    "sourceUpdatedAt", material.xmlSourceUpdatedAt()
            ));
            manifestItem.put("effectiveImages", material.images().stream()
                    .map(image -> Map.of(
                            "archivePath", "files/images/" + item.getId() + "/" + image.fileName(),
                            "fileName", image.fileName(),
                            "checksumSha256", image.checksumSha256(),
                            "sourceUpdatedAt", image.sourceUpdatedAt()
                    ))
                    .toList());
            manifestItems.add(manifestItem);
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", "1.0");
        manifest.put("exportedAt", exportedAt);
        manifest.put("workspaceId", dataset.getWorkspaceId());
        Map<String, Object> datasetManifest = new LinkedHashMap<>();
        datasetManifest.put("id", dataset.getId());
        datasetManifest.put("name", dataset.getName());
        datasetManifest.put("description", dataset.getDescription());
        datasetManifest.put("tags", defaultList(dataset.getTags()));
        datasetManifest.put("splitTemplate", dataset.getSplitTemplate().name());
        datasetManifest.put("splitAlgorithm", dataset.getSplitAlgorithm().name());
        datasetManifest.put("splitSeed", dataset.getSplitSeed());
        datasetManifest.put("trainPercentage", dataset.getTrainPercentage());
        datasetManifest.put("valPercentage", dataset.getValPercentage());
        datasetManifest.put("testPercentage", dataset.getTestPercentage());
        datasetManifest.put("stratifyTagIds", defaultList(dataset.getStratifyTagIds()));
        manifest.put("dataset", datasetManifest);
        if (release != null) {
            Map<String, Object> releaseManifest = new LinkedHashMap<>();
            releaseManifest.put("id", release.getId());
            releaseManifest.put("versionNumber", release.getVersionNumber());
            releaseManifest.put("versionTag", release.getVersionTag());
            releaseManifest.put("notes", release.getNotes());
            releaseManifest.put("createdByUserId", release.getCreatedByUserId());
            releaseManifest.put("immutable", true);
            manifest.put("release", releaseManifest);
        }
        manifest.put("warnings", validationWarnings);
        manifest.put("items", manifestItems);

        return new ExportSnapshot(manifest, buildStats(items), jsonlRows, files);
    }

    private ExportMaterial exportMaterial(String workspaceId, DatasetItem item) throws IOException {
        if (item.getMode() == DatasetItem.Mode.COPY) {
            List<DatasetItemCopyFile> copyFiles = item.getCopyFiles() == null ? List.of() : item.getCopyFiles();
            ResolvedFile xml = copyFiles.stream()
                    .filter(file -> file.getKind() == DatasetItemCopyFile.Kind.XML)
                    .findFirst()
                    .map(file -> new ResolvedFile(file.getFileName(), resolveStoragePath(file.getFilePath()),
                            file.getChecksumSha256(), file.getSourceUpdatedAt()))
                    .orElseThrow(() -> new IllegalStateException("Dataset copy item is missing XML: " + item.getId()));
            List<ResolvedFile> images = copyFiles.stream()
                    .filter(file -> file.getKind() == DatasetItemCopyFile.Kind.IMAGE)
                    .map(file -> new ResolvedFile(file.getFileName(), resolveStoragePath(file.getFilePath()),
                            file.getChecksumSha256(), file.getSourceUpdatedAt()))
                    .toList();
            return new ExportMaterial(xml, images, item.getSelectedSourceXmlUpdatedAt());
        }

        Page page = requirePageInWorkspace(item.getSourcePageId(), workspaceId);
        PageXml xml = requireSourceXml(page, item.getSelectedSourceXmlId());
        List<PageImage> images = requireSourceImages(page, item.getSelectedSourceImageIds());

        ResolvedFile xmlFile = new ResolvedFile(
                xml.getFileName(),
                resolveStoragePath(xml.getFilePath()),
                computeSha256(resolveStoragePath(xml.getFilePath())),
                xml.getUpdated()
        );
        List<ResolvedFile> imageFiles = new ArrayList<>();
        for (PageImage image : images) {
            Path absolutePath = resolveStoragePath(image.getFilePath());
            imageFiles.add(new ResolvedFile(
                    image.getFileName(),
                    absolutePath,
                    computeSha256(absolutePath),
                    image.getUpdated()
            ));
        }
        return new ExportMaterial(xmlFile, imageFiles, xml.getUpdated());
    }

    private DatasetDto.DetailResponse toDetailResponse(Dataset dataset,
                                                       AuthorizationCapabilitiesDto.DatasetCapabilities capabilities) {
        List<DatasetItem> items = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(dataset.getId());
        DatasetDto.StatsResponse stats = buildStats(items);
        List<String> warnings = readWarnings(dataset.getLastValidationWarningsJson());

        return new DatasetDto.DetailResponse(
                dataset.getId(),
                dataset.getWorkspaceId(),
                dataset.getName(),
                dataset.getDescription(),
                defaultList(dataset.getTags()),
                dataset.getCreated(),
                dataset.getUpdated(),
                dataset.getSplitTemplate(),
                dataset.getSplitAlgorithm(),
                dataset.getSplitSeed(),
                dataset.getTrainPercentage(),
                dataset.getValPercentage(),
                dataset.getTestPercentage(),
                defaultList(dataset.getStratifyTagIds()),
                dataset.getLastValidationStatus(),
                dataset.getLastExportStatus(),
                dataset.getLastValidationAt(),
                dataset.getLastExportedAt(),
                warnings,
                stats,
                items.stream().map(this::toItemResponse).toList(),
                datasetReleaseRepository.findByDatasetIdOrderByVersionNumberDesc(dataset.getId()).stream()
                        .map(this::toReleaseSummaryResponse)
                        .toList(),
                capabilities
        );
    }

    private DatasetDto.SummaryResponse toSummaryResponse(Dataset dataset,
                                                         AuthorizationCapabilitiesDto.DatasetCapabilities capabilities) {
        List<DatasetItem> items = datasetItemRepository.findByDatasetIdOrderByCreatedAsc(dataset.getId());
        return new DatasetDto.SummaryResponse(
                dataset.getId(),
                dataset.getWorkspaceId(),
                dataset.getName(),
                dataset.getDescription(),
                defaultList(dataset.getTags()),
                dataset.getCreated(),
                dataset.getUpdated(),
                items.size(),
                buildStats(items),
                dataset.getLastValidationStatus(),
                dataset.getLastExportStatus(),
                dataset.getLastValidationAt(),
                dataset.getLastExportedAt(),
                capabilities
        );
    }

    private DatasetDto.ItemResponse toItemResponse(DatasetItem item) {
        return new DatasetDto.ItemResponse(
                item.getId(),
                item.getSourceProjectId(),
                item.getSourceProjectName(),
                item.getSourcePageId(),
                item.getSourcePageName(),
                defaultList(item.getSourcePageTags()),
                item.getMode(),
                item.getSelectedSourceXmlId(),
                item.getSelectedSourceXmlFileName(),
                defaultList(item.getSelectedSourceImageIds()),
                item.getAssignedSplit(),
                item.isManualSplit(),
                item.isPinned(),
                item.getStatus(),
                item.getBrokenReason(),
                item.getCopiedAt(),
                item.getCreated(),
                item.getUpdated()
        );
    }

    private DatasetDto.ReleaseSummaryResponse toReleaseSummaryResponse(DatasetRelease release) {
        return new DatasetDto.ReleaseSummaryResponse(
                release.getId(),
                release.getVersionNumber(),
                release.getVersionTag(),
                release.getNotes(),
                DatasetDto.DatasetReleaseStatus.valueOf(release.getStatus().name()),
                release.getValidationStatus(),
                release.getFailureReason(),
                release.getItemCount() == null ? 0L : release.getItemCount(),
                release.getPackageFileName(),
                release.getPackageFileSize(),
                release.getPackageChecksumSha256(),
                release.getManifestChecksumSha256(),
                release.getCreatedByUserId(),
                release.getSourceDatasetUpdatedAt(),
                release.getCreated(),
                release.getUpdated()
        );
    }

    private DatasetDto.StatsResponse buildStats(List<DatasetItem> items) {
        Map<String, Long> countsBySplit = new LinkedHashMap<>();
        Map<String, Long> countsBySourceProject = new LinkedHashMap<>();
        Map<String, Long> countsByMode = new LinkedHashMap<>();
        Map<String, Long> countsByTag = new LinkedHashMap<>();
        long linked = 0L;
        long copied = 0L;
        long broken = 0L;

        for (DatasetItem item : items) {
            increment(countsBySplit, item.getAssignedSplit().name());
            increment(countsBySourceProject, item.getSourceProjectName());
            increment(countsByMode, item.getMode().name());
            for (String tag : defaultList(item.getSourcePageTags())) {
                increment(countsByTag, tag);
            }
            if (item.getMode() == DatasetItem.Mode.LINK) {
                linked++;
            } else {
                copied++;
            }
            if (item.getStatus() == DatasetItem.Status.BROKEN) {
                broken++;
            }
        }

        return new DatasetDto.StatsResponse(
                items.size(),
                linked,
                copied,
                broken,
                countsBySplit,
                countsBySourceProject,
                countsByMode,
                countsByTag
        );
    }

    private void increment(Map<String, Long> counts, String key) {
        counts.merge(key, 1L, Long::sum);
    }

    private void applyDatasetConfiguration(Dataset dataset, DatasetDto.CreateOrUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dataset request is required");
        }
        dataset.setName(request.name().trim());
        dataset.setDescription(request.description());
        dataset.setTags(normalizeStrings(request.tags()));
        dataset.setSplitTemplate(request.splitTemplate() == null ? Dataset.SplitTemplate.TRAIN_VAL_TEST : request.splitTemplate());
        dataset.setSplitAlgorithm(request.splitAlgorithm() == null ? Dataset.SplitAlgorithm.RANDOM_SEEDED : request.splitAlgorithm());
        dataset.setSplitSeed(request.splitSeed() == null ? 42L : request.splitSeed());

        Dataset.SplitTemplate template = dataset.getSplitTemplate();
        int defaultTrain = template == Dataset.SplitTemplate.TRAIN_VAL ? 80 : 70;
        int defaultVal = template == Dataset.SplitTemplate.TRAIN_VAL ? 20 : 15;
        int defaultTest = template == Dataset.SplitTemplate.TRAIN_VAL ? 0 : 15;

        dataset.setTrainPercentage(request.trainPercentage() == null ? defaultTrain : request.trainPercentage());
        dataset.setValPercentage(request.valPercentage() == null ? defaultVal : request.valPercentage());
        dataset.setTestPercentage(request.testPercentage() == null ? defaultTest : request.testPercentage());
        dataset.setStratifyTagIds(normalizeStrings(request.stratifyTagIds()));
        validateSplitConfiguration(dataset);
    }

    private void validateSplitConfiguration(Dataset dataset) {
        int train = defaultInt(dataset.getTrainPercentage());
        int val = defaultInt(dataset.getValPercentage());
        int test = dataset.getSplitTemplate() == Dataset.SplitTemplate.TRAIN_VAL ? 0 : defaultInt(dataset.getTestPercentage());

        if (train < 0 || val < 0 || test < 0) {
            throw new IllegalArgumentException("Split percentages must be non-negative");
        }
        int total = train + val + test;
        if (total != 100) {
            throw new IllegalArgumentException("Split percentages must add up to 100");
        }
        if (dataset.getSplitTemplate() == Dataset.SplitTemplate.TRAIN_VAL) {
            dataset.setTestPercentage(0);
        }
    }

    private void validateDatasetName(String workspaceId, String name, String existingDatasetId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Dataset name is required");
        }
        boolean exists = datasetRepository.findByWorkspaceIdOrderByUpdatedDesc(workspaceId).stream()
                .anyMatch(dataset -> dataset.getName() != null
                        && dataset.getName().equalsIgnoreCase(name.trim())
                        && !Objects.equals(dataset.getId(), existingDatasetId));
        if (exists) {
            throw new IllegalArgumentException("Dataset name already exists in this workspace");
        }
    }

    private Map<DatasetItem.Split, Integer> targetCounts(Dataset.SplitTemplate template,
                                                         int trainPct,
                                                         int valPct,
                                                         int testPct,
                                                         int itemCount) {
        Map<DatasetItem.Split, Integer> counts = new EnumMap<>(DatasetItem.Split.class);
        int train = (int) Math.round(itemCount * (trainPct / 100.0));
        int val = (int) Math.round(itemCount * (valPct / 100.0));
        int assigned = train + val;
        int test = Math.max(0, itemCount - assigned);
        counts.put(DatasetItem.Split.TRAIN, train);
        counts.put(DatasetItem.Split.VAL, val);
        if (template == Dataset.SplitTemplate.TRAIN_VAL_TEST) {
            counts.put(DatasetItem.Split.TEST, test);
        } else {
            counts.put(DatasetItem.Split.TEST, 0);
            if (assigned < itemCount) {
                counts.put(DatasetItem.Split.VAL, counts.get(DatasetItem.Split.VAL) + (itemCount - assigned));
            }
        }
        return counts;
    }

    private List<DatasetItem.Split> allowedSplits(Dataset.SplitTemplate template) {
        if (template == Dataset.SplitTemplate.TRAIN_VAL) {
            return List.of(DatasetItem.Split.TRAIN, DatasetItem.Split.VAL);
        }
        return List.of(DatasetItem.Split.TRAIN, DatasetItem.Split.VAL, DatasetItem.Split.TEST);
    }

    private DatasetItem.Split fallbackSplit(Dataset.SplitTemplate template) {
        return template == Dataset.SplitTemplate.TRAIN_VAL ? DatasetItem.Split.VAL : DatasetItem.Split.TEST;
    }

    private DatasetItem.Split normalizeSplitForTemplate(Dataset.SplitTemplate template, DatasetItem.Split split) {
        if (split == null) {
            return DatasetItem.Split.TRAIN;
        }
        if (template == Dataset.SplitTemplate.TRAIN_VAL && split == DatasetItem.Split.TEST) {
            return DatasetItem.Split.VAL;
        }
        return split;
    }

    private String stratifySignature(DatasetItem item, List<String> stratifyTagIds) {
        Set<String> tags = new LinkedHashSet<>(defaultList(item.getSourcePageTags()));
        List<String> matching = stratifyTagIds.stream()
                .filter(tags::contains)
                .sorted()
                .toList();
        return matching.isEmpty() ? "__UNTAGGED__" : String.join("|", matching);
    }

    private Path datasetRoot(String workspaceId, String datasetId, String itemId) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve("ws")
                .resolve(sanitizeSegment(workspaceId))
                .resolve("ds")
                .resolve(sanitizeSegment(datasetId));
        if (itemId != null) {
            root = root.resolve("items").resolve(sanitizeSegment(itemId));
        }
        return root;
    }

    private Path resolveStoragePath(String relativePath) {
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve(relativePath).normalize();
    }

    private String relativeToUploadRoot(Path absolutePath) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        return root.relativize(absolutePath.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    for (Path child : stream.toList()) {
                        deleteRecursively(child);
                    }
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup; orphaned files are still accounted for by quota refresh.
        }
    }

    private String writeWarnings(List<String> warnings) {
        try {
            return objectMapper.writeValueAsString(defaultList(warnings));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize validation warnings", e);
        }
    }

    private List<String> readWarnings(String warningsJson) {
        if (warningsJson == null || warningsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(warningsJson, new TypeReference<>() {});
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<String> normalizeStrings(List<String> rawValues) {
        if (rawValues == null) {
            return new ArrayList<>();
        }
        return rawValues.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private List<String> defaultList(List<String> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private String sanitizeSegment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String normalizeReleaseTag(String requestedTag, int versionNumber, String datasetId) {
        String candidate = normalizeNullableText(requestedTag);
        if (candidate == null) {
            candidate = "v" + versionNumber;
        }
        String normalized = candidate.trim();
        if (datasetReleaseRepository.findByDatasetIdOrderByVersionNumberDesc(datasetId).stream()
                .anyMatch(release -> release.getVersionTag() != null && release.getVersionTag().equalsIgnoreCase(normalized))) {
            throw new IllegalArgumentException("Release tag already exists in this dataset");
        }
        return normalized;
    }

    private String fileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    private String computeSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private long estimatePackageBytes(ExportSnapshot exportSnapshot) {
        long fileBytes = exportSnapshot.files().stream().mapToLong(file -> {
            try {
                return Files.size(file.absolutePath());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read export file size", e);
            }
        }).sum();
        return fileBytes + 1_048_576L;
    }

    private byte[] createPackageBytes(ExportSnapshot exportSnapshot) throws IOException {
        return archiveIoService.createZip(zipOut -> {
            archiveIoService.writeJsonEntry(zipOut, "manifest.json", exportSnapshot.manifest());
            archiveIoService.writeJsonEntry(zipOut, "stats.json", exportSnapshot.stats());

            for (Map.Entry<DatasetItem.Split, List<Map<String, Object>>> entry : exportSnapshot.jsonlRowsBySplit().entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                String content = entry.getValue().stream()
                        .map(row -> {
                            try {
                                return objectMapper.writeValueAsString(row);
                            } catch (IOException e) {
                                throw new IllegalStateException("Failed to serialize JSONL row", e);
                            }
                        })
                        .collect(Collectors.joining("\n")) + "\n";
                String splitName = entry.getKey().name().toLowerCase(Locale.ROOT);
                archiveIoService.writeBytesEntry(zipOut, "splits/" + splitName + ".jsonl", content.getBytes());
            }

            for (ExportFile exportFile : exportSnapshot.files()) {
                archiveIoService.writeFileEntry(zipOut, exportFile.archivePath(), exportFile.absolutePath());
            }
        });
    }

    private Path datasetReleaseRoot(String workspaceId, String datasetId, String releaseId) {
        return datasetRoot(workspaceId, datasetId, null)
                .resolve("releases")
                .resolve(sanitizeSegment(releaseId));
    }

    private record PendingItem(Page page, PageXml xml, List<PageImage> images, DatasetItem.Mode mode) {
    }

    private record ValidationSnapshot(Dataset.ValidationStatus status,
                                      DatasetDto.StatsResponse stats,
                                      List<String> warnings,
                                      List<DatasetDto.ValidationIssue> issues) {
    }

    private record ResolvedFile(String fileName, Path absolutePath, String checksumSha256, LocalDateTime sourceUpdatedAt) {
    }

    private record ExportMaterial(ResolvedFile xml, List<ResolvedFile> images, LocalDateTime xmlSourceUpdatedAt) {
    }

    private record ExportFile(String archivePath, Path absolutePath) {
    }

    private record ExportSnapshot(Map<String, Object> manifest,
                                  DatasetDto.StatsResponse stats,
                                  Map<DatasetItem.Split, List<Map<String, Object>>> jsonlRowsBySplit,
                                  List<ExportFile> files) {
    }

    public record ReleaseDownload(String fileName, byte[] bytes) {
    }
}
