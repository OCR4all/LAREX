package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.config.UploadDirectoryProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

@Service
public class UploadDirectoryPreflightService {

    private static final Logger log = LoggerFactory.getLogger(UploadDirectoryPreflightService.class);
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS_755 =
            PosixFilePermissions.fromString("rwxr-xr-x");

    private final UploadDirectoryProperties properties;

    public UploadDirectoryPreflightService(UploadDirectoryProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initializeOnStartup() {
        ensureDirectoriesReady();
    }

    public synchronized void ensureDirectoriesReady() {
        Path uploadRoot = requirePath(properties.getRootDirectory(), "upload root directory");
        Path tempDirectory = requirePath(properties.getTempDirectory(), "upload temp directory");

        Path normalizedRoot = uploadRoot.toAbsolutePath().normalize();
        Path normalizedTemp = tempDirectory.toAbsolutePath().normalize();

        if (!normalizedTemp.startsWith(normalizedRoot)) {
            log.warn("Upload temp directory {} is outside upload root {}. This is allowed but should be reviewed for production deployments.",
                    normalizedTemp, normalizedRoot);
        }

        ensureDirectory(normalizedRoot, "upload root");
        ensureDirectory(normalizedTemp, "upload temp");

        log.info("Upload directory preflight completed successfully. root={}, temp={}", normalizedRoot, normalizedTemp);
    }

    private Path requirePath(Path value, String label) {
        if (value == null) {
            throw new IllegalStateException("Missing configuration for " + label + " (larex.upload.directories.*)");
        }
        return value;
    }

    private void ensureDirectory(Path directory, String label) {
        boolean existedBefore = Files.exists(directory);

        try {
            if (properties.isCreateIfMissing()) {
                Files.createDirectories(directory);
            }

            if (!Files.exists(directory)) {
                throw new IllegalStateException(label + " directory does not exist: " + directory);
            }

            if (!Files.isDirectory(directory)) {
                throw new IllegalStateException(label + " path is not a directory: " + directory);
            }

            if (!existedBefore) {
                applyPosixPermissionsIfSupported(directory);
            }

            if (properties.isVerifyWritable()) {
                verifyWritable(directory, label);
            }
        } catch (Exception e) {
            handleInitializationFailure(directory, label, e);
        }
    }

    private void verifyWritable(Path directory, String label) throws IOException {
        if (!Files.isWritable(directory)) {
            throw new AccessDeniedException(directory.toString(), null, "Directory is not writable");
        }

        Path probeFile = null;
        try {
            probeFile = Files.createTempFile(directory, ".larex-write-probe-", ".tmp");
        } finally {
            if (probeFile != null) {
                Files.deleteIfExists(probeFile);
            }
        }

        try {
            FileStore store = Files.getFileStore(directory);
            long usableSpace = store.getUsableSpace();
            if (usableSpace <= 0) {
                throw new FileSystemException(directory.toString(), null, "No usable disk space reported");
            }
            log.debug("Verified writable {} directory at {} (usableSpace={} bytes)", label, directory, usableSpace);
        } catch (IOException e) {
            log.warn("Could not read filesystem space information for {} directory {}: {}", label, directory, e.getMessage());
        }
    }

    private void applyPosixPermissionsIfSupported(Path directory) {
        try {
            if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(directory, DIRECTORY_PERMISSIONS_755);
            }
        } catch (UnsupportedOperationException e) {
            log.debug("POSIX permissions not supported for {}", directory);
        } catch (IOException | SecurityException e) {
            log.warn("Could not set POSIX permissions 755 on {}: {}", directory, e.getMessage());
        }
    }

    private void handleInitializationFailure(Path directory, String label, Exception exception) {
        String message = buildErrorMessage(directory, label, exception);

        if (properties.isFailFast()) {
            log.error(message, exception);
            throw new IllegalStateException(message, exception);
        }

        log.error("{} Application startup will continue because larex.upload.directories.fail-fast=false", message, exception);
    }

    private String buildErrorMessage(Path directory, String label, Exception exception) {
        if (exception instanceof ReadOnlyFileSystemException) {
            return "Upload directory preflight failed for " + label + " (" + directory + "): filesystem is read-only";
        }
        if (exception instanceof FileAlreadyExistsException) {
            return "Upload directory preflight failed for " + label + " (" + directory + "): path exists but is not a directory";
        }
        if (exception instanceof AccessDeniedException) {
            return "Upload directory preflight failed for " + label + " (" + directory + "): access denied";
        }
        if (exception instanceof FileSystemException fileSystemException) {
            String reason = fileSystemException.getReason();
            if (reason == null || reason.isBlank()) {
                reason = "filesystem error";
            }
            return "Upload directory preflight failed for " + label + " (" + directory + "): " + reason
                    + " (check permissions, read-only mounts, and disk space)";
        }
        return "Upload directory preflight failed for " + label + " (" + directory + "): " + exception.getMessage();
    }

}
