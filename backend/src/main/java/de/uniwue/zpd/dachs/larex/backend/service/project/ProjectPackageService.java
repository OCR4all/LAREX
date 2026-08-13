package de.uniwue.zpd.dachs.larex.backend.service.project;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.config.ProjectPackageProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ToolkitPackageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectPackageRelease;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset;
import de.uniwue.zpd.dachs.larex.backend.entity.VirtualKeyboard;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.keyboard.VirtualKeyboardRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlVersionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectPackageReleaseRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.export.DocumentExportService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.StorageTrackingService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.toolkit.ToolkitPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PreDestroy;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Service
@Transactional
public class ProjectPackageService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long PREVIEW_CACHE_WEIGHT_UNIT_BYTES = 1024L;

    private final ProjectRepository projectRepository;
    private final ProjectPackageReleaseRepository projectPackageReleaseRepository;
    private final LibraryRepository libraryRepository;
    private final PageRepository pageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final PageXmlVersionRepository pageXmlVersionRepository;
    private final CodecRepository codecRepository;
    private final ControlledDictionaryRepository dictionaryRepository;
    private final LabelSetRepository labelSetRepository;
    private final TagSetRepository tagSetRepository;
    private final NormalizationProfileRepository normalizationProfileRepository;
    private final ValidationRulesetRepository validationRulesetRepository;
    private final VirtualKeyboardRepository virtualKeyboardRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ArchiveIoService archiveIoService;
    private final ProjectPackageArchiveService projectPackageArchiveService;
    private final ToolkitPackageService toolkitPackageService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final PageOrderService pageOrderService;
    private final PageFilterIndexService pageFilterIndexService;
    private final StorageTrackingService storageTrackingService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final PageXmlConversionService pageXmlConversionService;
    private final PageXmlCanonicalizationService pageXmlCanonicalizationService;
    private final DocumentExportService documentExportService;
    private final ObjectMapper objectMapper;
    private final Cache<String, PackagePreviewSession> importPreviewCache;
    private final long maxPreviewCacheBytes;
    private final long maxPreviewCacheWeight;
    private final int minimumPreviewCacheWeight;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${larex.project-releases.share-public-base-url}")
    private String projectReleaseSharePublicBaseUrl;

    public ProjectPackageService(ProjectRepository projectRepository,
                                 ProjectPackageReleaseRepository projectPackageReleaseRepository,
                                 LibraryRepository libraryRepository,
                                 PageRepository pageRepository,
                                 PageXmlRepository pageXmlRepository,
                                 PageXmlVersionRepository pageXmlVersionRepository,
                                 CodecRepository codecRepository,
                                 ControlledDictionaryRepository dictionaryRepository,
                                 LabelSetRepository labelSetRepository,
                                 TagSetRepository tagSetRepository,
                                 NormalizationProfileRepository normalizationProfileRepository,
                                 ValidationRulesetRepository validationRulesetRepository,
                                 VirtualKeyboardRepository virtualKeyboardRepository,
                                 WorkspaceAccessService workspaceAccessService,
                                 ArchiveIoService archiveIoService,
                                 ProjectPackageArchiveService projectPackageArchiveService,
                                 ToolkitPackageService toolkitPackageService,
                                 HierarchicalFileStorageService hierarchicalFileStorageService,
                                 PageOrderService pageOrderService,
                                 PageFilterIndexService pageFilterIndexService,
                                 StorageTrackingService storageTrackingService,
                                 WorkspaceQuotaGuardService workspaceQuotaGuardService,
                                 PageXmlConversionService pageXmlConversionService,
                                 PageXmlCanonicalizationService pageXmlCanonicalizationService,
                                 DocumentExportService documentExportService,
                                 ObjectMapper objectMapper,
                                 ProjectPackageProperties projectPackageProperties) {
        this.projectRepository = projectRepository;
        this.projectPackageReleaseRepository = projectPackageReleaseRepository;
        this.libraryRepository = libraryRepository;
        this.pageRepository = pageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.pageXmlVersionRepository = pageXmlVersionRepository;
        this.codecRepository = codecRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.labelSetRepository = labelSetRepository;
        this.tagSetRepository = tagSetRepository;
        this.normalizationProfileRepository = normalizationProfileRepository;
        this.validationRulesetRepository = validationRulesetRepository;
        this.virtualKeyboardRepository = virtualKeyboardRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.archiveIoService = archiveIoService;
        this.projectPackageArchiveService = projectPackageArchiveService;
        this.toolkitPackageService = toolkitPackageService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.pageOrderService = pageOrderService;
        this.pageFilterIndexService = pageFilterIndexService;
        this.storageTrackingService = storageTrackingService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.pageXmlConversionService = pageXmlConversionService;
        this.pageXmlCanonicalizationService = pageXmlCanonicalizationService;
        this.documentExportService = documentExportService;
        this.objectMapper = objectMapper;
        ProjectPackageProperties.Preview previewProperties = projectPackageProperties.getPreview();
        this.maxPreviewCacheBytes = previewProperties.getMaxCachedBytes();
        long maxCacheWeight = Math.max(
                1L,
                maxPreviewCacheBytes / PREVIEW_CACHE_WEIGHT_UNIT_BYTES
        );
        this.maxPreviewCacheWeight = maxCacheWeight;
        this.minimumPreviewCacheWeight = clampCacheWeight(
                divideRoundingUp(maxCacheWeight, previewProperties.getMaxSessions())
        );
        this.importPreviewCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(previewProperties.getExpireAfterMinutes()))
                .weigher((String token, PackagePreviewSession session) -> session.cacheWeight())
                .maximumWeight(maxCacheWeight)
                .executor(Runnable::run)
                .removalListener((String token, PackagePreviewSession session, RemovalCause cause) -> {
                    if (session != null) {
                        session.onRemoval();
                    }
                })
                .build();
    }

    @PreDestroy
    void closePendingImportPreviews() {
        importPreviewCache.invalidateAll();
        importPreviewCache.cleanUp();
    }

    @Transactional(readOnly = true)
    public void writeProjectPackage(String workspaceId,
                                    String projectId,
                                    String userId,
                                    ProjectPackageDto.ExportRequest request,
                                    OutputStream outputStream) throws IOException {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        writeProjectPackageInternal(workspaceId, projectId, request, outputStream);
    }

    @Transactional(readOnly = true)
    public void writeProjectPackageInternal(String workspaceId,
                                            String projectId,
                                            ProjectPackageDto.ExportRequest request,
                                            OutputStream outputStream) throws IOException {
        Project project = requireProject(workspaceId, projectId);
        List<Page> pages = resolvePagesForExport(projectId, request == null ? null : request.pageIds());
        PackageSnapshot packageSnapshot = buildPackageSnapshot(
                project,
                pages,
                request == null ? null : request.targetPageXmlVersion(),
                request == null ? null : request.embeddedOutputs(),
                request != null && request.includeXmlHistoryResolved()
        );
        try {
            writePackageZip(outputStream, packageSnapshot);
        } finally {
            cleanupEmbeddedOutputs(packageSnapshot);
        }
    }

    @Transactional(readOnly = true)
    public void writeProjectPackageInternalUncompressed(String workspaceId,
                                                        String projectId,
                                                        ProjectPackageDto.ExportRequest request,
                                                        OutputStream outputStream) throws IOException {
        Project project = requireProject(workspaceId, projectId);
        List<Page> pages = resolvePagesForExport(projectId, request == null ? null : request.pageIds());
        PackageSnapshot packageSnapshot = buildPackageSnapshot(
                project,
                pages,
                request == null ? null : request.targetPageXmlVersion(),
                request == null ? null : request.embeddedOutputs(),
                request != null && request.includeXmlHistoryResolved()
        );
        try {
            projectPackageArchiveService.writeUncompressedZip(outputStream, packageSnapshot.exportPackage());
        } finally {
            cleanupEmbeddedOutputs(packageSnapshot);
        }
    }

    @Transactional(readOnly = true)
    public void writeProjectPackageEntries(String workspaceId,
                                           String projectId,
                                           String userId,
                                           ProjectPackageDto.ExportRequest request,
                                           java.util.zip.ZipOutputStream zipOut,
                                           String entryPrefix) throws IOException {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        Project project = requireProject(workspaceId, projectId);
        List<Page> pages = resolvePagesForExport(projectId, request == null ? null : request.pageIds());
        PackageSnapshot packageSnapshot = buildPackageSnapshot(
                project,
                pages,
                request == null ? null : request.targetPageXmlVersion(),
                request == null ? null : request.embeddedOutputs(),
                request != null && request.includeXmlHistoryResolved()
        );
        try {
            writePackageEntries(zipOut, packageSnapshot, entryPrefix);
        } finally {
            cleanupEmbeddedOutputs(packageSnapshot);
        }
    }

    @Transactional(readOnly = true)
    public void writeBasicProjectExport(String workspaceId,
                                        String projectId,
                                        String userId,
                                        ProjectPackageDto.ExportRequest request,
                                        OutputStream outputStream) throws IOException {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        writeBasicProjectExportInternal(workspaceId, projectId, request, outputStream);
    }

    @Transactional(readOnly = true)
    public void writeBasicProjectExportInternal(String workspaceId,
                                                String projectId,
                                                ProjectPackageDto.ExportRequest request,
                                                OutputStream outputStream) throws IOException {
        Project project = requireProject(workspaceId, projectId);
        List<Page> pages = resolvePagesForExport(projectId, request == null ? null : request.pageIds());
        String targetPageXmlVersion = pageXmlConversionService.normalizeTargetVersion(
                request == null ? null : request.targetPageXmlVersion()
        );
        List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputRequests =
                request == null ? null : request.embeddedOutputs();
        List<DocumentExportService.EmbeddedProjectOutput> embeddedOutputs =
                embeddedOutputRequests == null || embeddedOutputRequests.isEmpty()
                        ? List.of()
                        : documentExportService.exportEmbeddedProjectOutputs(project, pages, embeddedOutputRequests);

        try {
            archiveIoService.writeZip(outputStream, zipOut -> writeBasicProjectExportEntries(
                    zipOut,
                    pages,
                    targetPageXmlVersion,
                    embeddedOutputs
            ));
        } finally {
            cleanupEmbeddedOutputs(embeddedOutputs);
        }
    }

    @Transactional(readOnly = true)
    public void writeBasicProjectExportEntries(String workspaceId,
                                               String projectId,
                                               String userId,
                                               ProjectPackageDto.ExportRequest request,
                                               java.util.zip.ZipOutputStream zipOut,
                                               String entryPrefix) throws IOException {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        Project project = requireProject(workspaceId, projectId);
        List<Page> pages = resolvePagesForExport(projectId, request == null ? null : request.pageIds());
        String targetPageXmlVersion = pageXmlConversionService.normalizeTargetVersion(
                request == null ? null : request.targetPageXmlVersion()
        );
        List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputRequests =
                request == null ? null : request.embeddedOutputs();
        List<DocumentExportService.EmbeddedProjectOutput> embeddedOutputs =
                embeddedOutputRequests == null || embeddedOutputRequests.isEmpty()
                        ? List.of()
                        : documentExportService.exportEmbeddedProjectOutputs(project, pages, embeddedOutputRequests);

        try {
            writeBasicProjectExportEntries(zipOut, pages, targetPageXmlVersion, embeddedOutputs, entryPrefix);
        } finally {
            cleanupEmbeddedOutputs(embeddedOutputs);
        }
    }

    public List<ProjectPackageDto.ReleaseSummaryResponse> listReleases(String workspaceId,
                                                                       String projectId,
                                                                       String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        requireProject(workspaceId, projectId);
        return projectPackageReleaseRepository.findByProjectIdOrderByVersionNumberDesc(projectId).stream()
                .map(this::toReleaseSummaryResponse)
                .toList();
    }

    public ProjectPackageDto.ReleaseSummaryResponse createRelease(String workspaceId,
                                                                  String projectId,
                                                                  ProjectPackageDto.CreateReleaseRequest request,
                                                                  String userId) throws IOException {
        workspaceAccessService.requireManageProjectReleasesAndSharesAccess(workspaceId, userId);
        Project project = requireProject(workspaceId, projectId);

        int nextVersionNumber = defaultInt(projectPackageReleaseRepository.findMaxVersionNumberByProjectId(projectId)) + 1;
        String versionTag = normalizeReleaseTag(request == null ? null : request.versionTag(), nextVersionNumber, projectId);
        String targetPageXmlVersion = pageXmlConversionService.normalizeTargetVersion(
                request == null ? null : request.targetPageXmlVersion()
        );
        List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputs = copyEmbeddedOutputs(
                request == null ? null : request.embeddedOutputs()
        );
        boolean includeXmlHistory = request == null || request.includeXmlHistoryResolved();
        List<Page> pages = resolvePagesForExport(projectId, null);

        ProjectPackageRelease release = new ProjectPackageRelease();
        release.setProject(project);
        release.setVersionNumber(nextVersionNumber);
        release.setVersionTag(versionTag);
        release.setNotes(normalizeNullableText(request == null ? null : request.notes()));
        release.setCreatedByUserId(userId);
        release.setStatus(ProjectPackageRelease.Status.CREATING);
        release.setPageCount((long) pages.size());
        release.setTargetPageXmlVersion(targetPageXmlVersion);
        release.setIncludeXmlHistory(includeXmlHistory);
        release.setEmbeddedOutputsJson(writeEmbeddedOutputsJson(embeddedOutputs));
        release.setSourceProjectUpdatedAt(project.getUpdated());
        release = projectPackageReleaseRepository.save(release);

        Path releaseRoot = projectReleaseRoot(workspaceId, projectId, release.getId());
        long reservedBytes = 0L;

        PackageSnapshot packageSnapshot = null;
        try {
            packageSnapshot = buildPackageSnapshot(project, pages, targetPageXmlVersion, embeddedOutputs, includeXmlHistory);
            String fileName = sanitizeSegment(project.getName()) + "-" + sanitizeSegment(versionTag) + ".larex-project.zip";
            Path packagePath = releaseRoot.resolve(fileName);
            reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    workspaceId,
                    estimatePackageBytes(packageSnapshot),
                    "project-release"
            );

            writePackageZip(packagePath, packageSnapshot);

            release.setStatus(ProjectPackageRelease.Status.READY);
            release.setFailureReason(null);
            release.setPackageFileName(fileName);
            release.setPackageFilePath(relativeToUploadRoot(packagePath));
                release.setPackageFileSize(Files.size(packagePath));
                release.setPackageChecksumSha256(computeSha256(packagePath));
                release.setManifestChecksumSha256(computeSha256(objectMapper.writeValueAsBytes(
                        packageSnapshot.exportPackage().manifest()
                )));
            projectPackageReleaseRepository.save(release);
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
            return toReleaseSummaryResponse(release);
        } catch (IOException | RuntimeException e) {
            deleteRecursively(releaseRoot);
            projectPackageReleaseRepository.deleteById(release.getId());
            if (reservedBytes > 0) {
                workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
            }
            throw e;
        } finally {
            cleanupEmbeddedOutputs(packageSnapshot);
        }
    }

    public ProjectPackageDto.ReleaseShareResponse createOrRotateReleaseShare(String workspaceId,
                                                                             String projectId,
                                                                             String releaseId,
                                                                             ProjectPackageDto.UpsertReleaseShareRequest request,
                                                                             String userId) {
        workspaceAccessService.requireManageProjectReleasesAndSharesAccess(workspaceId, userId);
        requireProject(workspaceId, projectId);
        ProjectPackageRelease release = requireRelease(projectId, releaseId);
        requireShareableRelease(release);

        LocalDateTime now = LocalDateTime.now();
        String sharePublicId = generateOpaqueToken(18);
        String shareSecret = generateOpaqueToken(32);

        release.setSharePublicId(sharePublicId);
        release.setShareSecretHash(computeSha256(shareSecret.getBytes(StandardCharsets.UTF_8)));
        release.setShareSecretPrefix(shareSecret.substring(0, Math.min(8, shareSecret.length())));
        release.setShareCreatedByUserId(userId);
        release.setShareCreatedAt(now);
        release.setShareExpiresAt(request.expiresAt());
        release.setShareRevokedAt(null);
        release.setShareLastUsedAt(null);
        release.setShareDownloadCount(0L);
        projectPackageReleaseRepository.save(release);

        return new ProjectPackageDto.ReleaseShareResponse(
                buildShareDownloadUrl(sharePublicId),
                shareSecret,
                release.getShareExpiresAt(),
                release.getShareCreatedAt()
        );
    }

    public ProjectPackageDto.ReleaseSummaryResponse updateReleaseShare(String workspaceId,
                                                                       String projectId,
                                                                       String releaseId,
                                                                       ProjectPackageDto.UpdateReleaseShareRequest request,
                                                                       String userId) {
        workspaceAccessService.requireManageProjectReleasesAndSharesAccess(workspaceId, userId);
        requireProject(workspaceId, projectId);
        ProjectPackageRelease release = requireRelease(projectId, releaseId);
        requireActiveShare(release);
        release.setShareExpiresAt(request.expiresAt());
        projectPackageReleaseRepository.save(release);
        return toReleaseSummaryResponse(release);
    }

    public ProjectPackageDto.ReleaseSummaryResponse revokeReleaseShare(String workspaceId,
                                                                       String projectId,
                                                                       String releaseId,
                                                                       String userId) {
        workspaceAccessService.requireManageProjectReleasesAndSharesAccess(workspaceId, userId);
        requireProject(workspaceId, projectId);
        ProjectPackageRelease release = requireRelease(projectId, releaseId);
        requireActiveShare(release);
        release.setShareRevokedAt(LocalDateTime.now());
        projectPackageReleaseRepository.save(release);
        return toReleaseSummaryResponse(release);
    }

    public ReleaseFileDownload downloadReleasePackage(String workspaceId,
                                                      String projectId,
                                                      String releaseId,
                                                      String userId) throws IOException {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        requireProject(workspaceId, projectId);
        ProjectPackageRelease release = requireRelease(projectId, releaseId);
        return resolveReleaseFileDownload(release, releaseId);
    }

    public SharedReleaseDownload downloadSharedReleasePackage(String sharePublicId,
                                                              String authorizationHeader,
                                                              boolean trackUsage) throws IOException {
        ProjectPackageRelease release = projectPackageReleaseRepository.findBySharePublicId(sharePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Project release package not found."));
        requireActiveShare(release);
        if (!matchesShareSecret(release.getShareSecretHash(), extractBearerToken(authorizationHeader))) {
            throw new ResourceNotFoundException("Project release package not found.");
        }

        ReleaseFileDownload download = resolveReleaseFileDownload(release, release.getId());
        if (trackUsage) {
            release.setShareLastUsedAt(LocalDateTime.now());
            release.setShareDownloadCount((release.getShareDownloadCount() == null ? 0L : release.getShareDownloadCount()) + 1L);
            projectPackageReleaseRepository.save(release);
        }

        return new SharedReleaseDownload(
                download.fileName(),
                download.absolutePath(),
                download.contentLength(),
                download.checksumSha256()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectPackageDto.ImportResult importProjectPackage(String workspaceId,
                                                               String userId,
                                                               MultipartFile packageFile) throws IOException {
        workspaceAccessService.requireAdminAccess(workspaceId, userId);

        if (packageFile == null || packageFile.isEmpty()) {
            throw new IllegalArgumentException("Project package file is required");
        }

        try (InputStream inputStream = packageFile.getInputStream()) {
            return importProjectPackageArchive(workspaceId, userId, inputStream);
        }
    }

    @Transactional(readOnly = true)
    public ProjectPackageDto.ImportPreview previewProjectPackage(String workspaceId,
                                                                 String userId,
                                                                 MultipartFile packageFile) throws IOException {
        workspaceAccessService.requireAdminAccess(workspaceId, userId);
        if (packageFile == null || packageFile.isEmpty()) {
            throw new IllegalArgumentException("Project package file is required");
        }

        ProjectPackageArchiveService.ImportedPackage importedPackage;
        try (InputStream inputStream = packageFile.getInputStream()) {
            importedPackage = projectPackageArchiveService.extractAndValidate(inputStream);
        }

        try {
            validateSingleXmlHeadPerPage(importedPackage);
            if (importedPackage.extractedBytes() > maxPreviewCacheBytes) {
                throw new IllegalArgumentException(
                        "Project package exceeds the pending preview storage limit of "
                                + maxPreviewCacheBytes + " bytes"
                );
            }
            ProjectPackageDto.PackageManifest manifest = importedPackage.manifest();
            Library library = libraryRepository.findByWorkspaceId(workspaceId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Projects not found for workspace: " + workspaceId
                    ));
            String projectName = normalizedProjectName(manifest.project().name());
            String existingProjectId = projectRepository.findByNameAndLibraryId(projectName, library.getId())
                    .map(Project::getId)
                    .orElse(null);
            List<ToolkitPackageDto.ResourcePreview> resourcePreviews =
                    sourceToolkitResources(importedPackage).stream()
                            .map(resource -> toolkitPackageService.previewToolkitResource(workspaceId, resource))
                            .toList();
            int imageCount = importedPackage.pages().stream()
                    .mapToInt(page -> safeList(page.descriptor().images()).size())
                    .sum();
            int xmlCount = importedPackage.pages().stream()
                    .mapToInt(page -> safeList(page.descriptor().xml()).size())
                    .sum();
            int xmlVersionCount = importedPackage.pages().stream()
                    .flatMap(page -> safeList(page.descriptor().xml()).stream())
                    .mapToInt(xml -> safeList(xml.history()).size())
                    .sum();

            String previewToken = UUID.randomUUID().toString();
            int cacheWeight = previewCacheWeight(importedPackage.extractedBytes());
            if (cacheWeight > maxPreviewCacheWeight) {
                throw new IllegalArgumentException(
                        "Project package exceeds the pending preview cache capacity"
                );
            }
            PackagePreviewSession previewSession = new PackagePreviewSession(
                    workspaceId,
                    userId,
                    importedPackage,
                    cacheWeight
            );
            importPreviewCache.put(
                    previewToken,
                    previewSession
            );
            if (importPreviewCache.getIfPresent(previewToken) != previewSession) {
                throw new IllegalArgumentException(
                        "Pending project package preview capacity is currently exhausted"
                );
            }
            return new ProjectPackageDto.ImportPreview(
                    previewToken,
                    projectName,
                    manifest.project().description(),
                    existingProjectId,
                    existingProjectId == null
                            ? projectName
                            : suggestedRenamedProjectName(projectName, library.getId()),
                    importedPackage.pages().stream()
                            .map(page -> page.descriptor().name())
                            .toList(),
                    imageCount,
                    xmlCount,
                    xmlVersionCount,
                    manifest.includesXmlHistory(),
                    resourcePreviews,
                    List.copyOf(safeList(manifest.warnings()))
            );
        } catch (RuntimeException exception) {
            importedPackage.close();
            throw exception;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectPackageDto.ImportResult importPreviewedProjectPackage(
            String workspaceId,
            String userId,
            ProjectPackageDto.ImportOptions options) throws IOException {
        workspaceAccessService.requireAdminAccess(workspaceId, userId);
        if (options == null || options.previewToken() == null || options.previewToken().isBlank()) {
            throw new IllegalArgumentException("Project package preview token is required");
        }

        PackagePreviewSession session = Optional.ofNullable(
                importPreviewCache.getIfPresent(options.previewToken())
        ).orElseThrow(() -> new IllegalArgumentException(
                "Project package preview has expired. Select the package again."
        ));
        if (!workspaceId.equals(session.workspaceId()) || !userId.equals(session.userId())) {
            throw new IllegalArgumentException("Project package preview does not match this workspace or user");
        }
        if (!session.claim()) {
            throw new IllegalArgumentException(
                    "Project package preview has expired or is already being imported"
            );
        }

        try {
            return importValidatedProjectPackage(workspaceId, userId, session.importedPackage(), options);
        } finally {
            importPreviewCache.invalidate(options.previewToken());
            session.finish();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectPackageDto.ImportResult importProjectPackageFromPathInternal(String workspaceId,
                                                                               String userId,
                                                                               Path packagePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(packagePath)) {
            return importProjectPackageArchive(workspaceId, userId, inputStream);
        }
    }

    private ProjectPackageDto.ImportResult importProjectPackageArchive(String workspaceId,
                                                                       String userId,
                                                                       InputStream packageStream) throws IOException {
        try (ProjectPackageArchiveService.ImportedPackage importedPackage =
                     projectPackageArchiveService.extractAndValidate(packageStream)) {
            return importValidatedProjectPackage(workspaceId, userId, importedPackage, null);
        }
    }

    private ProjectPackageDto.ImportResult importValidatedProjectPackage(
            String workspaceId,
            String userId,
            ProjectPackageArchiveService.ImportedPackage importedPackage,
            ProjectPackageDto.ImportOptions requestedOptions) throws IOException {
        validateSingleXmlHeadPerPage(importedPackage);
        ProjectPackageDto.ImportOptions options = requestedOptions == null
                ? new ProjectPackageDto.ImportOptions(
                        null,
                        ProjectPackageDto.ProjectImportAction.AUTO,
                        null,
                        true,
                        Map.of()
                )
                : requestedOptions;
        ProjectPackageDto.PackageManifest manifest = importedPackage.manifest();
        String sourceProjectName = normalizedProjectName(manifest.project().name());
        Library library = libraryRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Projects not found for workspace: " + workspaceId
                ));
        Optional<Project> existingProject =
                projectRepository.findByNameAndLibraryId(sourceProjectName, library.getId());
        if (options.projectActionResolved() == ProjectPackageDto.ProjectImportAction.SKIP) {
            return new ProjectPackageDto.ImportResult(
                    workspaceId,
                    null,
                    sourceProjectName,
                    0,
                    0,
                    0,
                    0,
                    List.of("Project import skipped by user"),
                    Map.of()
            );
        }

        boolean replaceExisting = options.projectActionResolved() == ProjectPackageDto.ProjectImportAction.REPLACE
                && existingProject.isPresent();
        String projectName;
        if (replaceExisting) {
            projectName = uniqueProjectName(sourceProjectName + " replacement", library.getId());
        } else if (options.projectActionResolved() == ProjectPackageDto.ProjectImportAction.RENAME) {
            projectName = validateRenamedProjectName(options.renamedProjectName(), library.getId());
        } else {
            projectName = uniqueProjectName(sourceProjectName, library.getId());
        }
        long reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                workspaceId,
                estimatePersistentImportBytes(importedPackage),
                "project-package-import"
        );
        List<String> createdStoragePaths = new ArrayList<>();

        try {
            ToolkitImport toolkitImport =
                    importToolkitResources(workspaceId, userId, importedPackage, options);

            Project project = new Project();
            project.setLibrary(library);
            project.setName(projectName);
            project.setDescription(manifest.project().description());
            project.setTags(new ArrayList<>(manifest.project().tags() == null ? List.of() : manifest.project().tags()));
            project.setLocked(manifest.project().locked());
            project.setLockedReason(manifest.project().lockedReason());
            applyProjectSettings(project, manifest.project());
            applyToolkitReferences(project, toolkitImport.targetIds());
            project = projectRepository.save(project);

            ImportCounts counts = importPagesAndFiles(
                    importedPackage,
                    project,
                    userId,
                    createdStoragePaths
            );
            if (replaceExisting) {
                replaceExistingProject(
                        workspaceId,
                        existingProject.orElseThrow(),
                        project,
                        sourceProjectName
                );
            }

            pageFilterIndexService.rebuildProjectIndex(project.getId());

            List<String> warnings = new ArrayList<>(safeList(manifest.warnings()));
            warnings.addAll(toolkitImport.warnings());
            return new ProjectPackageDto.ImportResult(
                    workspaceId,
                    project.getId(),
                    project.getName(),
                    counts.pages(),
                    counts.images(),
                    counts.xml(),
                    counts.xmlVersions(),
                    List.copyOf(warnings),
                    toolkitImport.targetIds()
            );
        } catch (IOException | RuntimeException exception) {
            hierarchicalFileStorageService.deleteStoredFiles(createdStoragePaths);
            throw exception;
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
        }
    }

    private long estimatePersistentImportBytes(ProjectPackageArchiveService.ImportedPackage importedPackage) throws IOException {
        long totalBytes = 0L;
        for (ProjectPackageArchiveService.ImportedPage page : importedPackage.pages()) {
            for (ProjectPackageDto.FileDescriptor image : safeList(page.descriptor().images())) {
                totalBytes += Files.size(page.resolve(importedPackage.root(), image.path()));
            }
            for (ProjectPackageDto.XmlFileDescriptor xml : safeList(page.descriptor().xml())) {
                totalBytes += Files.size(page.resolve(importedPackage.root(), xml.path()));
                for (ProjectPackageDto.XmlVersionDescriptor version : safeList(xml.history())) {
                    totalBytes += Files.size(page.resolve(importedPackage.root(), version.path()));
                }
            }
        }
        return totalBytes;
    }

    private ToolkitImport importToolkitResources(
            String workspaceId,
            String userId,
            ProjectPackageArchiveService.ImportedPackage importedPackage,
            ProjectPackageDto.ImportOptions options) {
        if (importedPackage.resources().isEmpty()) {
            return new ToolkitImport(Map.of(), List.of());
        }

        List<ToolkitPackageDto.ToolkitResource> resources = sourceToolkitResources(importedPackage);
        Map<ToolkitPackageDto.ToolkitType, ToolkitPackageDto.ImportAction> actions = new LinkedHashMap<>();
        for (ToolkitPackageDto.ToolkitResource resource : resources) {
            actions.put(resource.type(), options.resourceAction(resource.type()));
        }
        ToolkitPackageDto.ImportResult result = toolkitPackageService.importToolkitPackage(
                workspaceId,
                userId,
                new ToolkitPackageDto.ToolkitPackage(
                        new ToolkitPackageDto.PackageMeta("1.0", LocalDateTime.now(), workspaceId, null),
                        resources
                ),
                actions
        );

        Map<ToolkitPackageDto.ToolkitType, String> targetIds = new LinkedHashMap<>();
        for (ToolkitPackageDto.ToolkitType type : importedPackage.resources().keySet()) {
            String targetId = result.sourceToTargetIds().get(type.name());
            if (targetId != null) {
                targetIds.put(type, targetId);
            }
        }
        List<String> warnings = new ArrayList<>(safeList(result.warnings()));
        result.resources().stream()
                .filter(resource -> "RENAMED_IMPORTED".equals(resource.action())
                        || "REPLACED".equals(resource.action())
                        || "SKIPPED".equals(resource.action()))
                .map(resource -> resource.type().name().replace('_', ' ') + " \""
                        + resource.sourceName() + "\": " + resource.reason())
                .forEach(warnings::add);
        return new ToolkitImport(Map.copyOf(targetIds), List.copyOf(warnings));
    }

    private List<ToolkitPackageDto.ToolkitResource> sourceToolkitResources(
            ProjectPackageArchiveService.ImportedPackage importedPackage) {
        return importedPackage.resources().entrySet().stream()
                .map(entry -> new ToolkitPackageDto.ToolkitResource(
                        entry.getKey(),
                        entry.getKey().name(),
                        entry.getValue().descriptor().name(),
                        null,
                        null,
                        entry.getValue().descriptor().payload()
                ))
                .toList();
    }

    private PackageSnapshot buildPackageSnapshot(Project project,
                                                 List<Page> pages,
                                                 String requestedTargetPageXmlVersion,
                                                 List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputRequests,
                                                 boolean includeXmlHistory) throws IOException {
        String targetPageXmlVersion = pageXmlConversionService.normalizeTargetVersion(requestedTargetPageXmlVersion);
        boolean legacyTarget = pageXmlConversionService.isLegacyTargetVersion(targetPageXmlVersion);
        ProjectPackageArchiveService.ExportPackage exportPackage =
                buildExportPackage(project, pages, targetPageXmlVersion, legacyTarget, includeXmlHistory);
        List<DocumentExportService.EmbeddedProjectOutput> embeddedOutputs = documentExportService.exportEmbeddedProjectOutputs(
                project,
                pages,
                embeddedOutputRequests
        );
        List<ProjectPackageArchiveService.BinaryEntry> entries = new ArrayList<>(exportPackage.binaryEntries());
        for (DocumentExportService.EmbeddedProjectOutput output : embeddedOutputs) {
            entries.add(new ProjectPackageArchiveService.BinaryEntry(
                    output.archivePath(),
                    output.contentLength(),
                    stream -> Files.copy(output.absolutePath(), stream)
            ));
        }
        ProjectPackageArchiveService.ExportPackage withOutputs = new ProjectPackageArchiveService.ExportPackage(
                exportPackage.manifest(),
                exportPackage.pages(),
                exportPackage.resources(),
                List.copyOf(entries)
        );
        return new PackageSnapshot(withOutputs, embeddedOutputs);
    }

    private ProjectPackageArchiveService.ExportPackage buildExportPackage(
            Project project,
            List<Page> pages,
            String targetPageXmlVersion,
            boolean legacyTarget,
            boolean includeXmlHistory) throws IOException {
        Map<String, ProjectPackageDto.PageDescriptor> pageDescriptors = new LinkedHashMap<>();
        List<String> pagePaths = new ArrayList<>();
        List<ProjectPackageArchiveService.BinaryEntry> binaryEntries = new ArrayList<>();
        Set<String> usedPageDirectories = new HashSet<>();
        Map<String, PageXml> xmlHeadsByPageId = pageXmlHeadsByPageId(pages);

        for (Page page : pages) {
            String directoryName = uniqueDirectoryName(
                    sanitizeSegment(page.getName()),
                    usedPageDirectories
            );
            String pageDirectory = "pages/" + directoryName;
            String descriptorPath = pageDirectory + "/page.json";
            pagePaths.add(descriptorPath);

            List<ProjectPackageDto.FileDescriptor> images = new ArrayList<>();
            List<ProjectPackageDto.XmlFileDescriptor> xmlDescriptors = new ArrayList<>();
            Map<String, Integer> usedImageNames = new HashMap<>();
            Map<String, Integer> usedXmlNames = new HashMap<>();
            Map<String, Integer> usedHistoryDirectories = new HashMap<>();

            List<PageImage> pageImages = new ArrayList<>(page.getImages() == null ? Set.of() : page.getImages());
            pageImages.sort(Comparator.comparing(PageImage::getVariant).thenComparing(PageImage::getFileName));
            for (PageImage image : pageImages) {
                String archiveName = uniqueArchivePath(
                        sanitizeArchiveName(image.getFileName(), image.getVariant() + fileExtension(image.getFileName())),
                        usedImageNames
                );
                String relativePath = "images/" + archiveName;
                String archivePath = pageDirectory + "/" + relativePath;
                images.add(new ProjectPackageDto.FileDescriptor(
                        relativePath,
                        image.getFileName(),
                        image.getVariant(),
                        image.getBaseName()
                ));
                Path source = hierarchicalFileStorageService.resolveUploadPath(image.getFilePath());
                binaryEntries.add(new ProjectPackageArchiveService.BinaryEntry(
                        archivePath,
                        Files.size(source),
                        out -> Files.copy(source, out)
                ));
            }

            List<PageXml> pageXmlFiles = Optional.ofNullable(xmlHeadsByPageId.get(page.getId())).stream().toList();
            for (PageXml xml : pageXmlFiles) {
                String archiveName = uniqueArchivePath(
                        sanitizeArchiveName(xml.getFileName(), xml.getVariant() + fileExtension(xml.getFileName())),
                        usedXmlNames
                );
                String relativePath = "xml/" + archiveName;
                String archivePath = pageDirectory + "/" + relativePath;
                Path source = hierarchicalFileStorageService.resolveUploadPath(xml.getFilePath());
                if (xml.getSchema() == XmlSchema.PAGE_XML) {
                    binaryEntries.add(new ProjectPackageArchiveService.BinaryEntry(
                            archivePath,
                            Files.size(source),
                            out -> pageXmlConversionService.writeFileToVersion(source, targetPageXmlVersion, out)
                    ));
                } else {
                    binaryEntries.add(new ProjectPackageArchiveService.BinaryEntry(
                            archivePath,
                            Files.size(source),
                            out -> Files.copy(source, out)
                    ));
                }

                List<ProjectPackageDto.XmlVersionDescriptor> history = new ArrayList<>();
                if (includeXmlHistory) {
                    String historyDirectory = uniqueArchivePath(
                            sanitizeSegment(stripExtension(archiveName)),
                            usedHistoryDirectories
                    );
                    List<PageXmlVersion> versions =
                            pageXmlVersionRepository.findByPageXml_IdOrderByVersionNumberDesc(xml.getId()).stream()
                                    .sorted(Comparator.comparing(PageXmlVersion::getVersionNumber))
                                    .toList();
                    for (PageXmlVersion version : versions) {
                        String historyRelativePath = "history/" + historyDirectory + "/"
                                + String.format(Locale.ROOT, "%06d.xml", version.getVersionNumber());
                        String historyArchivePath = pageDirectory + "/" + historyRelativePath;
                        Path historySource = hierarchicalFileStorageService.resolveUploadPath(version.getFilePath());
                        history.add(new ProjectPackageDto.XmlVersionDescriptor(
                                version.getVersionNumber(),
                                historyRelativePath,
                                version.getUserId(),
                                version.getComment(),
                                version.getCreated()
                        ));
                        binaryEntries.add(new ProjectPackageArchiveService.BinaryEntry(
                                historyArchivePath,
                                Files.size(historySource),
                                out -> Files.copy(historySource, out)
                        ));
                    }
                }
                xmlDescriptors.add(new ProjectPackageDto.XmlFileDescriptor(
                        relativePath,
                        xml.getFileName(),
                        xml.getVariant(),
                        xml.getBaseName(),
                        List.copyOf(history)
                ));
            }

            ProjectPackageDto.ExternalSource externalSource = page.getExternalSourceType() == null
                    && page.getExternalSourceId() == null
                    && page.getExternalSourceUrl() == null
                    && page.getExternalSourceMetadataJson() == null
                    ? null
                    : new ProjectPackageDto.ExternalSource(
                            page.getExternalSourceType(),
                            page.getExternalSourceId(),
                            page.getExternalSourceUrl(),
                            readJsonNode(page.getExternalSourceMetadataJson())
                    );
            pageDescriptors.put(descriptorPath, new ProjectPackageDto.PageDescriptor(
                    page.getName(),
                    page.getDescription(),
                    page.getTags() == null ? List.of() : List.copyOf(page.getTags()),
                    page.isLocked(),
                    page.getLockedReason(),
                    page.getWorkflowState(),
                    externalSource,
                    List.copyOf(images),
                    List.copyOf(xmlDescriptors)
            ));
        }

        ToolkitPackageDto.ToolkitPackage toolkitSnapshot = toolkitPackageService.buildProjectToolkitSnapshot(
                project.getLibrary().getWorkspaceId(),
                project.getCodec() == null ? null : project.getCodec().getId(),
                project.getLabelSet() == null ? null : project.getLabelSet().getId(),
                project.getDictionary() == null ? null : project.getDictionary().getId(),
                project.getTagSet() == null ? null : project.getTagSet().getId(),
                project.getNormalizationProfile() == null ? null : project.getNormalizationProfile().getId(),
                project.getValidationRuleset() == null ? null : project.getValidationRuleset().getId(),
                project.getVirtualKeyboard() == null ? null : project.getVirtualKeyboard().getId()
        );
        Map<String, ProjectPackageDto.ResourceDescriptor> resources = new LinkedHashMap<>();
        Map<ToolkitPackageDto.ToolkitType, String> resourcePaths = new LinkedHashMap<>();
        for (ToolkitPackageDto.ToolkitResource resource : toolkitSnapshot.resources()) {
            String path = "resources/" + resource.type().name().toLowerCase(Locale.ROOT).replace('_', '-') + ".json";
            resourcePaths.put(resource.type(), path);
            resources.put(path, new ProjectPackageDto.ResourceDescriptor(
                    resource.type(),
                    resource.name(),
                    resource.payload()
            ));
        }

        ProjectPackageDto.PackageManifest manifest = new ProjectPackageDto.PackageManifest(
                ProjectPackageDto.DEFAULT_SCHEMA_VERSION,
                LocalDateTime.now(),
                targetPageXmlVersion,
                includeXmlHistory,
                new ProjectPackageDto.ProjectSnapshot(
                        project.getName(),
                        project.getDescription(),
                        project.getTags() == null ? List.of() : new ArrayList<>(project.getTags()),
                        project.isLocked(),
                        project.getLockedReason(),
                        project.isAllowCodecOverride(),
                        project.isAllowDictionaryOverride(),
                        project.isAllowVirtualKeyboardOverride(),
                        project.isAllowLabelSetOverride(),
                        project.isAllowTagSetOverride(),
                        project.isAllowNormalizationProfileOverride(),
                        project.isAllowValidationRulesetOverride(),
                        project.getDefaultGtIndex(),
                        project.getDefaultRecognitionIndicesList()
                ),
                List.copyOf(pagePaths),
                Map.copyOf(resourcePaths),
                buildManifestWarnings(legacyTarget, targetPageXmlVersion)
        );
        return new ProjectPackageArchiveService.ExportPackage(
                manifest,
                Map.copyOf(pageDescriptors),
                Map.copyOf(resources),
                List.copyOf(binaryEntries)
        );
    }

    private List<String> buildManifestWarnings(boolean legacyTarget,
                                               String targetPageXmlVersion) {
        List<String> warnings = new ArrayList<>();
        if (legacyTarget) {
            warnings.add("Export target PAGE XML " + targetPageXmlVersion
                    + " may lose data because older PAGE schemas do not support all PAGE 2019 features.");
            warnings.add("Version history snapshots in page history directories are preserved as-is and are not downconverted; "
                    + "snapshot versions may differ from the selected export target.");
        }
        return warnings;
    }

    private List<Page> resolvePagesForExport(String projectId, List<String> selectedPageIds) {
        if (selectedPageIds == null || selectedPageIds.isEmpty()) {
            return pageRepository.findByProjectId(projectId).stream()
                    .sorted(pageOrderService.projectOrderComparator())
                    .toList();
        }

        Set<String> selected = new HashSet<>(selectedPageIds);
        return pageRepository.findByProjectId(projectId).stream()
                .filter(page -> selected.contains(page.getId()))
                .sorted(pageOrderService.projectOrderComparator())
                .toList();
    }

    private void applyToolkitReferences(Project project,
                                        Map<ToolkitPackageDto.ToolkitType, String> targetIds) {
        String codecId = targetIds.get(ToolkitPackageDto.ToolkitType.CODEC);
        if (codecId != null) {
            codecRepository.findById(codecId).ifPresent(project::setCodec);
        }

        String labelSetId = targetIds.get(ToolkitPackageDto.ToolkitType.LABEL_SET);
        if (labelSetId != null) {
            labelSetRepository.findById(labelSetId).ifPresent(project::setLabelSet);
        }

        String dictionaryId = targetIds.get(ToolkitPackageDto.ToolkitType.DICTIONARY);
        if (dictionaryId != null) {
            dictionaryRepository.findById(dictionaryId).ifPresent(project::setDictionary);
        }

        String tagSetId = targetIds.get(ToolkitPackageDto.ToolkitType.TAG_SET);
        if (tagSetId != null) {
            tagSetRepository.findById(tagSetId).ifPresent(project::setTagSet);
        }

        String normalizationProfileId = targetIds.get(ToolkitPackageDto.ToolkitType.NORMALIZATION_PROFILE);
        if (normalizationProfileId != null) {
            normalizationProfileRepository.findById(normalizationProfileId).ifPresent(project::setNormalizationProfile);
        }

        String validationRulesetId = targetIds.get(ToolkitPackageDto.ToolkitType.VALIDATION_RULESET);
        if (validationRulesetId != null) {
            validationRulesetRepository.findById(validationRulesetId).ifPresent(project::setValidationRuleset);
        }
        String virtualKeyboardId = targetIds.get(ToolkitPackageDto.ToolkitType.VIRTUAL_KEYBOARD);
        if (virtualKeyboardId != null) {
            virtualKeyboardRepository.findById(virtualKeyboardId).ifPresent(project::setVirtualKeyboard);
        }
    }

    private void applyProjectSettings(Project project, ProjectPackageDto.ProjectSnapshot snapshot) {
        project.setAllowCodecOverride(snapshot.allowCodecOverride());
        project.setAllowDictionaryOverride(snapshot.allowDictionaryOverride());
        project.setAllowVirtualKeyboardOverride(snapshot.allowVirtualKeyboardOverride());
        project.setAllowLabelSetOverride(snapshot.allowLabelSetOverride());
        project.setAllowTagSetOverride(snapshot.allowTagSetOverride());
        project.setAllowNormalizationProfileOverride(snapshot.allowNormalizationProfileOverride());
        project.setAllowValidationRulesetOverride(snapshot.allowValidationRulesetOverride());
        project.setDefaultGtIndex(snapshot.defaultGtIndex());
        project.setDefaultRecognitionIndicesList(snapshot.defaultRecognitionIndices());
    }

    private ImportCounts importPagesAndFiles(ProjectPackageArchiveService.ImportedPackage importedPackage,
                                             Project project,
                                             String userId,
                                             List<String> createdStoragePaths) throws IOException {
        int imageCount = 0;
        int xmlCount = 0;
        int versionCount = 0;
        int index = 0;
        for (ProjectPackageArchiveService.ImportedPage importedPage : importedPackage.pages()) {
            ProjectPackageDto.PageDescriptor descriptor = importedPage.descriptor();
            if (safeList(descriptor.xml()).size() > 1) {
                throw new IllegalArgumentException(
                        "Page '" + descriptor.name() + "' declares more than one head XML file"
                );
            }
            Page page = new Page();
            page.setProject(project);
            page.setName(descriptor.name().trim());
            page.setDescription(descriptor.description());
            page.setTags(descriptor.tags() == null ? List.of() : new ArrayList<>(descriptor.tags()));
            page.setLocked(descriptor.locked());
            page.setLockedReason(descriptor.lockedReason());
            page.setWorkflowState(descriptor.workflowState() == null ? Page.WorkflowState.OPEN : descriptor.workflowState());
            page.setSortOrder(index * PageOrderService.SORT_ORDER_STEP);
            if (descriptor.externalSource() != null) {
                page.setExternalSourceType(descriptor.externalSource().type());
                page.setExternalSourceId(descriptor.externalSource().id());
                page.setExternalSourceUrl(descriptor.externalSource().url());
                page.setExternalSourceMetadataJson(descriptor.externalSource().metadata() == null
                        ? null
                        : objectMapper.writeValueAsString(descriptor.externalSource().metadata()));
            }
            page = pageRepository.save(page);

            for (ProjectPackageDto.FileDescriptor imageDescriptor : safeList(descriptor.images())) {
                Path source = importedPage.resolve(importedPackage.root(), imageDescriptor.path());
                var stored = hierarchicalFileStorageService.storeFromPath(
                        source,
                        imageDescriptor.fileName(),
                        detectMimeType(imageDescriptor.fileName(), "application/octet-stream"),
                        project.getLibrary().getWorkspaceId(),
                        project.getId(),
                        de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType.IMG,
                        userId,
                        false
                );
                createdStoragePaths.add(stored.storagePath());
                page.getImages().add(new PageImage(
                        imageDescriptor.fileName(),
                        stored.storagePath(),
                        stored.mimeType(),
                        stored.sizeBytes(),
                        imageDescriptor.variant(),
                        imageDescriptor.baseName(),
                        page
                ));
                imageCount++;
            }

            for (ProjectPackageDto.XmlFileDescriptor xmlDescriptor : safeList(descriptor.xml())) {
                Path source = importedPage.resolve(importedPackage.root(), xmlDescriptor.path());
                XmlSchema schema = detectXmlSchema(source);
                var stored = hierarchicalFileStorageService.storeFromPath(
                        source,
                        xmlDescriptor.fileName(),
                        detectMimeType(xmlDescriptor.fileName(), "application/xml"),
                        project.getLibrary().getWorkspaceId(),
                        project.getId(),
                        de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType.XML,
                        userId,
                        false
                );
                createdStoragePaths.add(stored.storagePath());
                PageXml xml = pageXmlRepository.save(new PageXml(
                        xmlDescriptor.fileName(),
                        stored.storagePath(),
                        stored.mimeType(),
                        stored.sizeBytes(),
                        xmlDescriptor.variant(),
                        xmlDescriptor.baseName(),
                        schema,
                        schema == XmlSchema.PAGE_XML ? pageXmlConversionService.detectPageVersion(source) : null,
                        page
                ));
                if (schema == XmlSchema.PAGE_XML) {
                    pageXmlCanonicalizationService.canonicalizeAtIngest(
                            xml,
                            userId,
                            "project package import",
                            false
                    );
                }
                versionCount += importXmlHistory(
                        importedPackage,
                        importedPage,
                        xml,
                        xmlDescriptor,
                        createdStoragePaths
                );
                xmlCount++;
            }
            index++;
        }
        return new ImportCounts(index, imageCount, xmlCount, versionCount);
    }

    private int importXmlHistory(ProjectPackageArchiveService.ImportedPackage importedPackage,
                                 ProjectPackageArchiveService.ImportedPage importedPage,
                                 PageXml targetXml,
                                 ProjectPackageDto.XmlFileDescriptor descriptor,
                                 List<String> createdStoragePaths) throws IOException {
        int imported = 0;
        for (ProjectPackageDto.XmlVersionDescriptor versionDescriptor : safeList(descriptor.history()).stream()
                .sorted(Comparator.comparing(ProjectPackageDto.XmlVersionDescriptor::versionNumber))
                .toList()) {
            Path source = importedPage.resolve(importedPackage.root(), versionDescriptor.path());
            String targetRelative = "xml/versions/" + targetXml.getId() + "/"
                    + versionDescriptor.versionNumber() + ".xml";
            Path targetAbsolute = Paths.get(uploadDir, targetRelative).normalize();
            Files.createDirectories(targetAbsolute.getParent());
            Files.copy(source, targetAbsolute, StandardCopyOption.REPLACE_EXISTING);
            createdStoragePaths.add(targetRelative);

            PageXmlVersion version = new PageXmlVersion();
            version.setPageXml(targetXml);
            version.setVersionNumber(versionDescriptor.versionNumber());
            version.setFilePath(targetRelative);
            version.setFileSize(Files.size(targetAbsolute));
            version.setUserId(versionDescriptor.userId() == null || versionDescriptor.userId().isBlank()
                    ? "import"
                    : versionDescriptor.userId());
            version.setComment(versionDescriptor.comment());
            version = pageXmlVersionRepository.saveAndFlush(version);
            if (versionDescriptor.created() != null) {
                pageXmlVersionRepository.updateCreatedTimestamp(version.getId(), versionDescriptor.created());
                version.setCreated(versionDescriptor.created());
            }
            imported++;
        }
        return imported;
    }

    private void replaceExistingProject(String workspaceId,
                                        Project existingProject,
                                        Project importedProject,
                                        String targetName) {
        String existingProjectId = existingProject.getId();
        List<String> oldStoragePaths = new ArrayList<>();
        List<Page> existingPages = pageRepository.findByProjectId(existingProjectId);
        Map<String, PageXml> xmlHeadsByPageId = pageXmlHeadsByPageId(existingPages);
        for (Page page : existingPages) {
            for (PageImage image : safeList(
                    page.getImages() == null ? null : new ArrayList<>(page.getImages())
            )) {
                oldStoragePaths.add(image.getFilePath());
                oldStoragePaths.add(image.getThumbnailPath());
            }
            for (PageXml xml : Optional.ofNullable(xmlHeadsByPageId.get(page.getId())).stream().toList()) {
                oldStoragePaths.add(xml.getFilePath());
                pageXmlVersionRepository.findByPageXml_IdOrderByVersionNumberDesc(xml.getId()).stream()
                        .map(PageXmlVersion::getFilePath)
                        .forEach(oldStoragePaths::add);
            }
        }

        projectRepository.delete(existingProject);
        projectRepository.flush();
        importedProject.setName(targetName);
        projectRepository.saveAndFlush(importedProject);

        Runnable cleanup = () -> {
            hierarchicalFileStorageService.deleteReplacedProjectFiles(
                    workspaceId,
                    existingProjectId,
                    oldStoragePaths
            );
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    private String normalizedProjectName(String value) {
        return value == null || value.isBlank() ? "Imported Project" : value.trim();
    }

    private int previewCacheWeight(long extractedBytes) {
        long extractedWeight = divideRoundingUp(
                Math.max(1L, extractedBytes),
                PREVIEW_CACHE_WEIGHT_UNIT_BYTES
        );
        return Math.max(minimumPreviewCacheWeight, clampCacheWeight(extractedWeight));
    }

    private static long divideRoundingUp(long value, long divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    private static int clampCacheWeight(long weight) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, weight));
    }

    private String validateRenamedProjectName(String value, String libraryId) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A custom project name is required when importing a renamed copy");
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("The custom project name must not exceed 100 characters");
        }
        if (projectRepository.existsByNameAndLibraryId(normalized, libraryId)) {
            throw new IllegalArgumentException(
                    "Project name '" + normalized + "' already exists in this workspace"
            );
        }
        return normalized;
    }

    private String suggestedRenamedProjectName(String sourceName, String libraryId) {
        String normalized = normalizedProjectName(sourceName);
        for (int index = 1; index < 10_000; index++) {
            String suffix = index == 1 ? " (imported)" : " (imported " + index + ")";
            int baseLength = Math.min(normalized.length(), 100 - suffix.length());
            String candidate = normalized.substring(0, baseLength).trim() + suffix;
            if (!projectRepository.existsByNameAndLibraryId(candidate, libraryId)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique project import name");
    }

    private String uniqueProjectName(String baseName, String libraryId) {
        String normalizedBase = normalizedProjectName(baseName);
        if (!projectRepository.existsByNameAndLibraryId(normalizedBase, libraryId)) {
            return normalizedBase;
        }

        String candidate = normalizedBase + " (imported)";
        if (!projectRepository.existsByNameAndLibraryId(candidate, libraryId)) {
            return candidate;
        }

        int index = 2;
        while (index < 10_000) {
            String next = normalizedBase + " (imported " + index + ")";
            if (!projectRepository.existsByNameAndLibraryId(next, libraryId)) {
                return next;
            }
            index++;
        }

        return normalizedBase + " (" + UUID.randomUUID() + ")";
    }

    private Project requireProject(String workspaceId, String projectId) {
        Project project = projectRepository.findWithAssociationsById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        if (project.getLibrary() == null || !workspaceId.equals(project.getLibrary().getWorkspaceId())) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        return project;
    }

    private ProjectPackageRelease requireRelease(String projectId, String releaseId) {
        return projectPackageReleaseRepository.findByIdAndProjectId(releaseId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project release", releaseId));
    }

    private ReleaseFileDownload resolveReleaseFileDownload(ProjectPackageRelease release, String releaseId) throws IOException {
        if (release.getPackageFilePath() == null || release.getPackageFilePath().isBlank()) {
            throw new ResourceNotFoundException("Project release package not found.");
        }
        Path packagePath = resolveStoragePath(release.getPackageFilePath());
        if (!Files.exists(packagePath)) {
            throw new ResourceNotFoundException("Project release package not found.");
        }
        return new ReleaseFileDownload(
                release.getPackageFileName() == null ? ("project-release-" + releaseId + ".zip") : release.getPackageFileName(),
                packagePath,
                Files.size(packagePath),
                release.getPackageChecksumSha256() == null ? computeSha256(packagePath) : release.getPackageChecksumSha256()
        );
    }

    private void requireShareableRelease(ProjectPackageRelease release) {
        if (release.getStatus() != ProjectPackageRelease.Status.READY) {
            throw new IllegalStateException("Only ready releases can be shared.");
        }
        if (release.getPackageFilePath() == null || release.getPackageFilePath().isBlank()) {
            throw new IllegalStateException("Release package is not available.");
        }
        Path packagePath = resolveStoragePath(release.getPackageFilePath());
        if (!Files.exists(packagePath)) {
            throw new IllegalStateException("Release package is not available.");
        }
    }

    private void requireActiveShare(ProjectPackageRelease release) {
        if (release.getSharePublicId() == null || release.getSharePublicId().isBlank()
                || release.getShareSecretHash() == null || release.getShareSecretHash().isBlank()) {
            throw new ResourceNotFoundException("Project release package not found.");
        }
        if (release.getShareRevokedAt() != null) {
            throw new ResourceNotFoundException("Project release package not found.");
        }
        if (release.getShareExpiresAt() == null || !release.getShareExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResourceNotFoundException("Project release package not found.");
        }
    }

    private ProjectPackageDto.ReleaseSummaryResponse toReleaseSummaryResponse(ProjectPackageRelease release) {
        boolean shareEnabled = release.getSharePublicId() != null
                && !release.getSharePublicId().isBlank()
                && release.getShareSecretHash() != null
                && !release.getShareSecretHash().isBlank()
                && release.getShareRevokedAt() == null
                && release.getShareExpiresAt() != null
                && release.getShareExpiresAt().isAfter(LocalDateTime.now());
        return new ProjectPackageDto.ReleaseSummaryResponse(
                release.getId(),
                release.getVersionNumber(),
                release.getVersionTag(),
                release.getNotes(),
                ProjectPackageDto.ProjectReleaseStatus.valueOf(release.getStatus().name()),
                release.getPageCount() == null ? 0L : release.getPageCount(),
                release.getTargetPageXmlVersion(),
                release.isIncludeXmlHistory(),
                readEmbeddedOutputs(release.getEmbeddedOutputsJson()),
                release.getFailureReason(),
                release.getPackageFileName(),
                release.getPackageFileSize(),
                release.getPackageChecksumSha256(),
                release.getManifestChecksumSha256(),
                release.getCreatedByUserId(),
                release.getSourceProjectUpdatedAt(),
                shareEnabled,
                release.getShareSecretPrefix(),
                release.getShareCreatedAt(),
                release.getShareExpiresAt(),
                release.getShareRevokedAt(),
                release.getShareLastUsedAt(),
                release.getShareDownloadCount() == null ? 0L : release.getShareDownloadCount(),
                release.getCreated(),
                release.getUpdated()
        );
    }

    private List<DocumentExportDto.EmbeddedProjectOutputRequest> copyEmbeddedOutputs(List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputs) {
        if (embeddedOutputs == null || embeddedOutputs.isEmpty()) {
            return List.of();
        }
        return embeddedOutputs.stream()
                .filter(java.util.Objects::nonNull)
                .map(output -> new DocumentExportDto.EmbeddedProjectOutputRequest(
                        output.format(),
                        output.includePageDelimiters(),
                        output.textLevel(),
                        output.textVariantIndex(),
                        output.pdfProfile(),
                        output.teiProfile(),
                        output.spreadsheetProfiles() == null ? null : List.copyOf(output.spreadsheetProfiles()),
                        output.docxOptions() == null ? null : new DocumentExportDto.DocxOptions(
                                output.docxOptions().preserveLineBreaks(),
                                output.docxOptions().forcePageBreaks(),
                                output.docxOptions().includeImageNames(),
                                output.docxOptions().markUnclearWords(),
                                output.docxOptions().unclearConfidenceThreshold()
                        ),
                        output.imageVariantSelection() == null ? null : new DocumentExportDto.ImageVariantSelection(
                                output.imageVariantSelection().mode(),
                                output.imageVariantSelection().variant(),
                                output.imageVariantSelection().pageVariants() == null
                                        ? null
                                        : new LinkedHashMap<>(output.imageVariantSelection().pageVariants()),
                                output.imageVariantSelection().fallbackImage()
                        )
                ))
                .toList();
    }

    private String writeEmbeddedOutputsJson(List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputs) {
        try {
            return objectMapper.writeValueAsString(copyEmbeddedOutputs(embeddedOutputs));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize embedded outputs", e);
        }
    }

    private List<DocumentExportDto.EmbeddedProjectOutputRequest> readEmbeddedOutputs(String embeddedOutputsJson) {
        if (embeddedOutputsJson == null || embeddedOutputsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(DocumentExportDto.EmbeddedProjectOutputRequest.class)
                    .readValue(embeddedOutputsJson);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to read embedded outputs", e);
        }
    }

    private String uniquePageName(String baseName, Collection<String> usedLowerCaseNames) {
        String normalizedBase = (baseName == null || baseName.isBlank()) ? "Imported Page" : baseName.trim();
        String lowerBase = normalizedBase.toLowerCase();
        if (!usedLowerCaseNames.contains(lowerBase)) {
            return normalizedBase;
        }

        String candidate = normalizedBase + " (imported)";
        if (!usedLowerCaseNames.contains(candidate.toLowerCase())) {
            return candidate;
        }

        int index = 2;
        while (index < 10_000) {
            String next = normalizedBase + " (imported " + index + ")";
            if (!usedLowerCaseNames.contains(next.toLowerCase())) {
                return next;
            }
            index++;
        }

        return normalizedBase + " (" + UUID.randomUUID() + ")";
    }

    private String fileExtension(String fileNameOrPath) {
        if (fileNameOrPath == null || fileNameOrPath.isBlank()) {
            return "";
        }

        String normalized = fileNameOrPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;

        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot);
    }

    private String stripExtension(String fileName) {
        String extension = fileExtension(fileName);
        return extension.isEmpty() ? fileName : fileName.substring(0, fileName.length() - extension.length());
    }

    private tools.jackson.databind.JsonNode readJsonNode(String json) throws IOException {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readTree(json);
    }

    private XmlSchema detectXmlSchema(Path xmlPath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(xmlPath.toFile());
            Element root = document.getDocumentElement();
            XmlSchema fromNamespace = XmlSchema.detectFromNamespace(root.getNamespaceURI());
            return fromNamespace == XmlSchema.UNKNOWN
                    ? XmlSchema.detectFromRootElement(root.getLocalName() == null ? root.getNodeName() : root.getLocalName())
                    : fromNamespace;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not detect XML schema for " + xmlPath.getFileName(), e);
        }
    }

    private String detectMimeType(String fileName, String fallback) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) return "image/tiff";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".xml")) return "application/xml";
        return fallback;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Map<String, PageXml> pageXmlHeadsByPageId(List<Page> pages) {
        if (pages.isEmpty()) {
            return Map.of();
        }
        Map<String, PageXml> headsByPageId = new HashMap<>();
        for (PageXml pageXml : pageXmlRepository.findByPage_IdIn(pages.stream().map(Page::getId).toList())) {
            headsByPageId.put(pageXml.getPage().getId(), pageXml);
        }
        return headsByPageId;
    }

    private void validateSingleXmlHeadPerPage(ProjectPackageArchiveService.ImportedPackage importedPackage) {
        for (ProjectPackageArchiveService.ImportedPage importedPage : importedPackage.pages()) {
            ProjectPackageDto.PageDescriptor descriptor = importedPage.descriptor();
            if (safeList(descriptor.xml()).size() > 1) {
                throw new IllegalArgumentException(
                        "Page '" + descriptor.name() + "' declares more than one head XML file"
                );
            }
        }
    }

    private long estimatePackageBytes(PackageSnapshot packageSnapshot) {
        long binaryBytes = packageSnapshot.exportPackage().binaryEntries().stream()
                .mapToLong(ProjectPackageArchiveService.BinaryEntry::contentLength)
                .sum();
        return binaryBytes + 1_048_576L;
    }

    private void writePackageZip(Path outputPath, PackageSnapshot packageSnapshot) throws IOException {
        projectPackageArchiveService.writeZip(outputPath, packageSnapshot.exportPackage());
    }

    private void writePackageZip(OutputStream outputStream, PackageSnapshot packageSnapshot) throws IOException {
        projectPackageArchiveService.writeZip(outputStream, packageSnapshot.exportPackage());
    }

    private void writePackageEntries(java.util.zip.ZipOutputStream zipOut,
                                     PackageSnapshot packageSnapshot,
                                     String entryPrefix) throws IOException {
        projectPackageArchiveService.writeEntries(zipOut, packageSnapshot.exportPackage(), entryPrefix);
    }

    private void writeBasicProjectExportEntries(java.util.zip.ZipOutputStream zipOut,
                                                List<Page> pages,
                                                String targetPageXmlVersion,
                                                List<DocumentExportService.EmbeddedProjectOutput> embeddedOutputs) throws IOException {
        writeBasicProjectExportEntries(zipOut, pages, targetPageXmlVersion, embeddedOutputs, "");
    }

    private void writeBasicProjectExportEntries(java.util.zip.ZipOutputStream zipOut,
                                                List<Page> pages,
                                                String targetPageXmlVersion,
                                                List<DocumentExportService.EmbeddedProjectOutput> embeddedOutputs,
                                                String entryPrefix) throws IOException {
        Map<String, Integer> usedEntryPaths = new HashMap<>();
        Map<String, PageXml> xmlHeadsByPageId = pageXmlHeadsByPageId(pages);

        for (Page page : pages) {
            List<PageImage> images = new ArrayList<>(page.getImages() == null ? Set.<PageImage>of() : page.getImages());
            images.sort(Comparator.comparing((PageImage image) -> image.getVariant() == null ? "" : image.getVariant(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(PageImage::getId));
            for (PageImage image : images) {
                String entryPath = uniqueArchivePath(
                        sanitizeArchiveName(image.getFileName(), image.getVariant() + fileExtension(image.getFileName())),
                        usedEntryPaths
                );
                archiveIoService.writeFileEntry(
                        zipOut,
                        prefixedArchivePath(entryPrefix, entryPath),
                        hierarchicalFileStorageService.resolveUploadPath(image.getFilePath())
                );
            }

            List<PageXml> xmlFiles = Optional.ofNullable(xmlHeadsByPageId.get(page.getId())).stream().toList();
            for (PageXml xml : xmlFiles) {
                String entryPath = uniqueArchivePath(
                        sanitizeArchiveName(xml.getFileName(), xml.getVariant() + fileExtension(xml.getFileName())),
                        usedEntryPaths
                );
                Path source = hierarchicalFileStorageService.resolveUploadPath(xml.getFilePath());
                if (xml.getSchema() == XmlSchema.PAGE_XML) {
                    archiveIoService.writeStreamEntry(
                            zipOut,
                            prefixedArchivePath(entryPrefix, entryPath),
                            entryOut -> pageXmlConversionService.writeFileToVersion(source, targetPageXmlVersion, entryOut)
                    );
                } else {
                    archiveIoService.writeFileEntry(zipOut, prefixedArchivePath(entryPrefix, entryPath), source);
                }
            }
        }

        for (DocumentExportService.EmbeddedProjectOutput output : embeddedOutputs) {
            String entryPath = uniqueArchivePath(sanitizeArchiveName(output.archivePath(), "export"), usedEntryPaths);
            archiveIoService.writeFileEntry(zipOut, prefixedArchivePath(entryPrefix, entryPath), output.absolutePath());
        }
    }

    private String prefixedArchivePath(String entryPrefix, String entryPath) {
        String normalizedEntryPath = archiveIoService.normalizeArchivePath(entryPath);
        if (entryPrefix == null || entryPrefix.isBlank()) {
            return normalizedEntryPath;
        }
        return archiveIoService.normalizeArchivePath(entryPrefix) + "/" + normalizedEntryPath;
    }

    private void cleanupEmbeddedOutputs(PackageSnapshot packageSnapshot) {
        if (packageSnapshot == null) {
            return;
        }
        cleanupEmbeddedOutputs(packageSnapshot.embeddedOutputs());
    }

    private void cleanupEmbeddedOutputs(List<DocumentExportService.EmbeddedProjectOutput> embeddedOutputs) {
        if (embeddedOutputs == null) {
            return;
        }
        for (DocumentExportService.EmbeddedProjectOutput output : embeddedOutputs) {
            try {
                Files.deleteIfExists(output.absolutePath());
            } catch (IOException ignored) {
            }
        }
    }

    private String normalizeReleaseTag(String requestedTag, int versionNumber, String projectId) {
        String candidate = normalizeNullableText(requestedTag);
        if (candidate == null) {
            candidate = "v" + versionNumber;
        }
        String normalized = candidate.trim();
        if (projectPackageReleaseRepository.findByProjectIdOrderByVersionNumberDesc(projectId).stream()
                .anyMatch(release -> release.getVersionTag() != null && release.getVersionTag().equalsIgnoreCase(normalized))) {
            throw new IllegalArgumentException("Release tag already exists in this project");
        }
        return normalized;
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

    private String sanitizeSegment(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return "release";
        }
        String sanitized = normalized
                .replaceAll("[^\\p{L}\\p{N}._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^[-.]+|[-.]+$)", "");
        return sanitized.isBlank() ? "release" : sanitized;
    }

    private String sanitizeArchiveName(String value, String fallback) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            normalized = fallback;
        }
        String fileName = normalized.replace('\\', '/');
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        String sanitized = fileName
                .replaceAll("[\\\\/:*?\"<>|]+", " ")
                .replaceAll("\\p{Cntrl}+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private String uniqueArchivePath(String requestedPath, Map<String, Integer> usedPaths) {
        String normalized = archiveIoService.normalizeArchivePath(requestedPath);
        String lowerCasePath = normalized.toLowerCase(Locale.ROOT);
        int duplicateIndex = usedPaths.getOrDefault(lowerCasePath, 0);
        if (duplicateIndex == 0) {
            usedPaths.put(lowerCasePath, 1);
            return normalized;
        }

        String parent = "";
        String name = normalized;
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            parent = normalized.substring(0, slash + 1);
            name = normalized.substring(slash + 1);
        }

        String stem = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            stem = name.substring(0, dot);
            extension = name.substring(dot);
        }

        String candidate;
        String candidateKey;
        do {
            candidate = parent + stem + " (" + duplicateIndex + ")" + extension;
            candidateKey = candidate.toLowerCase(Locale.ROOT);
            duplicateIndex++;
        } while (usedPaths.containsKey(candidateKey));

        usedPaths.put(lowerCasePath, duplicateIndex);
        usedPaths.put(candidateKey, 1);
        return candidate;
    }

    private String uniqueDirectoryName(String requestedName, Set<String> usedNames) {
        String normalized = archiveIoService.normalizeArchivePath(requestedName);
        String candidate = normalized;
        int suffix = 2;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = normalized + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private Path projectReleaseRoot(String workspaceId, String projectId, String releaseId) {
        return Path.of(uploadDir)
                .toAbsolutePath()
                .normalize()
                .resolve("project-releases")
                .resolve(sanitizeSegment(workspaceId))
                .resolve(sanitizeSegment(projectId))
                .resolve(sanitizeSegment(releaseId));
    }

    private Path resolveStoragePath(String storedPath) {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Path candidate = Path.of(storedPath);
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }
        return root.resolve(storedPath).normalize();
    }

    private String relativeToUploadRoot(Path path) {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(root)) {
            throw new IllegalArgumentException("Path is outside upload root: " + normalizedPath);
        }
        return root.relativize(normalizedPath).toString().replace('\\', '/');
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
            return hexDigest(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            return hexDigest(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private String hexDigest(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }

    private String buildShareDownloadUrl(String sharePublicId) {
        String normalizedBase = projectReleaseSharePublicBaseUrl == null
                ? ""
                : projectReleaseSharePublicBaseUrl.replaceAll("/+$", "");
        return normalizedBase + "/" + sharePublicId + "/download";
    }

    private String generateOpaqueToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (!authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        return token.isBlank() ? null : token;
    }

    private boolean matchesShareSecret(String expectedHash, String providedSecret) {
        if (expectedHash == null || expectedHash.isBlank() || providedSecret == null || providedSecret.isBlank()) {
            return false;
        }
        byte[] expectedBytes = decodeHex(expectedHash);
        byte[] actualBytes = decodeHex(computeSha256(providedSecret.getBytes(StandardCharsets.UTF_8)));
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private byte[] decodeHex(String value) {
        if (value == null || (value.length() % 2) != 0) {
            return new byte[0];
        }
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                return new byte[0];
            }
            bytes[i / 2] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private record PackageSnapshot(
            ProjectPackageArchiveService.ExportPackage exportPackage,
            List<DocumentExportService.EmbeddedProjectOutput> embeddedOutputs
    ) {
    }

    private record ImportCounts(int pages, int images, int xml, int xmlVersions) {
    }

    private record ToolkitImport(
            Map<ToolkitPackageDto.ToolkitType, String> targetIds,
            List<String> warnings
    ) {
    }

    private static final class PackagePreviewSession {
        private final String workspaceId;
        private final String userId;
        private final ProjectPackageArchiveService.ImportedPackage importedPackage;
        private final int cacheWeight;
        private final AtomicReference<PreviewSessionState> state =
                new AtomicReference<>(PreviewSessionState.AVAILABLE);

        private PackagePreviewSession(String workspaceId,
                                      String userId,
                                      ProjectPackageArchiveService.ImportedPackage importedPackage,
                                      int cacheWeight) {
            this.workspaceId = workspaceId;
            this.userId = userId;
            this.importedPackage = importedPackage;
            this.cacheWeight = cacheWeight;
        }

        private String workspaceId() {
            return workspaceId;
        }

        private String userId() {
            return userId;
        }

        private ProjectPackageArchiveService.ImportedPackage importedPackage() {
            return importedPackage;
        }

        private int cacheWeight() {
            return cacheWeight;
        }

        private boolean claim() {
            return state.compareAndSet(PreviewSessionState.AVAILABLE, PreviewSessionState.CLAIMED);
        }

        private void onRemoval() {
            if (state.compareAndSet(PreviewSessionState.AVAILABLE, PreviewSessionState.CLOSED)) {
                importedPackage.close();
            }
        }

        private void finish() {
            PreviewSessionState previous = state.getAndSet(PreviewSessionState.CLOSED);
            if (previous != PreviewSessionState.CLOSED) {
                importedPackage.close();
            }
        }
    }

    private enum PreviewSessionState {
        AVAILABLE,
        CLAIMED,
        CLOSED
    }

    public record ReleaseFileDownload(String fileName, Path absolutePath, long contentLength, String checksumSha256) {
    }

    public record SharedReleaseDownload(String fileName, Path absolutePath, long contentLength, String checksumSha256) {
    }
}
