package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import de.uniwue.zpd.dachs.larex.backend.util.ImageFileUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class LegacyOcr4allImportService {

    private final LibraryRepository libraryRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final PageFilterIndexService pageFilterIndexService;
    private final PageXmlCanonicalizationService pageXmlCanonicalizationService;

    public LegacyOcr4allImportService(LibraryRepository libraryRepository,
                                      ProjectRepository projectRepository,
                                      PageRepository pageRepository,
                                      PageXmlRepository pageXmlRepository,
                                      WorkspaceAccessService workspaceAccessService,
                                      HierarchicalFileStorageService hierarchicalFileStorageService,
                                      WorkspaceQuotaGuardService workspaceQuotaGuardService,
                                      PageFilterIndexService pageFilterIndexService,
                                      PageXmlCanonicalizationService pageXmlCanonicalizationService) {
        this.libraryRepository = libraryRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.pageFilterIndexService = pageFilterIndexService;
        this.pageXmlCanonicalizationService = pageXmlCanonicalizationService;
    }

    public ProjectPackageDto.ImportResult importProject(String workspaceId,
                                                        String userId,
                                                        List<MultipartFile> files,
                                                        List<String> relativePaths,
                                                        String projectName) throws IOException {
        workspaceAccessService.requireAdminAccess(workspaceId, userId);

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Legacy OCR4all project files are required");
        }
        if (relativePaths == null || relativePaths.size() != files.size()) {
            throw new IllegalArgumentException("A relative path is required for each uploaded file");
        }

        LegacyOcr4allScan scan = scanFiles(files, relativePaths);
        if (!scan.foundInputDirectory()) {
            throw new IllegalArgumentException("Legacy OCR4all project must contain an input directory");
        }
        if (!scan.foundProcessingDirectory()) {
            throw new IllegalArgumentException("Legacy OCR4all project must contain a processing directory");
        }
        if (scan.importFiles().isEmpty()) {
            throw new IllegalArgumentException("No supported images or XML files found in input or processing");
        }

        Set<String> pageBaseNames = pageBaseNames(scan.importFiles());
        if (pageBaseNames.isEmpty()) {
            throw new IllegalArgumentException("No pages could be derived from legacy OCR4all files");
        }
        validateSingleXmlHeadPerPage(scan.importFiles());

        long reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                workspaceId,
                scan.importFiles().stream().mapToLong(importFile -> importFile.file().getSize()).sum(),
                "legacy-ocr4all-import"
        );

        try {
            Library library = libraryRepository.findByWorkspaceId(workspaceId)
                    .orElseThrow(() -> new IllegalArgumentException("Projects not found for workspace: " + workspaceId));

            Project project = createProject(library, resolveProjectName(projectName, relativePaths));
            Map<String, Page> pagesByBaseName = createPages(project, pageBaseNames);

            int imageCount = 0;
            int xmlCount = 0;
            for (LegacyOcr4allImportFile importFile : sortImportFiles(scan.importFiles())) {
                Page page = pagesByBaseName.get(importFile.pageBaseName());
                if (page == null) {
                    continue;
                }

                if (importFile.kind() == LegacyOcr4allFileKind.IMAGE) {
                    storeImage(workspaceId, userId, project, page, importFile);
                    imageCount++;
                } else {
                    storeXml(workspaceId, userId, project, page, importFile);
                    xmlCount++;
                }
            }

            pageRepository.saveAll(pagesByBaseName.values());
            pageFilterIndexService.rebuildProjectIndex(project.getId());

            return new ProjectPackageDto.ImportResult(
                    workspaceId,
                    project.getId(),
                    project.getName(),
                    pagesByBaseName.size(),
                    imageCount,
                    xmlCount,
                    0,
                    buildWarnings(pageBaseNames, scan.originalPageBaseNames(), scan.xmlPageBaseNames(), scan.ignoredFileCount()),
                    Map.of()
            );
        } finally {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(workspaceId, reservedBytes);
        }
    }

    private LegacyOcr4allScan scanFiles(List<MultipartFile> files, List<String> relativePaths) {
        List<LegacyOcr4allImportFile> importFiles = new ArrayList<>();
        Set<String> originalPageBaseNames = new HashSet<>();
        Set<String> xmlPageBaseNames = new HashSet<>();
        boolean foundInputDirectory = false;
        boolean foundProcessingDirectory = false;
        int ignoredFileCount = 0;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file == null || file.isEmpty()) {
                continue;
            }

            String relativePath = normalizeClientRelativePath(relativePaths.get(i));
            if (relativePath.isBlank()) {
                relativePath = normalizeClientRelativePath(file.getOriginalFilename());
            }
            if (relativePath.isBlank()) {
                ignoredFileCount++;
                continue;
            }

            List<String> segments = pathSegments(relativePath);
            String fileName = fileName(relativePath);
            int inputIndex = indexOfSegment(segments, "input");
            int processingIndex = indexOfSegment(segments, "processing");

            if (inputIndex >= 0 && inputIndex < segments.size() - 1) {
                foundInputDirectory = true;
                if (isImageFileName(fileName)) {
                    String baseName = ImageFileUtils.parseImageName(fileName).baseName();
                    importFiles.add(new LegacyOcr4allImportFile(file, relativePath, LegacyOcr4allFileKind.IMAGE, baseName, "original"));
                    originalPageBaseNames.add(baseName);
                } else {
                    ignoredFileCount++;
                }
            } else if (processingIndex >= 0 && processingIndex < segments.size() - 1) {
                foundProcessingDirectory = true;
                if (isImageFileName(fileName)) {
                    ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(fileName);
                    importFiles.add(new LegacyOcr4allImportFile(
                            file,
                            relativePath,
                            LegacyOcr4allFileKind.IMAGE,
                            nameInfo.baseName(),
                            deriveProcessingVariant(segments, processingIndex, nameInfo.variant())
                    ));
                } else if (isXmlFileName(fileName)) {
                    String baseName = xmlBaseName(fileName);
                    importFiles.add(new LegacyOcr4allImportFile(
                            file,
                            relativePath,
                            LegacyOcr4allFileKind.XML,
                            baseName,
                            deriveProcessingVariant(segments, processingIndex, "original")
                    ));
                    xmlPageBaseNames.add(baseName);
                } else {
                    ignoredFileCount++;
                }
            } else {
                ignoredFileCount++;
            }
        }

        return new LegacyOcr4allScan(
                importFiles,
                originalPageBaseNames,
                xmlPageBaseNames,
                foundInputDirectory,
                foundProcessingDirectory,
                ignoredFileCount
        );
    }

    private Set<String> pageBaseNames(List<LegacyOcr4allImportFile> importFiles) {
        Set<String> pageBaseNames = new TreeSet<>();
        for (LegacyOcr4allImportFile importFile : importFiles) {
            if (importFile.pageBaseName() != null && !importFile.pageBaseName().isBlank()) {
                pageBaseNames.add(importFile.pageBaseName());
            }
        }
        return pageBaseNames;
    }

    private Project createProject(Library library, String requestedProjectName) {
        Project project = new Project();
        project.setLibrary(library);
        project.setName(uniqueProjectName(requestedProjectName, library.getId()));
        project.setDescription("Imported from a legacy OCR4all project.");
        project.setTags(new ArrayList<>());
        return projectRepository.save(project);
    }

    private Map<String, Page> createPages(Project project, Set<String> pageBaseNames) {
        Map<String, Page> pagesByBaseName = new LinkedHashMap<>();
        Set<String> usedPageNames = new HashSet<>();
        for (String baseName : pageBaseNames) {
            String pageName = uniquePageName(baseName, usedPageNames);
            usedPageNames.add(pageName.toLowerCase(Locale.ROOT));

            Page page = new Page();
            page.setProject(project);
            page.setName(pageName);
            page.setDescription("Imported from legacy OCR4all project");
            page.setTags(new ArrayList<>());
            page = pageRepository.save(page);
            pagesByBaseName.put(baseName, page);
        }
        return pagesByBaseName;
    }

    private List<LegacyOcr4allImportFile> sortImportFiles(List<LegacyOcr4allImportFile> importFiles) {
        return importFiles.stream()
                .sorted(Comparator.comparing(LegacyOcr4allImportFile::pageBaseName)
                        .thenComparing(importFile -> importFile.kind().ordinal())
                        .thenComparing(LegacyOcr4allImportFile::relativePath))
                .toList();
    }

    private void storeImage(String workspaceId,
                            String userId,
                            Project project,
                            Page page,
                            LegacyOcr4allImportFile importFile) throws IOException {
        var storedImage = hierarchicalFileStorageService.storeMultipartFile(
                importFile.file(),
                workspaceId,
                project.getId(),
                StoredFileType.IMG,
                userId
        );

        PageImage pageImage = new PageImage(
                storedImage.originalFilename(),
                storedImage.storagePath(),
                storedImage.mimeType(),
                storedImage.sizeBytes(),
                importFile.variant(),
                importFile.pageBaseName(),
                page
        );
        page.getImages().add(pageImage);
    }

    private void storeXml(String workspaceId,
                          String userId,
                          Project project,
                          Page page,
                          LegacyOcr4allImportFile importFile) throws IOException {
        var storedXml = hierarchicalFileStorageService.storeMultipartFile(
                importFile.file(),
                workspaceId,
                project.getId(),
                StoredFileType.XML,
                userId
        );

        PageXml pageXml = new PageXml(
                storedXml.originalFilename(),
                storedXml.storagePath(),
                storedXml.mimeType(),
                storedXml.sizeBytes(),
                importFile.variant(),
                importFile.pageBaseName(),
                XmlSchema.PAGE_XML,
                null,
                page
        );
        pageXml = pageXmlRepository.save(pageXml);
        pageXmlCanonicalizationService.canonicalizeAtIngest(pageXml, userId, "legacy OCR4all import");
    }

    private void validateSingleXmlHeadPerPage(List<LegacyOcr4allImportFile> importFiles) {
        Map<String, Long> xmlCountsByPage = importFiles.stream()
                .filter(importFile -> importFile.kind() == LegacyOcr4allFileKind.XML)
                .collect(java.util.stream.Collectors.groupingBy(
                        LegacyOcr4allImportFile::pageBaseName,
                        java.util.stream.Collectors.counting()
                ));
        xmlCountsByPage.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .findFirst()
                .ifPresent(entry -> {
                    throw new IllegalArgumentException(
                            "Legacy OCR4all page '" + entry.getKey() + "' contains more than one head XML file"
                    );
                });
    }

    private String normalizeClientRelativePath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.replace('\\', '/').trim();
        String[] rawSegments = normalized.split("/");
        List<String> safeSegments = new ArrayList<>();
        for (String rawSegment : rawSegments) {
            String segment = rawSegment.trim();
            if (segment.isBlank() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("Relative upload path must not contain '..'");
            }
            safeSegments.add(segment);
        }
        return String.join("/", safeSegments);
    }

    private List<String> pathSegments(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return List.of();
        }
        return List.of(normalizedPath.split("/"));
    }

    private int indexOfSegment(List<String> segments, String expected) {
        for (int index = 0; index < segments.size(); index++) {
            if (expected.equalsIgnoreCase(segments.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private String fileName(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return "";
        }
        int slashIndex = normalizedPath.lastIndexOf('/');
        return slashIndex >= 0 ? normalizedPath.substring(slashIndex + 1) : normalizedPath;
    }

    private boolean isImageFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".gif")
                || lowerName.endsWith(".bmp")
                || lowerName.endsWith(".tif")
                || lowerName.endsWith(".tiff")
                || lowerName.endsWith(".webp");
    }

    private boolean isXmlFileName(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".xml");
    }

    private String xmlBaseName(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim();
        String lowerName = normalized.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".xml")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        int firstDotIndex = normalized.indexOf('.');
        if (firstDotIndex > 0) {
            return normalized.substring(0, firstDotIndex);
        }
        return normalized.isBlank() ? "page" : normalized;
    }

    private String deriveProcessingVariant(List<String> segments, int processingIndex, String fallback) {
        if (processingIndex >= 0 && processingIndex < segments.size() - 2) {
            return sanitizeVariant(
                    String.join(".", segments.subList(processingIndex + 1, segments.size() - 1)),
                    fallback
            );
        }
        return sanitizeVariant(fallback, "processing");
    }

    private String sanitizeVariant(String value, String fallback) {
        String normalized = value == null ? "" : value.trim()
                .replace('\\', '.')
                .replace('/', '.')
                .replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            normalized = fallback == null || fallback.isBlank() ? "processing" : fallback.trim();
        }
        if (normalized.isBlank()) {
            normalized = "processing";
        }
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String resolveProjectName(String requestedProjectName, List<String> relativePaths) {
        if (requestedProjectName != null && !requestedProjectName.isBlank()) {
            return requestedProjectName.trim();
        }
        if (relativePaths != null) {
            for (String path : relativePaths) {
                String normalized = normalizeClientRelativePath(path);
                List<String> segments = pathSegments(normalized);
                if (!segments.isEmpty()) {
                    String firstSegment = segments.get(0);
                    if (!"input".equalsIgnoreCase(firstSegment) && !"processing".equalsIgnoreCase(firstSegment)) {
                        return firstSegment;
                    }
                }
            }
        }
        return "Legacy OCR4all Project";
    }

    private List<String> buildWarnings(Set<String> pageBaseNames,
                                       Set<String> originalPageBaseNames,
                                       Set<String> xmlPageBaseNames,
                                       int ignoredFileCount) {
        List<String> warnings = new ArrayList<>();
        if (ignoredFileCount > 0) {
            warnings.add("Ignored " + ignoredFileCount + " unsupported file" + (ignoredFileCount == 1 ? "" : "s") + ".");
        }

        long pagesWithoutOriginal = pageBaseNames.stream()
                .filter(baseName -> !originalPageBaseNames.contains(baseName))
                .count();
        if (pagesWithoutOriginal > 0) {
            warnings.add(pagesWithoutOriginal + " page" + (pagesWithoutOriginal == 1 ? " has" : "s have") + " no original input image.");
        }

        long pagesWithoutXml = pageBaseNames.stream()
                .filter(baseName -> !xmlPageBaseNames.contains(baseName))
                .count();
        if (pagesWithoutXml > 0) {
            warnings.add(pagesWithoutXml + " page" + (pagesWithoutXml == 1 ? " has" : "s have") + " no XML file.");
        }
        return warnings;
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

    private String uniquePageName(String baseName, Set<String> usedLowerCaseNames) {
        String normalizedBase = (baseName == null || baseName.isBlank()) ? "Imported Page" : baseName.trim();
        String lowerBase = normalizedBase.toLowerCase(Locale.ROOT);
        if (!usedLowerCaseNames.contains(lowerBase)) {
            return normalizedBase;
        }

        String candidate = normalizedBase + " (imported)";
        if (!usedLowerCaseNames.contains(candidate.toLowerCase(Locale.ROOT))) {
            return candidate;
        }

        int index = 2;
        while (index < 10_000) {
            String next = normalizedBase + " (imported " + index + ")";
            if (!usedLowerCaseNames.contains(next.toLowerCase(Locale.ROOT))) {
                return next;
            }
            index++;
        }

        return normalizedBase + " (" + UUID.randomUUID() + ")";
    }

    private enum LegacyOcr4allFileKind {
        IMAGE,
        XML
    }

    private record LegacyOcr4allImportFile(
            MultipartFile file,
            String relativePath,
            LegacyOcr4allFileKind kind,
            String pageBaseName,
            String variant
    ) {
    }

    private record LegacyOcr4allScan(
            List<LegacyOcr4allImportFile> importFiles,
            Set<String> originalPageBaseNames,
            Set<String> xmlPageBaseNames,
            boolean foundInputDirectory,
            boolean foundProcessingDirectory,
            int ignoredFileCount
    ) {
    }
}
