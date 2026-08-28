package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.config.ProjectExportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectExportArtifactStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void finalizesArtifactsAndOnlyResolvesPathsInsideRoot() throws Exception {
        ProjectExportProperties properties = new ProjectExportProperties();
        properties.setArtifactDirectory(tempDir.toString());
        properties.setMinimumFreeBytes(0);
        ProjectExportArtifactStore store = new ProjectExportArtifactStore(properties);
        store.initialize();

        String jobId = UUID.randomUUID().toString();
        ProjectExportArtifactStore.PendingArtifact pending = store.createPending(jobId);
        try (var output = pending.outputStream()) {
            output.write("zip".getBytes());
        }
        Path completed = store.complete(pending);

        assertThat(Files.readString(completed)).isEqualTo("zip");
        assertThat(store.resolveStoredPath(store.relativePath(completed))).isEqualTo(completed);
        assertThatThrownBy(() -> store.resolveStoredPath("../outside.zip"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
