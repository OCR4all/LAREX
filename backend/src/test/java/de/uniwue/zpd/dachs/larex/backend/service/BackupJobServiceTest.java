package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.BackupJobDto;
import de.uniwue.zpd.dachs.larex.backend.service.backup.BackupJobProcessor;
import de.uniwue.zpd.dachs.larex.backend.service.backup.BackupJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;


import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BackupJobServiceTest {

    @Mock
    private BackupJobProcessor backupJobProcessor;

    @Mock
    private AsyncTaskExecutor taskExecutor;

    private BackupJobService service;

    @BeforeEach
    void setUp() {
        service = new BackupJobService(backupJobProcessor, taskExecutor);
    }

    @Test
    void validatePath_acceptsPathWithinAllowList() throws Exception {
        Path allowed = Files.createTempDirectory("backup-allowed-");
        try {
            ReflectionTestUtils.setField(service, "backupEnabled", true);
            ReflectionTestUtils.setField(service, "allowedPathsConfig", allowed.toString());
            ReflectionTestUtils.setField(service, "outputDir", allowed.resolve("out").toString());
            ReflectionTestUtils.invokeMethod(service, "initAllowedPaths");

            BackupJobDto.ValidatePathResponse response = service.validatePath(
                    new BackupJobDto.ValidatePathRequest(allowed.toString(), BackupJobDto.PathRole.SOURCE)
            );

            assertTrue(response.valid());
        } finally {
            Files.deleteIfExists(allowed);
        }
    }

    @Test
    void validatePath_rejectsPathOutsideAllowList() throws Exception {
        Path allowed = Files.createTempDirectory("backup-allowed-");
        Path outside = Files.createTempDirectory("backup-outside-");
        try {
            ReflectionTestUtils.setField(service, "backupEnabled", true);
            ReflectionTestUtils.setField(service, "allowedPathsConfig", allowed.toString());
            ReflectionTestUtils.setField(service, "outputDir", allowed.resolve("out").toString());
            ReflectionTestUtils.invokeMethod(service, "initAllowedPaths");

            BackupJobDto.ValidatePathResponse response = service.validatePath(
                    new BackupJobDto.ValidatePathRequest(outside.toString(), BackupJobDto.PathRole.SOURCE)
            );

            assertFalse(response.valid());
        } finally {
            Files.deleteIfExists(outside);
            Files.deleteIfExists(allowed);
        }
    }
}
