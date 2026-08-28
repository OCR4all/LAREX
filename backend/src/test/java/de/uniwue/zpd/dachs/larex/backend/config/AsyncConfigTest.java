package de.uniwue.zpd.dachs.larex.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Test
    void createsDedicatedIiifPreviewAndDownloadExecutors() {
        AsyncConfig config = new AsyncConfig(
                new AsyncExecutorProperties(),
                new UploadProperties(),
                new ImportProperties(),
                new IiifProperties(),
                new AnnotationProperties(),
                new StorageProperties(),
                new BackupProperties(),
                new ProjectExportProperties()
        );

        ThreadPoolTaskExecutor preview = config.iiifPreviewTaskExecutor();
        ThreadPoolTaskExecutor download = config.iiifDownloadTaskExecutor();
        ThreadPoolTaskExecutor generalImport = config.importTaskExecutor();
        ThreadPoolTaskExecutor backup = config.backupTaskExecutor();
        ThreadPoolTaskExecutor projectExport = config.projectExportTaskExecutor();
        try {
            assertThat(preview).isNotSameAs(download).isNotSameAs(generalImport).isNotSameAs(backup);
            assertThat(preview.getThreadNamePrefix()).isEqualTo("iiif-preview-");
            assertThat(preview.getCorePoolSize()).isEqualTo(2);
            assertThat(preview.getMaxPoolSize()).isEqualTo(4);
            assertThat(download.getThreadNamePrefix()).isEqualTo("iiif-download-");
            assertThat(download.getCorePoolSize()).isEqualTo(1);
            assertThat(download.getMaxPoolSize()).isEqualTo(2);
            assertThat(generalImport.getThreadNamePrefix()).isEqualTo("import-");
            assertThat(backup.getThreadNamePrefix()).isEqualTo("backup-");
            assertThat(backup.getCorePoolSize()).isEqualTo(1);
            assertThat(backup.getMaxPoolSize()).isEqualTo(1);
            assertThat(projectExport.getThreadNamePrefix()).isEqualTo("project-export-");
            assertThat(projectExport.getMaxPoolSize()).isEqualTo(1);
        } finally {
            preview.shutdown();
            download.shutdown();
            generalImport.shutdown();
            backup.shutdown();
            projectExport.shutdown();
        }
    }
}
