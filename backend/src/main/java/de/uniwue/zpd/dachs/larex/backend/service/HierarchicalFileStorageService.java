package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.repository.StoredFileRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class HierarchicalFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(HierarchicalFileStorageService.class);

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");
    private static final Pattern FILENAME_SAFE_CHARS = Pattern.compile("[^A-Za-z0-9._ -]");
    private static final Pattern SEGMENT_SAFE_CHARS = Pattern.compile("[^A-Za-z0-9._-]");

    private static final Map<String, String> IMAGE_MIME_TO_EXT = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/bmp", "bmp",
            "image/tiff", "tiff",
            "image/webp", "webp"
    );

    private static final Map<String, String> EXT_TO_IMAGE_MIME = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "bmp", "image/bmp",
            "tif", "image/tiff",
            "tiff", "image/tiff",
            "webp", "image/webp"
    );

    private static final Set<String> XML_MIMES = Set.of("application/xml", "text/xml");

    private final StoredFileRepository storedFileRepository;
    private final UploadDirectoryPreflightService uploadDirectoryPreflightService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private Path uploadRoot;

    public HierarchicalFileStorageService(StoredFileRepository storedFileRepository,
                                          UploadDirectoryPreflightService uploadDirectoryPreflightService) {
        this.storedFileRepository = storedFileRepository;
        this.uploadDirectoryPreflightService = uploadDirectoryPreflightService;
    }

    @PostConstruct
    private void initUploadRoot() {
        uploadDirectoryPreflightService.ensureDirectoriesReady();
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
            log.info("HierarchicalFileStorageService initialized at {}", uploadRoot);
        } catch (IOException e) {
            log.warn("Could not initialize upload directory {}. File operations may fail until the path is writable.", uploadRoot);
        }
    }

    @Transactional
    public StoredFileDescriptor storeMultipartFile(
            MultipartFile file,
            String workspaceId,
            String projectId,
            StoredFileType fileType,
            String createdBy
    ) throws IOException {
        String sanitizedOriginalName = sanitizeOriginalFilename(file.getOriginalFilename());
        String mimeType = resolveMimeType(fileType, file.getContentType(), sanitizedOriginalName, null);
        String extension = resolveExtension(fileType, mimeType);

        String storageUuid = generateStorageUuid();
        String storagePath = buildStoragePath(workspaceId, projectId, fileType, storageUuid, extension);
        Path absolutePath = resolveUploadPath(storagePath);

        Files.createDirectories(absolutePath.getParent());
        file.transferTo(absolutePath.toFile());

        return persistStoredFile(
                storageUuid,
                workspaceId,
                projectId,
                fileType,
                storagePath,
                sanitizedOriginalName,
                mimeType,
                extension,
                createdBy,
                absolutePath
        );
    }

    @Transactional
    public StoredFileDescriptor storeFromPath(
            Path sourcePath,
            String originalFilename,
            String declaredMimeType,
            String workspaceId,
            String projectId,
            StoredFileType fileType,
            String createdBy,
            boolean moveSource
    ) throws IOException {
        Path normalizedSource = sourcePath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedSource) || !Files.isRegularFile(normalizedSource)) {
            throw new IOException("Source file does not exist: " + normalizedSource);
        }

        String sanitizedOriginalName = sanitizeOriginalFilename(originalFilename);
        String mimeType = resolveMimeType(fileType, declaredMimeType, sanitizedOriginalName, normalizedSource);
        String extension = resolveExtension(fileType, mimeType);

        String storageUuid = generateStorageUuid();
        String storagePath = buildStoragePath(workspaceId, projectId, fileType, storageUuid, extension);
        Path absolutePath = resolveUploadPath(storagePath);

        Files.createDirectories(absolutePath.getParent());
        if (moveSource) {
            Files.move(normalizedSource, absolutePath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(normalizedSource, absolutePath, StandardCopyOption.REPLACE_EXISTING);
        }

        return persistStoredFile(
                storageUuid,
                workspaceId,
                projectId,
                fileType,
                storagePath,
                sanitizedOriginalName,
                mimeType,
                extension,
                createdBy,
                absolutePath
        );
    }

    @Transactional
    public StoredFileDescriptor storeBufferedImage(
            BufferedImage image,
            String formatName,
            String originalFilename,
            String workspaceId,
            String projectId,
            String createdBy
    ) throws IOException {
        String normalizedFormat = formatName == null ? "" : formatName.toLowerCase(Locale.ROOT);
        String mimeType = switch (normalizedFormat) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            case "webp" -> "image/webp";
            default -> throw new IllegalArgumentException("Unsupported image format: " + formatName);
        };

        String sanitizedOriginalName = sanitizeOriginalFilename(originalFilename);
        String extension = resolveExtension(StoredFileType.IMG, mimeType);
        String storageUuid = generateStorageUuid();
        String storagePath = buildStoragePath(workspaceId, projectId, StoredFileType.IMG, storageUuid, extension);
        Path absolutePath = resolveUploadPath(storagePath);

        Files.createDirectories(absolutePath.getParent());
        boolean writeSucceeded = ImageIO.write(image, normalizedFormat, absolutePath.toFile());
        if (!writeSucceeded) {
            throw new IOException("Unable to encode image format: " + formatName);
        }

        return persistStoredFile(
                storageUuid,
                workspaceId,
                projectId,
                StoredFileType.IMG,
                storagePath,
                sanitizedOriginalName,
                mimeType,
                extension,
                createdBy,
                absolutePath
        );
    }

    @Transactional
    public boolean deleteStoredFile(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return false;
        }

        Path absolutePath;
        try {
            absolutePath = resolveUploadPath(storagePath);
        } catch (IllegalArgumentException e) {
            log.warn("Skipping delete for invalid storage path {}: {}", storagePath, e.getMessage());
            return false;
        }

        boolean deleted = false;
        try {
            deleted = Files.deleteIfExists(absolutePath);
        } catch (IOException e) {
            log.warn("Failed deleting storage file {}", absolutePath, e);
        }

        markDeletedByStoragePath(storagePath);

        if (deleted) {
            cleanupEmptyAncestorDirectories(absolutePath.getParent());
        }

        return deleted;
    }

    @Transactional
    public boolean deleteProjectTree(String workspaceId, String projectId) {
        String projectRootRelative = projectRootRelativePath(workspaceId, projectId);
        Path projectRoot;

        try {
            projectRoot = resolveUploadPath(projectRootRelative);
        } catch (IllegalArgumentException e) {
            log.warn("Skipping project tree delete for invalid path {}: {}", projectRootRelative, e.getMessage());
            return false;
        }

        boolean deletedAnything = false;
        if (Files.exists(projectRoot) && Files.isDirectory(projectRoot)) {
            try (Stream<Path> walk = Files.walk(projectRoot)) {
                for (Path path : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                    Files.deleteIfExists(path);
                    deletedAnything = true;
                }
            } catch (IOException e) {
                log.warn("Failed deleting project directory tree {}", projectRoot, e);
            }
        }

        int affected = storedFileRepository.markStatusByWorkspaceAndProject(workspaceId, projectId, StoredFileStatus.DELETED);
        if (affected > 0) {
            deletedAnything = true;
        }

        cleanupEmptyAncestorDirectories(projectRoot.getParent());
        return deletedAnything;
    }

    @Transactional
    public int cleanupEmptyWorkspaceDirectories() {
        Path wsRoot = uploadRoot.resolve("ws");
        if (!Files.exists(wsRoot) || !Files.isDirectory(wsRoot)) {
            return 0;
        }

        int deletedCount = 0;
        try (Stream<Path> walk = Files.walk(wsRoot)) {
            for (Path dir : walk
                    .filter(Files::isDirectory)
                    .sorted((a, b) -> b.compareTo(a))
                    .toList()) {
                if (dir.equals(wsRoot)) {
                    continue;
                }

                if (isDirectoryEmpty(dir)) {
                    Files.deleteIfExists(dir);
                    deletedCount++;
                }
            }
        } catch (IOException e) {
            log.warn("Failed cleaning empty shard directories under {}", wsRoot, e);
        }

        return deletedCount;
    }

    public String sanitizeOriginalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }

        String baseName = Paths.get(filename).getFileName().toString();
        String noControlChars = CONTROL_CHARS.matcher(baseName).replaceAll("");
        String safeName = FILENAME_SAFE_CHARS.matcher(noControlChars).replaceAll("_").trim();

        if (safeName.isBlank()) {
            return "file";
        }

        int maxLen = 255;
        return safeName.length() > maxLen ? safeName.substring(0, maxLen) : safeName;
    }

    public Path resolveUploadPath(String relativeStoragePath) {
        Path resolved = uploadRoot.resolve(relativeStoragePath).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Path escapes upload root: " + relativeStoragePath);
        }
        return resolved;
    }

    public String projectRootRelativePath(String workspaceId, String projectId) {
        return String.join("/", "ws", normalizeSegment(workspaceId), "pr", normalizeSegment(projectId));
    }

    private StoredFileDescriptor persistStoredFile(
            String storageUuid,
            String workspaceId,
            String projectId,
            StoredFileType fileType,
            String storagePath,
            String sanitizedOriginalName,
            String mimeType,
            String extension,
            String createdBy,
            Path absolutePath
    ) throws IOException {
        long sizeBytes = Files.size(absolutePath);
        String checksumSha256 = computeSha256(absolutePath);

        StoredFile storedFile = new StoredFile();
        storedFile.setUuid(storageUuid);
        storedFile.setWorkspaceId(workspaceId);
        storedFile.setProjectId(projectId);
        storedFile.setFileType(fileType);
        storedFile.setStoragePath(storagePath);
        storedFile.setOriginalFilename(sanitizedOriginalName);
        storedFile.setMimeType(mimeType);
        storedFile.setExtension(extension);
        storedFile.setSizeBytes(sizeBytes);
        storedFile.setChecksumSha256(checksumSha256);
        storedFile.setCreatedBy((createdBy == null || createdBy.isBlank()) ? "system" : createdBy);
        storedFile.setStatus(StoredFileStatus.READY);

        storedFileRepository.save(storedFile);

        return new StoredFileDescriptor(
                storedFile.getUuid(),
                storedFile.getStoragePath(),
                storedFile.getOriginalFilename(),
                storedFile.getMimeType(),
                storedFile.getExtension(),
                storedFile.getSizeBytes(),
                storedFile.getChecksumSha256(),
                storedFile.getFileType()
        );
    }

    private String buildStoragePath(
            String workspaceId,
            String projectId,
            StoredFileType fileType,
            String storageUuid,
            String extension
    ) {
        String wsSegment = normalizeSegment(workspaceId);
        String projectSegment = normalizeSegment(projectId);
        String hex1 = storageUuid.substring(0, 2);
        String hex2 = storageUuid.substring(2, 4);

        return String.join(
                "/",
                "ws",
                wsSegment,
                "pr",
                projectSegment,
                fileType.getFolderName(),
                hex1,
                hex2,
                storageUuid + "." + extension
        );
    }

    private String generateStorageUuid() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    private String resolveMimeType(
            StoredFileType fileType,
            String declaredMimeType,
            String originalFilename,
            Path sourcePath
    ) {
        String normalizedMime = normalizeMimeType(declaredMimeType);

        if (fileType == StoredFileType.XML) {
            if (normalizedMime != null && XML_MIMES.contains(normalizedMime)) {
                return "application/xml";
            }

            if (hasExtension(originalFilename, "xml") || pathHasExtension(sourcePath, "xml")) {
                return "application/xml";
            }

            throw new IllegalArgumentException("Unsupported XML MIME type: " + declaredMimeType);
        }

        if (normalizedMime != null && IMAGE_MIME_TO_EXT.containsKey(normalizedMime)) {
            return normalizedMime;
        }

        String extFromName = fileExtension(originalFilename);
        if (extFromName != null) {
            String mimeFromExt = EXT_TO_IMAGE_MIME.get(extFromName);
            if (mimeFromExt != null) {
                return mimeFromExt;
            }
        }

        String extFromPath = sourcePath != null ? fileExtension(sourcePath.getFileName().toString()) : null;
        if (extFromPath != null) {
            String mimeFromExt = EXT_TO_IMAGE_MIME.get(extFromPath);
            if (mimeFromExt != null) {
                return mimeFromExt;
            }
        }

        if (sourcePath != null) {
            try {
                String probed = normalizeMimeType(Files.probeContentType(sourcePath));
                if (probed != null && IMAGE_MIME_TO_EXT.containsKey(probed)) {
                    return probed;
                }
            } catch (IOException ignored) {
                // Fall through to validation error.
            }
        }

        throw new IllegalArgumentException("Unsupported MIME type for " + fileType + ": " + declaredMimeType);
    }

    private String resolveExtension(StoredFileType fileType, String mimeType) {
        if (fileType == StoredFileType.XML) {
            return "xml";
        }

        String ext = IMAGE_MIME_TO_EXT.get(mimeType);
        if (ext == null) {
            throw new IllegalArgumentException("Unsupported MIME type: " + mimeType);
        }
        return ext;
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return null;
        }

        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex >= 0) {
            normalized = normalized.substring(0, semicolonIndex).trim();
        }
        return normalized;
    }

    private String normalizeSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Path segment must not be empty");
        }

        String trimmed = value.trim();
        String safeSegment = SEGMENT_SAFE_CHARS.matcher(trimmed).replaceAll("_");
        if (safeSegment.isBlank()) {
            throw new IllegalArgumentException("Path segment contains no safe characters");
        }

        return safeSegment;
    }

    private String fileExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return null;
        }

        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean hasExtension(String filename, String extension) {
        String fileExt = fileExtension(filename);
        return Objects.equals(fileExt, extension);
    }

    private boolean pathHasExtension(Path path, String extension) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        return hasExtension(path.getFileName().toString(), extension);
    }

    private String computeSha256(Path filePath) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }

        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(filePath)) {
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private void markDeletedByStoragePath(String storagePath) {
        Optional<StoredFile> storedFileOpt = storedFileRepository.findByStoragePath(storagePath);
        storedFileOpt.ifPresent(storedFile -> {
            if (storedFile.getStatus() != StoredFileStatus.DELETED) {
                storedFile.setStatus(StoredFileStatus.DELETED);
                storedFileRepository.save(storedFile);
            }
        });
    }

    private void cleanupEmptyAncestorDirectories(Path startDir) {
        Path current = startDir;

        while (current != null && !current.equals(uploadRoot)) {
            if (!Files.isDirectory(current)) {
                break;
            }

            if (!isDirectoryEmpty(current)) {
                break;
            }

            try {
                Files.deleteIfExists(current);
            } catch (IOException e) {
                log.debug("Could not delete empty directory {}", current, e);
                break;
            }

            current = current.getParent();
        }
    }

    private boolean isDirectoryEmpty(Path dir) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    public record StoredFileDescriptor(
            String uuid,
            String storagePath,
            String originalFilename,
            String mimeType,
            String extension,
            long sizeBytes,
            String checksumSha256,
            StoredFileType fileType
    ) {
    }
}
