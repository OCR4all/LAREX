package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.config.UploadDirectoryProperties;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class UploadPathService {

    private final UploadDirectoryProperties uploadDirectoryProperties;

    public UploadPathService(UploadDirectoryProperties uploadDirectoryProperties) {
        this.uploadDirectoryProperties = uploadDirectoryProperties;
    }

    public Path root() {
        Path rootDirectory = uploadDirectoryProperties.getRootDirectory();
        if (rootDirectory == null) {
            throw new IllegalStateException("Missing upload root directory configuration");
        }
        return rootDirectory.toAbsolutePath().normalize();
    }

    public Path resolve(String relativePath) {
        Path root = root();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes upload root: " + relativePath);
        }
        return resolved;
    }

    public Path resolve(String first, String... more) {
        return resolve(Path.of(first, more).toString());
    }
}
