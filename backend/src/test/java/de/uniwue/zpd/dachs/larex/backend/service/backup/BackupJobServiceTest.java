package de.uniwue.zpd.dachs.larex.backend.service.backup;

import de.uniwue.zpd.dachs.larex.backend.config.BackupProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.BackupJobDto;
import de.uniwue.zpd.dachs.larex.backend.service.notification.JobRealtimePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class BackupJobServiceTest {

    @Mock
    private BackupJobProcessor backupJobProcessor;

    @Mock
    private AsyncTaskExecutor taskExecutor;

    @Mock
    private Future<?> future;

    private BackupJobService service;
    private BackupProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BackupProperties();
        service = new BackupJobService(backupJobProcessor, taskExecutor, properties, mock(JobRealtimePublisher.class));
    }

    @Test
    void validatePath_acceptsPathWithinAllowList() throws Exception {
        Path allowed = Files.createTempDirectory("backup-allowed-");
        try {
            configureBackupPaths(allowed);

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
            configureBackupPaths(allowed);

            BackupJobDto.ValidatePathResponse response = service.validatePath(
                    new BackupJobDto.ValidatePathRequest(outside.toString(), BackupJobDto.PathRole.SOURCE)
            );

            assertFalse(response.valid());
        } finally {
            Files.deleteIfExists(outside);
            Files.deleteIfExists(allowed);
        }
    }

    @Test
    void createVerifyJob_requiresSourceButNotOutput() throws Exception {
        Path allowed = Files.createTempDirectory("backup-allowed-");
        try {
            configureBackupPaths(allowed);

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.createJob(
                            "admin",
                            new BackupJobDto.CreateJobRequest(
                                    BackupJobDto.JobType.VERIFY,
                                    null,
                                    null,
                                    null
                            )
                    )
            );

            assertTrue(error.getMessage().contains("sourcePath is required"));
        } finally {
            Files.deleteIfExists(allowed);
        }
    }

    @Test
    void createDumpJob_usesConfiguredOutputDirectory() throws Exception {
        Path allowed = Files.createTempDirectory("backup-allowed-");
        try {
            configureBackupPaths(allowed);
            doReturn(future).when(taskExecutor).submit(any(Runnable.class));

            BackupJobDto.JobResponse response = service.createJob(
                    "admin",
                    new BackupJobDto.CreateJobRequest(
                            BackupJobDto.JobType.DUMP,
                            null,
                            null,
                            null
                    )
            );

            assertEquals(allowed.resolve("out").toString(), response.outputPath());
        } finally {
            Files.deleteIfExists(allowed);
        }
    }

    @Test
    void createDumpJob_acceptsConfiguredOutputOutsideSourceAllowList() throws Exception {
        Path allowedSource = Files.createTempDirectory("backup-source-");
        Path outputParent = Files.createTempDirectory("backup-output-");
        try {
            Path configuredOutput = outputParent.resolve("portable");
            properties.setEnabled(true);
            properties.setAllowedPaths(List.of(allowedSource.toString()));
            properties.setOutputDir(configuredOutput.toString());
            service.initAllowedPaths();
            doReturn(future).when(taskExecutor).submit(any(Runnable.class));

            BackupJobDto.JobResponse response = service.createJob(
                    "admin",
                    new BackupJobDto.CreateJobRequest(
                            BackupJobDto.JobType.DUMP,
                            null,
                            null,
                            null
                    )
            );

            assertEquals(configuredOutput.toString(), response.outputPath());
        } finally {
            Files.deleteIfExists(outputParent);
            Files.deleteIfExists(allowedSource);
        }
    }

    private void configureBackupPaths(Path allowed) {
        properties.setEnabled(true);
        properties.setAllowedPaths(List.of(allowed.toString()));
        properties.setOutputDir(allowed.resolve("out").toString());
        service.initAllowedPaths();
    }
}
