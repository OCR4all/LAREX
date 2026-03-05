package de.uniwue.zpd.dachs.larex.backend.service.storage;

import de.uniwue.zpd.dachs.larex.backend.dto.StorageCleanupDto;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class StorageCleanupService {

    private static final Logger log = LoggerFactory.getLogger(StorageCleanupService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final PageImageRepository pageImageRepository;
    private final PageRepository pageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final PageXmlVersionRepository pageXmlVersionRepository;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${larex.upload.temp-directory:${file.upload-dir}/temp}")
    private String tempDirectory;

    public StorageCleanupService(PageImageRepository pageImageRepository,
                                 PageRepository pageRepository,
                                 PageXmlRepository pageXmlRepository,
                                 PageXmlVersionRepository pageXmlVersionRepository,
                                 HierarchicalFileStorageService hierarchicalFileStorageService) {
        this.pageImageRepository = pageImageRepository;
        this.pageRepository = pageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.pageXmlVersionRepository = pageXmlVersionRepository;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
    }

    /**
     * Get an overview of storage usage and orphaned files.
     */
    public StorageCleanupDto.StorageOverview getStorageOverview() {
        // Get counts from database
        long totalImages = pageImageRepository.count();
        long totalXmlFiles = pageXmlRepository.count();

        // Get all referenced paths from database
        Set<String> referencedImagePaths = new HashSet<>(pageImageRepository.findAllFilePaths());
        Set<String> referencedThumbnailPaths = new HashSet<>(pageImageRepository.findAllThumbnailPaths());
        Set<String> referencedXmlPaths = new HashSet<>(pageXmlRepository.findAllFilePaths());
        Set<String> referencedXmlVersionPaths = new HashSet<>(pageXmlVersionRepository.findAllFilePaths());

        // Scan directories and find orphans
        List<StorageCleanupDto.OrphanedFile> orphanedImages = new ArrayList<>();
        orphanedImages.addAll(scanForOrphanedFiles("images", referencedImagePaths, "image"));
        orphanedImages.addAll(scanForWorkspaceTypeOrphanedFiles("img", referencedImagePaths, "image"));

        List<StorageCleanupDto.OrphanedFile> orphanedXml = new ArrayList<>();
        orphanedXml.addAll(scanForOrphanedFiles("xml", union(referencedXmlPaths, referencedXmlVersionPaths), "xml"));
        orphanedXml.addAll(scanForWorkspaceTypeOrphanedFiles("xml", union(referencedXmlPaths, referencedXmlVersionPaths), "xml"));

        List<StorageCleanupDto.OrphanedFile> orphanedThumbnails = new ArrayList<>();
        orphanedThumbnails.addAll(scanForOrphanedFiles("thumbnails", referencedThumbnailPaths, "thumbnail"));
        orphanedThumbnails.addAll(scanForWorkspaceTypeOrphanedFiles("thumb", referencedThumbnailPaths, "thumbnail"));
        List<StorageCleanupDto.OrphanedFile> orphanedTemp = scanTempDirectory();

        // Count thumbnails on disk
        int totalThumbnails = countFilesInDirectory("thumbnails") + countFilesInWorkspaceType("thumb");

        // Calculate totals
        long orphanedTotalBytes = 0;
        orphanedTotalBytes += orphanedImages.stream().mapToLong(StorageCleanupDto.OrphanedFile::sizeBytes).sum();
        orphanedTotalBytes += orphanedXml.stream().mapToLong(StorageCleanupDto.OrphanedFile::sizeBytes).sum();
        orphanedTotalBytes += orphanedThumbnails.stream().mapToLong(StorageCleanupDto.OrphanedFile::sizeBytes).sum();
        orphanedTotalBytes += orphanedTemp.stream().mapToLong(StorageCleanupDto.OrphanedFile::sizeBytes).sum();

        long totalUsedBytes = calculateDirectorySize(Paths.get(uploadDir));

        return new StorageCleanupDto.StorageOverview(
                totalUsedBytes,
                formatBytes(totalUsedBytes),
                (int) totalImages,
                (int) totalXmlFiles,
                totalThumbnails,
                orphanedImages.size(),
                orphanedXml.size(),
                orphanedThumbnails.size(),
                orphanedTemp.size(),
                orphanedTotalBytes,
                formatBytes(orphanedTotalBytes)
        );
    }

    /**
     * Get list of all orphaned files.
     */
    public StorageCleanupDto.OrphanedFilesResponse getOrphanedFiles() {
        return getOrphanedFiles(null, null, null, null);
    }

    /**
     * Get orphaned files with optional filtering and pagination.
     */
    public StorageCleanupDto.OrphanedFilesResponse getOrphanedFiles(
            Integer page,
            Integer size,
            String type,
            String search) {
        // Get all referenced paths from database
        Set<String> referencedImagePaths = new HashSet<>(pageImageRepository.findAllFilePaths());
        Set<String> referencedThumbnailPaths = new HashSet<>(pageImageRepository.findAllThumbnailPaths());
        Set<String> referencedXmlPaths = new HashSet<>(pageXmlRepository.findAllFilePaths());
        Set<String> referencedXmlVersionPaths = new HashSet<>(pageXmlVersionRepository.findAllFilePaths());

        // Scan directories
        List<StorageCleanupDto.OrphanedFile> allOrphaned = new ArrayList<>();
        allOrphaned.addAll(scanForOrphanedFiles("images", referencedImagePaths, "image"));
        allOrphaned.addAll(scanForWorkspaceTypeOrphanedFiles("img", referencedImagePaths, "image"));
        allOrphaned.addAll(scanForOrphanedFiles("xml", union(referencedXmlPaths, referencedXmlVersionPaths), "xml"));
        allOrphaned.addAll(scanForWorkspaceTypeOrphanedFiles("xml", union(referencedXmlPaths, referencedXmlVersionPaths), "xml"));
        allOrphaned.addAll(scanForOrphanedFiles("thumbnails", referencedThumbnailPaths, "thumbnail"));
        allOrphaned.addAll(scanForWorkspaceTypeOrphanedFiles("thumb", referencedThumbnailPaths, "thumbnail"));
        allOrphaned.addAll(scanTempDirectory());

        String normalizedType = type != null ? type.trim().toLowerCase(Locale.ROOT) : null;
        String normalizedSearch = search != null ? search.trim().toLowerCase(Locale.ROOT) : null;

        List<StorageCleanupDto.OrphanedFile> filteredOrphaned = allOrphaned.stream()
                .filter(file -> normalizedType == null || normalizedType.isEmpty() || file.type().equalsIgnoreCase(normalizedType))
                .filter(file -> normalizedSearch == null || normalizedSearch.isEmpty()
                        || file.path().toLowerCase(Locale.ROOT).contains(normalizedSearch))
                .sorted(Comparator.comparing(StorageCleanupDto.OrphanedFile::path))
                .toList();

        int totalCount = filteredOrphaned.size();
        long totalSize = filteredOrphaned.stream().mapToLong(StorageCleanupDto.OrphanedFile::sizeBytes).sum();

        List<StorageCleanupDto.OrphanedFile> paginatedFiles = filteredOrphaned;
        if (page != null && size != null && page > 0 && size > 0) {
            int fromIndex = Math.max(0, (page - 1) * size);
            int toIndex = Math.min(fromIndex + size, totalCount);
            paginatedFiles = fromIndex >= totalCount ? List.of() : filteredOrphaned.subList(fromIndex, toIndex);
        }

        return new StorageCleanupDto.OrphanedFilesResponse(
                paginatedFiles,
                totalCount,
                totalSize,
                formatBytes(totalSize)
        );
    }

    /**
     * Delete specified orphaned files.
     */
    public StorageCleanupDto.CleanupResponse deleteOrphanedFiles(List<String> paths) {
        // Get all referenced paths to ensure we don't delete active files
        Set<String> referencedPaths = new HashSet<>();
        referencedPaths.addAll(pageImageRepository.findAllFilePaths());
        referencedPaths.addAll(pageImageRepository.findAllThumbnailPaths());
        referencedPaths.addAll(pageXmlRepository.findAllFilePaths());

        int deletedCount = 0;
        int failedCount = 0;
        long freedBytes = 0;
        List<String> errors = new ArrayList<>();

        for (String relativePath : paths) {
            // Security check: ensure the path is within upload directory
            Path fullPath = Paths.get(uploadDir, relativePath).normalize();
            if (!fullPath.startsWith(Paths.get(uploadDir).normalize())) {
                errors.add("Invalid path (outside upload directory): " + relativePath);
                failedCount++;
                continue;
            }

            // Check if file is still referenced (race condition protection)
            if (referencedPaths.contains(relativePath)) {
                errors.add("File is still referenced: " + relativePath);
                failedCount++;
                continue;
            }

            try {
                if (Files.exists(fullPath)) {
                    long fileSize = Files.size(fullPath);
                    if (hierarchicalFileStorageService.deleteStoredFile(relativePath)) {
                        freedBytes += fileSize;
                        deletedCount++;
                        log.info("Deleted orphaned file: {}", fullPath);
                    } else {
                        errors.add("Failed to delete: " + relativePath);
                        failedCount++;
                    }
                } else {
                    errors.add("File not found: " + relativePath);
                    failedCount++;
                }
            } catch (IOException e) {
                errors.add("Failed to delete " + relativePath + ": " + e.getMessage());
                failedCount++;
                log.error("Failed to delete orphaned file: {}", fullPath, e);
            }
        }

        return new StorageCleanupDto.CleanupResponse(
                deletedCount,
                failedCount,
                freedBytes,
                formatBytes(freedBytes),
                errors
        );
    }

    /**
     * Delete all orphaned files.
     */
    public StorageCleanupDto.CleanupResponse deleteAllOrphanedFiles() {
        StorageCleanupDto.OrphanedFilesResponse orphaned = getOrphanedFiles();
        List<String> paths = orphaned.files().stream()
                .map(StorageCleanupDto.OrphanedFile::path)
                .toList();
        return deleteOrphanedFiles(paths);
    }

    private List<StorageCleanupDto.OrphanedFile> scanForOrphanedFiles(String subDir, Set<String> referencedPaths, String type) {
        List<StorageCleanupDto.OrphanedFile> orphaned = new ArrayList<>();
        Path dirPath = Paths.get(uploadDir, subDir);

        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            return orphaned;
        }

        try (Stream<Path> files = Files.walk(dirPath)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String relativePath = normalizeRelativePath(Paths.get(uploadDir).relativize(file));
                if (!referencedPaths.contains(relativePath)) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                        orphaned.add(new StorageCleanupDto.OrphanedFile(
                                relativePath,
                                type,
                                attrs.size(),
                                DATE_FORMATTER.format(attrs.lastModifiedTime().toInstant())
                        ));
                    } catch (IOException e) {
                        log.warn("Failed to read file attributes: {}", file, e);
                    }
                }
            });
        } catch (IOException e) {
            log.error("Failed to scan directory: {}", dirPath, e);
        }

        return orphaned;
    }

    private List<StorageCleanupDto.OrphanedFile> scanForWorkspaceTypeOrphanedFiles(String typeFolder, Set<String> referencedPaths, String type) {
        List<StorageCleanupDto.OrphanedFile> orphaned = new ArrayList<>();
        Path wsRoot = Paths.get(uploadDir, "ws");

        if (!Files.exists(wsRoot) || !Files.isDirectory(wsRoot)) {
            return orphaned;
        }

        try (Stream<Path> files = Files.walk(wsRoot)) {
            files.filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relativePath = normalizeRelativePath(Paths.get(uploadDir).relativize(file));
                        if (!isPathInTypeFolder(relativePath, typeFolder)) {
                            return;
                        }

                        if (!referencedPaths.contains(relativePath)) {
                            try {
                                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                                orphaned.add(new StorageCleanupDto.OrphanedFile(
                                        relativePath,
                                        type,
                                        attrs.size(),
                                        DATE_FORMATTER.format(attrs.lastModifiedTime().toInstant())
                                ));
                            } catch (IOException e) {
                                log.warn("Failed to read file attributes: {}", file, e);
                            }
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to scan workspace type folder: {}", typeFolder, e);
        }

        return orphaned;
    }

    private List<StorageCleanupDto.OrphanedFile> scanTempDirectory() {
        List<StorageCleanupDto.OrphanedFile> orphaned = new ArrayList<>();
        Path tempPath = Paths.get(tempDirectory);

        if (!Files.exists(tempPath) || !Files.isDirectory(tempPath)) {
            return orphaned;
        }

        try (Stream<Path> files = Files.walk(tempPath)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try {
                    String relativePath = normalizeRelativePath(Paths.get(uploadDir).relativize(file));
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    orphaned.add(new StorageCleanupDto.OrphanedFile(
                            relativePath,
                            "temp",
                            attrs.size(),
                            DATE_FORMATTER.format(attrs.lastModifiedTime().toInstant())
                    ));
                } catch (IOException e) {
                    log.warn("Failed to read file attributes: {}", file, e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to scan temp directory: {}", tempPath, e);
        }

        return orphaned;
    }

    private int countFilesInDirectory(String subDir) {
        Path dirPath = Paths.get(uploadDir, subDir);
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            return 0;
        }

        try (Stream<Path> files = Files.walk(dirPath)) {
            return (int) files.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            log.error("Failed to count files in directory: {}", dirPath, e);
            return 0;
        }
    }

    private int countFilesInWorkspaceType(String typeFolder) {
        Path wsRoot = Paths.get(uploadDir, "ws");
        if (!Files.exists(wsRoot) || !Files.isDirectory(wsRoot)) {
            return 0;
        }

        try (Stream<Path> files = Files.walk(wsRoot)) {
            return (int) files
                    .filter(Files::isRegularFile)
                    .map(path -> normalizeRelativePath(Paths.get(uploadDir).relativize(path)))
                    .filter(relativePath -> isPathInTypeFolder(relativePath, typeFolder))
                    .count();
        } catch (IOException e) {
            log.error("Failed to count files in workspace type folder: {}", typeFolder, e);
            return 0;
        }
    }

    private long calculateDirectorySize(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return 0;
        }

        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.error("Failed to calculate directory size: {}", dir, e);
            return 0;
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private Set<String> union(Set<String> first, Set<String> second) {
        Set<String> union = new HashSet<>(first);
        union.addAll(second);
        return union;
    }

    private String normalizeRelativePath(Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }

    private boolean isPathInTypeFolder(String relativePath, String typeFolder) {
        return relativePath.contains("/" + typeFolder + "/");
    }
}
