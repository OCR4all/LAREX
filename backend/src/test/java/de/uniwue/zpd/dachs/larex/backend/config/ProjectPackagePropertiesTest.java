package de.uniwue.zpd.dachs.larex.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectPackagePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProjectPackageConfiguration.class);

    @Test
    void bindsArchiveExtractionLimits() {
        contextRunner
                .withPropertyValues(
                        "larex.project-packages.archive.max-archive-bytes=1000",
                        "larex.project-packages.archive.max-entries=20",
                        "larex.project-packages.archive.max-entry-bytes=300",
                        "larex.project-packages.archive.max-total-bytes=900",
                        "larex.project-packages.archive.max-descriptor-bytes=200",
                        "larex.project-packages.archive.max-compression-ratio=25",
                        "larex.project-packages.preview.max-cached-bytes=8000",
                        "larex.project-packages.preview.max-sessions=8",
                        "larex.project-packages.preview.expire-after-minutes=15"
                )
                .run(context -> {
                    ProjectPackageProperties.Archive archive =
                            context.getBean(ProjectPackageProperties.class).getArchive();
                    assertThat(archive.getMaxArchiveBytes()).isEqualTo(1000);
                    assertThat(archive.getMaxEntries()).isEqualTo(20);
                    assertThat(archive.getMaxEntryBytes()).isEqualTo(300);
                    assertThat(archive.getMaxTotalBytes()).isEqualTo(900);
                    assertThat(archive.getMaxDescriptorBytes()).isEqualTo(200);
                    assertThat(archive.getMaxCompressionRatio()).isEqualTo(25);
                    ProjectPackageProperties.Preview preview =
                            context.getBean(ProjectPackageProperties.class).getPreview();
                    assertThat(preview.getMaxCachedBytes()).isEqualTo(8000);
                    assertThat(preview.getMaxSessions()).isEqualTo(8);
                    assertThat(preview.getExpireAfterMinutes()).isEqualTo(15);
                });
    }

    @Test
    void rejectsInvalidArchiveExtractionLimits() {
        contextRunner
                .withPropertyValues("larex.project-packages.archive.max-entries=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
