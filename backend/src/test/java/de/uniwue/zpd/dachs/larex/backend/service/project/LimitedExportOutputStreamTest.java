package de.uniwue.zpd.dachs.larex.backend.service.project;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitedExportOutputStreamTest {

    @Test
    void rejectsWritesPastConfiguredLimitWithoutWritingPartialChunk() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        LimitedExportOutputStream output = new LimitedExportOutputStream(target, 5);

        output.write(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> output.write(new byte[]{4, 5, 6}))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maximum artifact size");
        assertThat(target.toByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    void checksCancellationWhileLargeArchiveIsBeingWritten() {
        AtomicBoolean cancelled = new AtomicBoolean(true);
        LimitedExportOutputStream output = new LimitedExportOutputStream(
                new ByteArrayOutputStream(), 32L * 1024 * 1024, cancelled::get);

        assertThatThrownBy(() -> output.write(new byte[8 * 1024 * 1024]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void runsResourceGuardPeriodically() throws IOException {
        AtomicBoolean checked = new AtomicBoolean();
        LimitedExportOutputStream output = new LimitedExportOutputStream(
                new ByteArrayOutputStream(),
                32L * 1024 * 1024,
                () -> false,
                () -> checked.set(true)
        );

        output.write(new byte[8 * 1024 * 1024]);

        assertThat(checked).isTrue();
    }
}
