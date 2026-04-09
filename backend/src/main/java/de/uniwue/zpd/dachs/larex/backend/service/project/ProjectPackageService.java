package de.uniwue.zpd.dachs.larex.backend.service.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UtilityPackageDto;
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
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlVersionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectPackageReleaseRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.export.DocumentExportService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.StorageTrackingService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.utility.UtilityPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Service
@Transactional
public class ProjectPackageService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
    private final WorkspaceAccessService workspaceAccessService;
    private final ArchiveIoService archiveIoService;
    private final UtilityPackageService utilityPackageService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final PageFilterIndexService pageFilterIndexService;
    private final StorageTrackingService storageTrackingService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final PageXmlConversionService pageXmlConversionService;
    private final PageXmlCanonicalizationService pageXmlCanonicalizationService;
    private final DocumentExportService documentExportService;
    private final ObjectMapper objectMapper;

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
                                 WorkspaceAccessService workspaceAccessService,
                                 ArchiveIoService archiveIoService,
                                 UtilityPackageService utilityPackageService,
                                 HierarchicalFileStorageService hierarchicalFileStorageService,
                                 PageFilterIndexService pageFilterIndexService,
                                 StorageTrackingService storageTrackingService,
                                 WorkspaceQuotaGuardService workspaceQuotaGuardService,
                                 PageXmlConversionService pageXmlConversionService,
                                 PageXmlCanonicalizationService pageXmlCanonicalizationService,
                                 DocumentExportService documentExportService,
                                 ObjectMapper objectMapper) {
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
        this.workspaceAccessService = workspaceAccessService;
        this.archiveIoService = archiveIoService;
        this.utilityPackageService = utilityPackageService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.pageFilterIndexService = pageFilterIndexService;
        this.storageTrackingService = storageTrackingService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.pageXmlConversionService = pageXmlConversionService;
        this.pageXmlCanonicalizationService = pageXmlCanonicalizationService;
        this.documentExportService = documentExportService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public byte[] exportProjectPackage(String workspaceId,
                                       String projectId,
                                       String userId,
                                       ProjectPackageDto.ExportRequest request) throws IOException {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        return exportProjectPackageInternal(workspaceId, projectId, request);
    }

    @Transactional(readOnly = true)
    public byte[] exportProjectPackageInternal(String workspaceId,
                                               String projectId,
                                               ProjectPackageDto.ExportRequest request) throws IOException {
        Project project = requireProject(workspaceId, projectId);
        List<Page> pages = resolvePagesForExport(projectId, request == null ? null : request.pageIds());
        PackageSnapshot packageSnapshot = buildPackageSnapshot(
                project,
                pages,
                request == null ? null : request.targetPageXmlVersion(),
                request == null ? null : request.embeddedOutputs()
        );
        return createPackageBytes(packageSnapshot);
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
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        Project project = requireProject(workspaceId, projectId);

        int nextVersionNumber = defaultInt(projectPackageReleaseRepository.findMaxVersionNumberByProjectId(projectId)) + 1;
        String versionTag = normalizeReleaseTag(request == null ? null : request.versionTag(), nextVersionNumber, projectId);
        String targetPageXmlVersion = pageXmlConversionService.normalizeTargetVersion(
                request == null ? null : request.targetPageXmlVersion()
        );
        List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputs = copyEmbeddedOutputs(
                request == null ? null : request.embeddedOutputs()
        );
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
        release.setEmbeddedOutputsJson(writeEmbeddedOutputsJson(embeddedOutputs));
        release.setSourceProjectUpdatedAt(project.getUpdated());
        release = projectPackageReleaseRepository.save(release);

        Path releaseRoot = projectReleaseRoot(workspaceId, projectId, release.getId());
        long reservedBytes = 0L;

        try {
            PackageSnapshot packageSnapshot = buildPackageSnapshot(project, pages, targetPageXmlVersion, embeddedOutputs);
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
            release.setManifestChecksumSha256(computeSha256(objectMapper.writeValueAsBytes(packageSnapshot.manifest())));
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
        }
    }

    public ProjectPackageDto.ReleaseShareResponse createOrRotateReleaseShare(String workspaceId,
                                                                             String projectId,
                                                                             String releaseId,
                                                                             ProjectPackageDto.UpsertReleaseShareRequest request,
                                                                             String userId) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
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
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
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
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
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
        Path tempDir = archiveIoService.extractZipToTempDir(packageStream, "larex-project-import-");
        try {
            Path manifestPath = tempDir.resolve("manifest.json");
            if (!Files.exists(manifestPath)) {
                throw new IllegalArgumentException("manifest.json missing in project package");
            }

            ProjectPackageDto.PackageManifest manifest = archiveIoService.readJson(
                    manifestPath,
                    ProjectPackageDto.PackageManifest.class
            );

            long reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                    workspaceId,
                    estimatePersistentImportBytes(tempDir, manifest),
                    "project-package-import"
            );

            try {
                UtilityPackageDto.ImportResult utilityImportResult = importUtilityReferencesFromPackage(
                        workspaceId,
                        userId,
                        tempDir,
                        manifest.utilityReferences(),
                        manifest.sourceWorkspaceName()
                );

                Library library = libraryRepository.findByWorkspaceId(workspaceId)
                        .orElseThrow(() -> new IllegalArgumentException("Library not found for workspace: " + workspaceId));

                String projectName = uniqueProjectName(
                        manifest.project().name(),
                        library.getId()
                );

                Project project = new Project();
                project.setLibrary(library);
                project.setName(projectName);
                project.setDescription(manifest.project().description());
                project.setTags(new ArrayList<>(manifest.project().tags() == null ? List.of() : manifest.project().tags()));
                project.setLocked(manifest.project().locked());
                project.setLockedReason(manifest.project().lockedReason());
                applyUtilityReferences(project, utilityImportResult.sourceToTargetIds(), manifest.utilityReferences());
                project = projectRepository.save(project);

                Map<String, Page> pageBySourceId = importPages(project, manifest.pages());
                Map<String, PageXml> xmlBySourceId = importProjectFiles(tempDir, project, manifest.files(), pageBySourceId, userId);
                int xmlVersionCount = importXmlVersions(tempDir, manifest.xmlVersions(), xmlBySourceId);

                pageFilterIndexService.rebuildProjectIndex(project.getId());

                int imageCount = (int) manifest.files().stream().filter(f -> f.kind() == ProjectPackageDto.FileKind.IMAGE).count();
                int xmlCount = (int) manifest.files().stream().filter(f -> f.kind() == ProjectPackageDto.FileKind.XML).count();

                List<String> warnings = new ArrayList<>();
                if (manifest.warnings() != null) {
                    warnings.addAll(manifest.warnings());
                }
                warnings.addAll(utilityImportResult.warnings());

                return new ProjectPackageDto.ImportResult(
                        workspaceId,
                        project.getId(),
                        project.getName(),
                        pageBySourceId.size(),
                        imageCount,
                        xmlCount,
                        xmlVersionCount,
                        warnings,
                        utilityImportResult.sourceToTargetIds()
                );
            } finally {
                workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
            }
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private long estimatePersistentImportBytes(Path tempDir, ProjectPackageDto.PackageManifest manifest) throws IOException {
        long totalBytes = 0L;

        if (manifest.files() != null) {
            for (ProjectPackageDto.FileEntry entry : manifest.files()) {
                totalBytes += resolveExistingImportFileSize(tempDir, entry.archivePath());
                if (entry.thumbnailArchivePath() != null && !entry.thumbnailArchivePath().isBlank()) {
                    totalBytes += resolveExistingImportFileSize(tempDir, entry.thumbnailArchivePath());
                }
            }
        }

        if (manifest.xmlVersions() != null) {
            for (ProjectPackageDto.XmlVersionEntry versionEntry : manifest.xmlVersions()) {
                totalBytes += resolveExistingImportFileSize(tempDir, versionEntry.archivePath());
            }
        }

        return totalBytes;
    }

    private long resolveExistingImportFileSize(Path tempDir, String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            return 0L;
        }

        Path sourcePath = tempDir.resolve(archiveIoService.normalizeArchivePath(relativePath));
        return Files.exists(sourcePath) ? Files.size(sourcePath) : 0L;
    }

    private PackageSnapshot buildPackageSnapshot(Project project,
                                                 List<Page> pages,
                                                 String requestedTargetPageXmlVersion,
                                                 List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputRequests) throws IOException {
        String targetPageXmlVersion = pageXmlConversionService.normalizeTargetVersion(requestedTargetPageXmlVersion);
        boolean legacyTarget = pageXmlConversionService.isLegacyTargetVersion(targetPageXmlVersion);
        ExportBundle exportBundle = buildExportBundle(project, pages, targetPageXmlVersion, legacyTarget);
        List<DocumentExportService.EmbeddedProjectOutput> embeddedOutputs = documentExportService.exportEmbeddedProjectOutputs(
                project,
                pages,
                embeddedOutputRequests
        );
        byte[] metsBytes = buildMetsXml(project, pages, exportBundle.manifest(), embeddedOutputs);
        return new PackageSnapshot(exportBundle, embeddedOutputs, metsBytes, targetPageXmlVersion);
    }

    private ExportBundle buildExportBundle(Project project,
                                           List<Page> pages,
                                           String targetPageXmlVersion,
                                           boolean legacyTarget) throws IOException {
        List<ProjectPackageDto.PageSnapshot> pageSnapshots = new ArrayList<>();
        List<ProjectPackageDto.FileEntry> fileEntries = new ArrayList<>();
        List<ProjectPackageDto.XmlVersionEntry> versionEntries = new ArrayList<>();
        Map<String, Path> versionPathByArchivePath = new LinkedHashMap<>();

        Map<String, PageImage> imageBySourceId = new HashMap<>();
        Map<String, Path> fileSourcePathByArchivePath = new HashMap<>();

        for (Page page : pages) {
            pageSnapshots.add(new ProjectPackageDto.PageSnapshot(
                    page.getId(),
                    page.getName(),
                    page.getDescription(),
                    page.getTags() == null ? List.of() : new ArrayList<>(page.getTags()),
                    page.getCreated(),
                    page.getUpdated(),
                    page.isLocked(),
                    page.getLockedReason()
            ));

            List<PageImage> images = new ArrayList<>(page.getImages() == null ? Set.<PageImage>of() : page.getImages());
            images.sort(Comparator.comparing(PageImage::getId));
            for (PageImage image : images) {
                String archivePath = "files/images/" + image.getId() + fileExtension(image.getFileName());
                String thumbnailArchivePath = image.getThumbnailPath() == null
                        ? null
                        : "files/thumbnails/" + image.getId() + fileExtension(image.getThumbnailPath());

                fileEntries.add(new ProjectPackageDto.FileEntry(
                        image.getId(),
                        page.getId(),
                        ProjectPackageDto.FileKind.IMAGE,
                        image.getFileName(),
                        image.getMimeType(),
                        image.getFileSize(),
                        image.getVariant(),
                        image.getBaseName(),
                        archivePath,
                        null,
                        null,
                        thumbnailArchivePath,
                        image.getCreated(),
                        image.getUpdated()
                ));
                fileSourcePathByArchivePath.put(archivePath, hierarchicalFileStorageService.resolveUploadPath(image.getFilePath()));
                imageBySourceId.put(image.getId(), image);
            }

            List<PageXml> xmlFiles = new ArrayList<>(page.getXmlFiles() == null ? Set.<PageXml>of() : page.getXmlFiles());
            xmlFiles.sort(Comparator.comparing(PageXml::getId));
            for (PageXml xml : xmlFiles) {
                String archivePath = "files/xml/" + xml.getId() + fileExtension(xml.getFileName());
                String exportSchemaVersion = xml.getSchema() == XmlSchema.PAGE_XML
                        ? targetPageXmlVersion
                        : xml.getSchemaVersion();
                fileEntries.add(new ProjectPackageDto.FileEntry(
                        xml.getId(),
                        page.getId(),
                        ProjectPackageDto.FileKind.XML,
                        xml.getFileName(),
                        xml.getMimeType(),
                        xml.getFileSize(),
                        xml.getVariant(),
                        xml.getBaseName(),
                        archivePath,
                        xml.getSchema(),
                        exportSchemaVersion,
                        null,
                        xml.getCreated(),
                        xml.getUpdated()
                ));
                fileSourcePathByArchivePath.put(archivePath, hierarchicalFileStorageService.resolveUploadPath(xml.getFilePath()));

                List<PageXmlVersion> versions = pageXmlVersionRepository.findByPageXml_IdOrderByVersionNumberDesc(xml.getId())
                        .stream()
                        .sorted(Comparator.comparing(PageXmlVersion::getVersionNumber))
                        .toList();
                for (PageXmlVersion version : versions) {
                    String versionArchivePath = "files/xml-versions/" + xml.getId() + "/" + version.getVersionNumber() + ".xml";
                    versionEntries.add(new ProjectPackageDto.XmlVersionEntry(
                            version.getId(),
                            xml.getId(),
                            version.getVersionNumber(),
                            versionArchivePath,
                            version.getFileSize(),
                            version.getUserId(),
                            version.getComment(),
                            version.getCreated()
                    ));
                    versionPathByArchivePath.put(versionArchivePath, hierarchicalFileStorageService.resolveUploadPath(version.getFilePath()));
                }
            }
        }

        UtilityPackageDto.UtilityPackage utilitySnapshot = utilityPackageService.buildProjectUtilitySnapshot(
                project.getLibrary().getWorkspaceId(),
                project.getCodec() == null ? null : project.getCodec().getId(),
                project.getLabelSet() == null ? null : project.getLabelSet().getId(),
                project.getDictionary() == null ? null : project.getDictionary().getId(),
                project.getTagSet() == null ? null : project.getTagSet().getId(),
                project.getNormalizationProfile() == null ? null : project.getNormalizationProfile().getId(),
                project.getValidationRuleset() == null ? null : project.getValidationRuleset().getId()
        );

        Map<String, UtilityPackageDto.UtilityResource> utilityResourcesByPath = new LinkedHashMap<>();
        Map<UtilityPackageDto.UtilityType, ProjectPackageDto.UtilityReference> utilityRefByType = new LinkedHashMap<>();

        for (UtilityPackageDto.UtilityResource resource : utilitySnapshot.resources()) {
            String snapshotPath = "utilities/" + resource.type().name().toLowerCase() + "-" + resource.sourceId() + ".json";
            utilityResourcesByPath.put(snapshotPath, resource);
            utilityRefByType.put(resource.type(), new ProjectPackageDto.UtilityReference(
                    resource.sourceId(),
                    resource.name(),
                    resource.sourceCreated(),
                    resource.sourceUpdated(),
                    snapshotPath
            ));
        }

        ProjectPackageDto.UtilityReferences utilityReferences = new ProjectPackageDto.UtilityReferences(
                utilityRefByType.get(UtilityPackageDto.UtilityType.CODEC),
                utilityRefByType.get(UtilityPackageDto.UtilityType.LABEL_SET),
                utilityRefByType.get(UtilityPackageDto.UtilityType.DICTIONARY),
                utilityRefByType.get(UtilityPackageDto.UtilityType.TAG_SET),
                utilityRefByType.get(UtilityPackageDto.UtilityType.NORMALIZATION_PROFILE),
                utilityRefByType.get(UtilityPackageDto.UtilityType.VALIDATION_RULESET)
        );

        ProjectPackageDto.PackageManifest manifest = new ProjectPackageDto.PackageManifest(
                ProjectPackageDto.DEFAULT_SCHEMA_VERSION,
                LocalDateTime.now(),
                project.getLibrary().getWorkspaceId(),
                utilitySnapshot.meta() == null ? project.getLibrary().getWorkspaceId() : utilitySnapshot.meta().workspaceName(),
                new ProjectPackageDto.ProjectSnapshot(
                        project.getId(),
                        project.getName(),
                        project.getDescription(),
                        project.getTags() == null ? List.of() : new ArrayList<>(project.getTags()),
                        project.getCreated(),
                        project.getUpdated(),
                        project.isLocked(),
                        project.getLockedReason()
                ),
                pageSnapshots,
                utilityReferences,
                fileEntries,
                versionEntries,
                buildManifestWarnings(legacyTarget, targetPageXmlVersion)
        );

        return new ExportBundle(manifest, fileSourcePathByArchivePath, versionPathByArchivePath, utilityResourcesByPath, imageBySourceId);
    }

    private List<String> buildManifestWarnings(boolean legacyTarget,
                                               String targetPageXmlVersion) {
        List<String> warnings = new ArrayList<>();
        if (legacyTarget) {
            warnings.add("Export target PAGE XML " + targetPageXmlVersion
                    + " may lose data because older PAGE schemas do not support all PAGE 2019 features.");
            warnings.add("Version history snapshots in files/xml-versions are preserved as-is and are not downconverted; "
                    + "snapshot versions may differ from the selected export target.");
        }
        return warnings;
    }

    private List<Page> resolvePagesForExport(String projectId, List<String> selectedPageIds) {
        if (selectedPageIds == null || selectedPageIds.isEmpty()) {
            return pageRepository.findByProjectId(projectId).stream()
                    .sorted(Comparator.comparing(Page::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }

        Set<String> selected = new HashSet<>(selectedPageIds);
        return pageRepository.findByProjectId(projectId).stream()
                .filter(page -> selected.contains(page.getId()))
                .sorted(Comparator.comparing(Page::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Path resolveUploadPath(String archivePath,
                                   ProjectPackageDto.FileKind kind,
                                   String sourceId,
                                   ExportBundle exportBundle) {
        Path path = exportBundle.filePathByArchivePath().get(archivePath);
        if (path == null) {
            throw new IllegalArgumentException("Missing source file for " + kind + " " + sourceId);
        }
        return path;
    }

    private Path resolveThumbnailUploadPath(String imageSourceId, ExportBundle exportBundle) {
        PageImage image = exportBundle.imageBySourceId().get(imageSourceId);
        if (image == null || image.getThumbnailPath() == null) {
            return null;
        }
        return hierarchicalFileStorageService.resolveUploadPath(image.getThumbnailPath());
    }

    private UtilityPackageDto.ImportResult importUtilityReferencesFromPackage(String workspaceId,
                                                                              String userId,
                                                                              Path tempDir,
                                                                              ProjectPackageDto.UtilityReferences utilityReferences,
                                                                              String sourceWorkspaceName) throws IOException {
        if (utilityReferences == null) {
            return new UtilityPackageDto.ImportResult(workspaceId, 0, 0, List.of(), List.of(), Map.of());
        }

        List<UtilityPackageDto.UtilityResource> resources = new ArrayList<>();
        loadUtilityResource(tempDir, utilityReferences.codec(), resources);
        loadUtilityResource(tempDir, utilityReferences.labelSet(), resources);
        loadUtilityResource(tempDir, utilityReferences.dictionary(), resources);
        loadUtilityResource(tempDir, utilityReferences.tagSet(), resources);
        loadUtilityResource(tempDir, utilityReferences.normalizationProfile(), resources);
        loadUtilityResource(tempDir, utilityReferences.validationRuleset(), resources);

        if (resources.isEmpty()) {
            return new UtilityPackageDto.ImportResult(workspaceId, 0, 0, List.of(), List.of(), Map.of());
        }

        UtilityPackageDto.UtilityPackage utilityPackage = new UtilityPackageDto.UtilityPackage(
                new UtilityPackageDto.PackageMeta("1.0", LocalDateTime.now(), workspaceId, sourceWorkspaceName),
                resources
        );

        return utilityPackageService.importUtilityPackage(workspaceId, userId, utilityPackage);
    }

    private void loadUtilityResource(Path tempDir,
                                     ProjectPackageDto.UtilityReference utilityReference,
                                     List<UtilityPackageDto.UtilityResource> target) throws IOException {
        if (utilityReference == null || utilityReference.snapshotPath() == null) {
            return;
        }

        Path resourcePath = tempDir.resolve(archiveIoService.normalizeArchivePath(utilityReference.snapshotPath()));
        if (!Files.exists(resourcePath)) {
            return;
        }

        UtilityPackageDto.UtilityResource resource = objectMapper.readValue(resourcePath.toFile(), UtilityPackageDto.UtilityResource.class);
        target.add(resource);
    }

    private void applyUtilityReferences(Project project,
                                        Map<String, String> sourceToTarget,
                                        ProjectPackageDto.UtilityReferences utilityReferences) {
        if (utilityReferences == null) {
            return;
        }

        String codecId = mapSourceUtilityId(utilityReferences.codec(), sourceToTarget);
        if (codecId != null) {
            codecRepository.findById(codecId).ifPresent(project::setCodec);
        }

        String labelSetId = mapSourceUtilityId(utilityReferences.labelSet(), sourceToTarget);
        if (labelSetId != null) {
            labelSetRepository.findById(labelSetId).ifPresent(project::setLabelSet);
        }

        String dictionaryId = mapSourceUtilityId(utilityReferences.dictionary(), sourceToTarget);
        if (dictionaryId != null) {
            dictionaryRepository.findById(dictionaryId).ifPresent(project::setDictionary);
        }

        String tagSetId = mapSourceUtilityId(utilityReferences.tagSet(), sourceToTarget);
        if (tagSetId != null) {
            tagSetRepository.findById(tagSetId).ifPresent(project::setTagSet);
        }

        String normalizationProfileId = mapSourceUtilityId(utilityReferences.normalizationProfile(), sourceToTarget);
        if (normalizationProfileId != null) {
            normalizationProfileRepository.findById(normalizationProfileId).ifPresent(project::setNormalizationProfile);
        }

        String validationRulesetId = mapSourceUtilityId(utilityReferences.validationRuleset(), sourceToTarget);
        if (validationRulesetId != null) {
            validationRulesetRepository.findById(validationRulesetId).ifPresent(project::setValidationRuleset);
        }
    }

    private String mapSourceUtilityId(ProjectPackageDto.UtilityReference reference,
                                      Map<String, String> sourceToTarget) {
        if (reference == null || reference.sourceId() == null) {
            return null;
        }
        return sourceToTarget.get(reference.sourceId());
    }

    private Map<String, Page> importPages(Project project, List<ProjectPackageDto.PageSnapshot> pageSnapshots) {
        Map<String, Page> pageBySourceId = new LinkedHashMap<>();
        if (pageSnapshots == null) {
            return pageBySourceId;
        }

        Set<String> usedNames = new HashSet<>();
        for (ProjectPackageDto.PageSnapshot snapshot : pageSnapshots) {
            String pageName = uniquePageName(snapshot.name(), usedNames);
            usedNames.add(pageName.toLowerCase());

            Page page = new Page();
            page.setProject(project);
            page.setName(pageName);
            page.setDescription(snapshot.description());
            page.setTags(snapshot.tags() == null ? List.of() : new ArrayList<>(snapshot.tags()));
            page.setLocked(snapshot.locked());
            page.setLockedReason(snapshot.lockedReason());
            page = pageRepository.save(page);
            pageBySourceId.put(snapshot.sourcePageId(), page);
        }
        return pageBySourceId;
    }

    private Map<String, PageXml> importProjectFiles(Path tempDir,
                                                    Project project,
                                                    List<ProjectPackageDto.FileEntry> fileEntries,
                                                    Map<String, Page> pageBySourceId,
                                                    String userId) throws IOException {
        Map<String, PageXml> xmlBySourceId = new LinkedHashMap<>();

        if (fileEntries == null) {
            return xmlBySourceId;
        }

        List<ProjectPackageDto.FileEntry> sortedEntries = fileEntries.stream()
                .sorted(Comparator.comparing(ProjectPackageDto.FileEntry::archivePath))
                .toList();

        for (ProjectPackageDto.FileEntry entry : sortedEntries) {
            Page page = pageBySourceId.get(entry.sourcePageId());
            if (page == null) {
                continue;
            }

            Path sourcePath = tempDir.resolve(archiveIoService.normalizeArchivePath(entry.archivePath()));
            if (!Files.exists(sourcePath)) {
                continue;
            }

            if (entry.kind() == ProjectPackageDto.FileKind.IMAGE) {
                var stored = hierarchicalFileStorageService.storeFromPath(
                        sourcePath,
                        entry.fileName(),
                        entry.mimeType(),
                        project.getLibrary().getWorkspaceId(),
                        project.getId(),
                        de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType.IMG,
                        userId,
                        false
                );

                PageImage image = new PageImage(
                        entry.fileName(),
                        stored.storagePath(),
                        stored.mimeType(),
                        stored.sizeBytes(),
                        entry.variant(),
                        entry.baseName(),
                        page
                );

                if (entry.thumbnailArchivePath() != null) {
                    Path thumbnailSource = tempDir.resolve(archiveIoService.normalizeArchivePath(entry.thumbnailArchivePath()));
                    if (Files.exists(thumbnailSource)) {
                        var storedThumb = hierarchicalFileStorageService.storeFromPath(
                                thumbnailSource,
                                "thumb-" + entry.fileName(),
                                entry.mimeType(),
                                project.getLibrary().getWorkspaceId(),
                                project.getId(),
                                de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType.THUMB,
                                userId,
                                false
                        );
                        image.setThumbnailPath(storedThumb.storagePath());
                    }
                }

                page.getImages().add(image);
            } else if (entry.kind() == ProjectPackageDto.FileKind.XML) {
                var stored = hierarchicalFileStorageService.storeFromPath(
                        sourcePath,
                        entry.fileName(),
                        entry.mimeType(),
                        project.getLibrary().getWorkspaceId(),
                        project.getId(),
                        de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType.XML,
                        userId,
                        false
                );

                PageXml xml = new PageXml(
                        entry.fileName(),
                        stored.storagePath(),
                        stored.mimeType(),
                        stored.sizeBytes(),
                        entry.variant() == null ? "original" : entry.variant(),
                        entry.baseName() == null ? entry.fileName() : entry.baseName(),
                        entry.xmlSchema() == null ? XmlSchema.PAGE_XML : entry.xmlSchema(),
                        entry.xmlSchemaVersion(),
                        page
                );
                xml = pageXmlRepository.save(xml);
                if (xml.getSchema() == XmlSchema.PAGE_XML) {
                    pageXmlCanonicalizationService.canonicalizeAtIngest(xml, userId, "project package import");
                }
                xmlBySourceId.put(entry.sourceId(), xml);
            }
        }

        return xmlBySourceId;
    }

    private int importXmlVersions(Path tempDir,
                                  List<ProjectPackageDto.XmlVersionEntry> versionEntries,
                                  Map<String, PageXml> xmlBySourceId) throws IOException {
        if (versionEntries == null || versionEntries.isEmpty()) {
            return 0;
        }

        int importedCount = 0;
        for (ProjectPackageDto.XmlVersionEntry versionEntry : versionEntries.stream()
                .sorted(Comparator.comparing(ProjectPackageDto.XmlVersionEntry::sourceXmlId)
                        .thenComparing(ProjectPackageDto.XmlVersionEntry::versionNumber))
                .toList()) {

            PageXml targetXml = xmlBySourceId.get(versionEntry.sourceXmlId());
            if (targetXml == null) {
                continue;
            }

            Path sourcePath = tempDir.resolve(archiveIoService.normalizeArchivePath(versionEntry.archivePath()));
            if (!Files.exists(sourcePath)) {
                continue;
            }

            String targetRelative = "xml/versions/" + targetXml.getId() + "/" + versionEntry.versionNumber() + ".xml";
            Path targetAbsolute = Paths.get(uploadDir, targetRelative).normalize();
            Files.createDirectories(targetAbsolute.getParent());
            Files.copy(sourcePath, targetAbsolute, StandardCopyOption.REPLACE_EXISTING);

            PageXmlVersion version = new PageXmlVersion();
            version.setPageXml(targetXml);
            version.setVersionNumber(versionEntry.versionNumber());
            version.setFilePath(targetRelative);
            version.setFileSize(Files.size(targetAbsolute));
            version.setUserId(versionEntry.userId() == null || versionEntry.userId().isBlank() ? "import" : versionEntry.userId());
            version.setComment(versionEntry.comment());
            pageXmlVersionRepository.save(version);
            importedCount++;
        }

        return importedCount;
    }

    private String uniqueProjectName(String baseName, String libraryId) {
        String normalizedBase = (baseName == null || baseName.isBlank()) ? "Imported Project" : baseName.trim();
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
                                output.docxOptions().markUnclearWords()
                        )
                ))
                .toList();
    }

    private String writeEmbeddedOutputsJson(List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputs) {
        try {
            return objectMapper.writeValueAsString(copyEmbeddedOutputs(embeddedOutputs));
        } catch (IOException e) {
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
        } catch (IOException e) {
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

    private byte[] buildMetsXml(Project project,
                                List<Page> pages,
                                ProjectPackageDto.PackageManifest manifest,
                                List<DocumentExportService.EmbeddedProjectOutput> embeddedOutputs) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();

            String metsNs = "http://www.loc.gov/METS/";
            String xlinkNs = "http://www.w3.org/1999/xlink";
            Element mets = document.createElementNS(metsNs, "mets:mets");
            mets.setAttribute("OBJID", project.getId());
            mets.setAttribute("TYPE", "LAREX_PROJECT_PACKAGE");
            mets.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:mets", metsNs);
            mets.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xlink", xlinkNs);
            document.appendChild(mets);

            Element metsHdr = document.createElementNS(metsNs, "mets:metsHdr");
            metsHdr.setAttribute("CREATEDATE", LocalDateTime.now().toString());
            mets.appendChild(metsHdr);

            Element fileSec = document.createElementNS(metsNs, "mets:fileSec");
            mets.appendChild(fileSec);

            Element imageGroup = document.createElementNS(metsNs, "mets:fileGrp");
            imageGroup.setAttribute("USE", "ORIGINAL_IMAGES");
            fileSec.appendChild(imageGroup);

            Element pageXmlGroup = document.createElementNS(metsNs, "mets:fileGrp");
            pageXmlGroup.setAttribute("USE", "PAGE_XML");
            fileSec.appendChild(pageXmlGroup);

            Element derivativeGroup = document.createElementNS(metsNs, "mets:fileGrp");
            derivativeGroup.setAttribute("USE", "DERIVATIVES");
            fileSec.appendChild(derivativeGroup);

            Map<String, String> imageFileIdByPageId = new HashMap<>();
            Map<String, String> xmlFileIdByPageId = new HashMap<>();

            for (ProjectPackageDto.FileEntry file : manifest.files()) {
                if (file.kind() == ProjectPackageDto.FileKind.IMAGE) {
                    String fileId = "IMG_" + sanitizeMetsId(file.sourceId());
                    imageFileIdByPageId.put(file.sourcePageId(), fileId);
                    imageGroup.appendChild(createMetsFile(document, metsNs, xlinkNs, fileId, file.mimeType(), file.archivePath()));
                } else if (file.kind() == ProjectPackageDto.FileKind.XML) {
                    String fileId = "XML_" + sanitizeMetsId(file.sourceId());
                    xmlFileIdByPageId.put(file.sourcePageId(), fileId);
                    pageXmlGroup.appendChild(createMetsFile(document, metsNs, xlinkNs, fileId, file.mimeType(), file.archivePath()));
                }
            }

            List<String> derivativeFileIds = new ArrayList<>();
            for (DocumentExportService.EmbeddedProjectOutput output : embeddedOutputs) {
                String fileId = "DERIV_" + sanitizeMetsId(output.archivePath());
                derivativeFileIds.add(fileId);
                String mimeType = guessDerivativeMimeType(output.archivePath());
                derivativeGroup.appendChild(createMetsFile(document, metsNs, xlinkNs, fileId, mimeType, output.archivePath()));
            }

            Element structMap = document.createElementNS(metsNs, "mets:structMap");
            structMap.setAttribute("TYPE", "physical");
            mets.appendChild(structMap);

            Element rootDiv = document.createElementNS(metsNs, "mets:div");
            rootDiv.setAttribute("TYPE", "project");
            rootDiv.setAttribute("LABEL", project.getName());
            structMap.appendChild(rootDiv);

            for (Page page : pages) {
                Element pageDiv = document.createElementNS(metsNs, "mets:div");
                pageDiv.setAttribute("TYPE", "page");
                pageDiv.setAttribute("DMDID", sanitizeMetsId(page.getId()));
                pageDiv.setAttribute("LABEL", page.getName());
                rootDiv.appendChild(pageDiv);

                String imageFileId = imageFileIdByPageId.get(page.getId());
                if (imageFileId != null) {
                    pageDiv.appendChild(createFptr(document, metsNs, imageFileId));
                }
                String xmlFileId = xmlFileIdByPageId.get(page.getId());
                if (xmlFileId != null) {
                    pageDiv.appendChild(createFptr(document, metsNs, xmlFileId));
                }
            }

            if (!derivativeFileIds.isEmpty()) {
                Element derivativesDiv = document.createElementNS(metsNs, "mets:div");
                derivativesDiv.setAttribute("TYPE", "derivatives");
                derivativesDiv.setAttribute("LABEL", "Embedded exports");
                rootDiv.appendChild(derivativesDiv);
                for (String derivativeFileId : derivativeFileIds) {
                    derivativesDiv.appendChild(createFptr(document, metsNs, derivativeFileId));
                }
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.transform(new DOMSource(document), new StreamResult(outputStream));
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IOException("Failed to generate mets.xml", e);
        }
    }

    private Element createMetsFile(Document document,
                                   String metsNs,
                                   String xlinkNs,
                                   String id,
                                   String mimeType,
                                   String href) {
        Element file = document.createElementNS(metsNs, "mets:file");
        file.setAttribute("ID", id);
        if (mimeType != null && !mimeType.isBlank()) {
            file.setAttribute("MIMETYPE", mimeType);
        }

        Element flocat = document.createElementNS(metsNs, "mets:FLocat");
        flocat.setAttribute("LOCTYPE", "URL");
        flocat.setAttributeNS(xlinkNs, "xlink:href", href);
        file.appendChild(flocat);
        return file;
    }

    private Element createFptr(Document document, String metsNs, String fileId) {
        Element fptr = document.createElementNS(metsNs, "mets:fptr");
        fptr.setAttribute("FILEID", fileId);
        return fptr;
    }

    private String sanitizeMetsId(String value) {
        if (value == null || value.isBlank()) {
            return "ID";
        }
        return value.replaceAll("[^A-Za-z0-9_.-]+", "_");
    }

    private String guessDerivativeMimeType(String archivePath) {
        String normalized = archivePath == null ? "" : archivePath.toLowerCase();
        if (normalized.endsWith(".zip")) {
            return "application/zip";
        }
        if (normalized.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (normalized.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (normalized.endsWith(".tei.xml")) {
            return "application/tei+xml";
        }
        if (normalized.endsWith(".alto.xml") || normalized.endsWith(".xml")) {
            return "application/xml";
        }
        if (normalized.endsWith(".csv")) {
            return "text/csv";
        }
        if (normalized.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (normalized.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private long estimatePackageBytes(PackageSnapshot packageSnapshot) {
        long sourceBytes = packageSnapshot.exportBundle().manifest().files().stream().mapToLong(file -> {
            try {
                Path sourcePath = resolveUploadPath(file.archivePath(), file.kind(), file.sourceId(), packageSnapshot.exportBundle());
                return Files.size(sourcePath);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to estimate package file size", e);
            }
        }).sum();

        long versionBytes = packageSnapshot.exportBundle().manifest().xmlVersions().stream().mapToLong(entry -> {
            Path sourcePath = packageSnapshot.exportBundle().versionPathByArchivePath().get(entry.archivePath());
            if (sourcePath == null) {
                return 0L;
            }
            try {
                return Files.exists(sourcePath) ? Files.size(sourcePath) : 0L;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to estimate package version size", e);
            }
        }).sum();

        long embeddedBytes = packageSnapshot.embeddedOutputs().stream()
                .mapToLong(output -> output.bytes() == null ? 0L : output.bytes().length)
                .sum();

        return sourceBytes + versionBytes + embeddedBytes + packageSnapshot.metsBytes().length + 1_048_576L;
    }

    private byte[] createPackageBytes(PackageSnapshot packageSnapshot) throws IOException {
        return archiveIoService.createZip(zipOut -> writePackageEntries(zipOut, packageSnapshot));
    }

    private void writePackageZip(Path outputPath, PackageSnapshot packageSnapshot) throws IOException {
        archiveIoService.writeZip(outputPath, zipOut -> writePackageEntries(zipOut, packageSnapshot));
    }

    private void writePackageEntries(java.util.zip.ZipOutputStream zipOut, PackageSnapshot packageSnapshot) throws IOException {
        archiveIoService.writeJsonEntry(zipOut, "manifest.json", packageSnapshot.manifest());
        archiveIoService.writeBytesEntry(zipOut, "mets.xml", packageSnapshot.metsBytes());

        for (ProjectPackageDto.FileEntry fileEntry : packageSnapshot.manifest().files()) {
            Path source = resolveUploadPath(fileEntry.archivePath(), fileEntry.kind(), fileEntry.sourceId(), packageSnapshot.exportBundle());
            if (fileEntry.kind() == ProjectPackageDto.FileKind.XML && fileEntry.xmlSchema() == XmlSchema.PAGE_XML) {
                byte[] convertedBytes = pageXmlConversionService.convertFileToVersion(source, packageSnapshot.targetPageXmlVersion());
                archiveIoService.writeBytesEntry(zipOut, fileEntry.archivePath(), convertedBytes);
            } else {
                archiveIoService.writeFileEntry(zipOut, fileEntry.archivePath(), source);
            }

            if (fileEntry.thumbnailArchivePath() != null) {
                Path thumbnailPath = resolveThumbnailUploadPath(fileEntry.sourceId(), packageSnapshot.exportBundle());
                if (thumbnailPath != null && Files.exists(thumbnailPath)) {
                    archiveIoService.writeFileEntry(zipOut, fileEntry.thumbnailArchivePath(), thumbnailPath);
                }
            }
        }

        for (ProjectPackageDto.XmlVersionEntry versionEntry : packageSnapshot.manifest().xmlVersions()) {
            Path sourcePath = packageSnapshot.exportBundle().versionPathByArchivePath().get(versionEntry.archivePath());
            if (sourcePath != null && Files.exists(sourcePath)) {
                archiveIoService.writeFileEntry(zipOut, versionEntry.archivePath(), sourcePath);
            }
        }

        for (Map.Entry<String, UtilityPackageDto.UtilityResource> entry : packageSnapshot.exportBundle().utilityResourceByPath().entrySet()) {
            archiveIoService.writeJsonEntry(zipOut, entry.getKey(), entry.getValue());
        }

        for (DocumentExportService.EmbeddedProjectOutput output : packageSnapshot.embeddedOutputs()) {
            archiveIoService.writeBytesEntry(zipOut, output.archivePath(), output.bytes());
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
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^[-.]+|[-.]+$)", "");
        return sanitized.isBlank() ? "release" : sanitized;
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

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private record ExportBundle(
            ProjectPackageDto.PackageManifest manifest,
            Map<String, Path> filePathByArchivePath,
            Map<String, Path> versionPathByArchivePath,
            Map<String, UtilityPackageDto.UtilityResource> utilityResourceByPath,
            Map<String, PageImage> imageBySourceId
    ) {
    }

    private record PackageSnapshot(
            ExportBundle exportBundle,
            List<DocumentExportService.EmbeddedProjectOutput> embeddedOutputs,
            byte[] metsBytes,
            String targetPageXmlVersion
    ) {
        private ProjectPackageDto.PackageManifest manifest() {
            return exportBundle.manifest();
        }
    }

    public record ReleaseFileDownload(String fileName, Path absolutePath, long contentLength, String checksumSha256) {
    }

    public record SharedReleaseDownload(String fileName, Path absolutePath, long contentLength, String checksumSha256) {
    }
}
