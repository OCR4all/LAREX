package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.config.UploadDirectoryProperties;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadDirectoryPreflightService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UploadDirectoryPreflightServiceIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void createsConfiguredDirectoriesAndAppliesPermissions() throws IOException {
        Path uploadRoot = tempDir.resolve("mnt/data/uploads");
        Path uploadTemp = uploadRoot.resolve("temp");

        UploadDirectoryPreflightService service = new UploadDirectoryPreflightService(properties(uploadRoot, uploadTemp));

        service.ensureDirectoriesReady();

        assertThat(uploadRoot).isDirectory();
        assertThat(uploadTemp).isDirectory();

        FileStore fileStore = Files.getFileStore(uploadRoot);
        if (fileStore.supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> expected = PosixFilePermissions.fromString("rwxr-xr-x");
            assertThat(Files.getPosixFilePermissions(uploadRoot)).isEqualTo(expected);
            assertThat(Files.getPosixFilePermissions(uploadTemp)).isEqualTo(expected);
        }
    }

    @Test
    void directoryCreationIsIdempotentUnderConcurrentCalls() throws Exception {
        Path uploadRoot = tempDir.resolve("shared/uploads");
        Path uploadTemp = uploadRoot.resolve("temp");
        UploadDirectoryPreflightService service = new UploadDirectoryPreflightService(properties(uploadRoot, uploadTemp));

        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    service.ensureDirectoriesReady();
                    return null;
                }));
            }

            ready.await();
            start.countDown();

            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(uploadRoot).isDirectory();
        assertThat(uploadTemp).isDirectory();
    }

    @Test
    void failsFastWhenParentDirectoryIsNotWritable() throws IOException {
        Path readonlyParent = tempDir.resolve("readonly");
        Files.createDirectories(readonlyParent);

        assumeTrue(Files.getFileStore(readonlyParent).supportsFileAttributeView("posix"));

        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(readonlyParent);
        Files.setPosixFilePermissions(readonlyParent, PosixFilePermissions.fromString("r-xr-xr-x"));

        Path uploadRoot = readonlyParent.resolve("uploads");
        Path uploadTemp = uploadRoot.resolve("temp");
        UploadDirectoryPreflightService service = new UploadDirectoryPreflightService(properties(uploadRoot, uploadTemp));

        try {
            assertThatThrownBy(service::ensureDirectoriesReady)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Upload directory preflight failed");
        } finally {
            Files.setPosixFilePermissions(readonlyParent, originalPermissions);
        }
    }

    private UploadDirectoryProperties properties(Path uploadRoot, Path uploadTemp) {
        UploadDirectoryProperties properties = new UploadDirectoryProperties();
        properties.setRootDirectory(uploadRoot);
        properties.setTempDirectory(uploadTemp);
        properties.setCreateIfMissing(true);
        properties.setVerifyWritable(true);
        properties.setFailFast(true);
        return properties;
    }
}
