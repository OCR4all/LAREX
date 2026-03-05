package de.uniwue.zpd.dachs.larex.backend.service.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UtilityPackageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlVersionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.StorageTrackingService;
import de.uniwue.zpd.dachs.larex.backend.service.utility.UtilityPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class ProjectPackageService {

    private final ProjectRepository projectRepository;
    private final LibraryRepository libraryRepository;
    private final PageRepository pageRepository;
    private final PageXmlVersionRepository pageXmlVersionRepository;
    private final CodecRepository codecRepository;
    private final LabelSetRepository labelSetRepository;
    private final TagSetRepository tagSetRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final ArchiveIoService archiveIoService;
    private final UtilityPackageService utilityPackageService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final PageFilterIndexService pageFilterIndexService;
    private final StorageTrackingService storageTrackingService;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public ProjectPackageService(ProjectRepository projectRepository,
                                 LibraryRepository libraryRepository,
                                 PageRepository pageRepository,
                                 PageXmlVersionRepository pageXmlVersionRepository,
                                 CodecRepository codecRepository,
                                 LabelSetRepository labelSetRepository,
                                 TagSetRepository tagSetRepository,
                                 WorkspaceAccessService workspaceAccessService,
                                 ArchiveIoService archiveIoService,
                                 UtilityPackageService utilityPackageService,
                                 HierarchicalFileStorageService hierarchicalFileStorageService,
                                 PageFilterIndexService pageFilterIndexService,
                                 StorageTrackingService storageTrackingService,
                                 ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.libraryRepository = libraryRepository;
        this.pageRepository = pageRepository;
        this.pageXmlVersionRepository = pageXmlVersionRepository;
        this.codecRepository = codecRepository;
        this.labelSetRepository = labelSetRepository;
        this.tagSetRepository = tagSetRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.archiveIoService = archiveIoService;
        this.utilityPackageService = utilityPackageService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.pageFilterIndexService = pageFilterIndexService;
        this.storageTrackingService = storageTrackingService;
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
        Project project = projectRepository.findWithAssociationsById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (!workspaceId.equals(project.getLibrary().getWorkspaceId())) {
            throw new IllegalArgumentException("Project does not belong to workspace");
        }

        List<Page> pages = resolvePagesForExport(projectId, request == null ? null : request.pageIds());
        ExportBundle exportBundle = buildExportBundle(project, pages);

        return archiveIoService.createZip(zipOut -> {
            archiveIoService.writeJsonEntry(zipOut, "manifest.json", exportBundle.manifest());

            for (ProjectPackageDto.FileEntry fileEntry : exportBundle.manifest().files()) {
                Path source = resolveUploadPath(fileEntry.archivePath(), fileEntry.kind(), fileEntry.sourceId(), exportBundle);
                archiveIoService.writeFileEntry(zipOut, fileEntry.archivePath(), source);

                if (fileEntry.thumbnailArchivePath() != null) {
                    Path thumbnailPath = resolveThumbnailUploadPath(fileEntry.sourceId(), exportBundle);
                    if (thumbnailPath != null && Files.exists(thumbnailPath)) {
                        archiveIoService.writeFileEntry(zipOut, fileEntry.thumbnailArchivePath(), thumbnailPath);
                    }
                }
            }

            for (ProjectPackageDto.XmlVersionEntry versionEntry : exportBundle.manifest().xmlVersions()) {
                Path sourcePath = exportBundle.versionPathByArchivePath().get(versionEntry.archivePath());
                if (sourcePath != null && Files.exists(sourcePath)) {
                    archiveIoService.writeFileEntry(zipOut, versionEntry.archivePath(), sourcePath);
                }
            }

            for (Map.Entry<String, UtilityPackageDto.UtilityResource> entry : exportBundle.utilityResourceByPath().entrySet()) {
                archiveIoService.writeJsonEntry(zipOut, entry.getKey(), entry.getValue());
            }
        });
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
            storageTrackingService.syncWorkspaceUsage(workspaceId);

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
            deleteDirectoryQuietly(tempDir);
        }
    }

    private ExportBundle buildExportBundle(Project project, List<Page> pages) throws IOException {
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
                        xml.getSchemaVersion(),
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
                project.getTagSet() == null ? null : project.getTagSet().getId()
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
                utilityRefByType.get(UtilityPackageDto.UtilityType.TAG_SET)
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
                List.of()
        );

        return new ExportBundle(manifest, fileSourcePathByArchivePath, versionPathByArchivePath, utilityResourcesByPath, imageBySourceId);
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
        loadUtilityResource(tempDir, utilityReferences.tagSet(), resources);

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

        String tagSetId = mapSourceUtilityId(utilityReferences.tagSet(), sourceToTarget);
        if (tagSetId != null) {
            tagSetRepository.findById(tagSetId).ifPresent(project::setTagSet);
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
                page.getXmlFiles().add(xml);
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
}
