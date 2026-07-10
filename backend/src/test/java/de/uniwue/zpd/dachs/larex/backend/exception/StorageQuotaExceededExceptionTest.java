package de.uniwue.zpd.dachs.larex.backend.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageQuotaExceededExceptionTest {

    @Test
    void messageUsesHumanReadableStorageAmounts() {
        StorageQuotaExceededException exception = new StorageQuotaExceededException(
                "workspace-1",
                "iiif-import-job",
                150L * 1024L * 1024L,
                25L * 1024L * 1024L,
                500L * 1024L * 1024L,
                475L * 1024L * 1024L,
                0L,
                95.0
        );

        assertThat(exception.getMessage())
                .isEqualTo("Not enough workspace storage. This IIIF import needs 150.0 MB, but only 25.0 MB is available. "
                        + "Free up storage or ask an administrator to increase the workspace quota.")
                .doesNotContain("157286400");
    }
}
