package de.uniwue.zpd.dachs.larex.backend.service.project;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;

final class LimitedExportOutputStream extends FilterOutputStream {

    private final long maximumBytes;
    private final BooleanSupplier cancellationRequested;
    private final IoCheck resourceCheck;
    private long written;
    private long nextPeriodicCheck = 8L * 1024 * 1024;

    LimitedExportOutputStream(OutputStream outputStream, long maximumBytes) {
        this(outputStream, maximumBytes, () -> false, () -> {});
    }

    LimitedExportOutputStream(OutputStream outputStream, long maximumBytes,
                              BooleanSupplier cancellationRequested) {
        this(outputStream, maximumBytes, cancellationRequested, () -> {});
    }

    LimitedExportOutputStream(OutputStream outputStream, long maximumBytes,
                              BooleanSupplier cancellationRequested, IoCheck resourceCheck) {
        super(outputStream);
        this.maximumBytes = maximumBytes;
        this.cancellationRequested = cancellationRequested;
        this.resourceCheck = resourceCheck;
    }

    @Override
    public void write(int value) throws IOException {
        requireCapacity(1);
        out.write(value);
        written++;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        requireCapacity(length);
        out.write(bytes, offset, length);
        written += length;
    }

    long written() {
        return written;
    }

    private void requireCapacity(int additionalBytes) throws IOException {
        if (written + additionalBytes >= nextPeriodicCheck) {
            if (cancellationRequested.getAsBoolean()) {
                throw new IOException("Project export was cancelled");
            }
            resourceCheck.run();
            nextPeriodicCheck = written + additionalBytes + 8L * 1024 * 1024;
        }
        if (additionalBytes < 0 || written > maximumBytes - additionalBytes) {
            throw new IOException("Project export exceeded the configured maximum artifact size");
        }
    }

    @FunctionalInterface
    interface IoCheck {
        void run() throws IOException;
    }
}
