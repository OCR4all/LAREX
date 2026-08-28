package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.config.ProjectExportProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

@Service
public class ProjectExportArtifactStore {

    private final ProjectExportProperties properties;
    private Path root;

    public ProjectExportArtifactStore(ProjectExportProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() throws IOException {
        root = Path.of(properties.getArtifactDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (!Files.isWritable(root)) {
            throw new IOException("Project export artifact directory is not writable: " + root);
        }
    }

    public PendingArtifact createPending(String jobId) throws IOException {
        requireSpace();
        Path directory = resolveJobDirectory(jobId);
        Files.createDirectories(directory);
        Path pending = directory.resolve("archive.zip.part");
        Path completed = directory.resolve("archive.zip");
        Files.deleteIfExists(pending);
        return new PendingArtifact(
                pending,
                completed,
                Files.newOutputStream(pending, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        );
    }

    public Path complete(PendingArtifact artifact) throws IOException {
        try {
            return Files.move(artifact.pendingPath(), artifact.completedPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            return Files.move(artifact.pendingPath(), artifact.completedPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path resolveStoredPath(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid export artifact path");
        }
        return resolved;
    }

    public String relativePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("Artifact is outside the export directory");
        }
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    public void deleteJobArtifacts(String jobId) throws IOException {
        Path directory = resolveJobDirectory(jobId);
        Files.deleteIfExists(directory.resolve("archive.zip.part"));
        Files.deleteIfExists(directory.resolve("archive.zip"));
        Files.deleteIfExists(directory);
    }

    private Path resolveJobDirectory(String jobId) {
        if (jobId == null || !jobId.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Invalid export job ID");
        }
        return root.resolve(jobId).normalize();
    }

    void requireSpace() throws IOException {
        long usable = Files.getFileStore(root).getUsableSpace();
        if (usable < properties.getMinimumFreeBytes()) {
            throw new IOException("Project export stopped to preserve the configured free-space reserve");
        }
    }

    public record PendingArtifact(Path pendingPath, Path completedPath, OutputStream outputStream) {}
}
