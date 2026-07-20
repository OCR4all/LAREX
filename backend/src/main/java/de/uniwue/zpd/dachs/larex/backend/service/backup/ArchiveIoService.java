package de.uniwue.zpd.dachs.larex.backend.service.backup;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class ArchiveIoService {

    private static final int ZIP_OUTPUT_BUFFER_BYTES = 256 * 1024;
    private static final int ZIP_ENTRY_BUFFER_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;

    public ArchiveIoService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeZip(Path outputPath, ZipWriter writer) throws IOException {
        Path parent = outputPath.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writeZip(out, writer);
        }
    }

    public void writeZip(OutputStream outputStream, ZipWriter writer) throws IOException {
        writeZip(outputStream, Deflater.DEFAULT_COMPRESSION, writer);
    }

    public void writeZip(OutputStream outputStream,
                         int compressionLevel,
                         ZipWriter writer) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(
                new BufferedOutputStream(
                        new NonClosingOutputStream(outputStream),
                        ZIP_OUTPUT_BUFFER_BYTES
                )
        )) {
            zipOut.setLevel(compressionLevel);
            writer.write(zipOut);
        }
    }

    public void writeJsonEntry(ZipOutputStream zipOut, String entryName, Object payload) throws IOException {
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
        writeBytesEntry(zipOut, entryName, bytes);
    }

    public String writeJsonEntryWithSha256(ZipOutputStream zipOut,
                                           String entryName,
                                           Object payload) throws IOException {
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
        return writeStreamEntryWithSha256(zipOut, entryName, output -> output.write(bytes));
    }

    public <T> T readJson(Path jsonPath, Class<T> targetClass) throws IOException {
        return objectMapper.readValue(jsonPath.toFile(), targetClass);
    }

    public void writeBytesEntry(ZipOutputStream zipOut, String entryName, byte[] bytes) throws IOException {
        writeStreamEntry(zipOut, entryName, entryOut -> entryOut.write(bytes));
    }

    public void writeFileEntry(ZipOutputStream zipOut, String entryName, Path sourceFile) throws IOException {
        writeStreamEntry(zipOut, entryName, entryOut -> Files.copy(sourceFile, entryOut));
    }

    public void writeDirectoryEntry(ZipOutputStream zipOut, String entryName) throws IOException {
        String normalizedName = normalizeArchivePath(entryName) + "/";
        zipOut.putNextEntry(new ZipEntry(normalizedName));
        zipOut.closeEntry();
    }

    public void writeStreamEntry(ZipOutputStream zipOut, String entryName, EntryWriter writer) throws IOException {
        String normalizedName = normalizeArchivePath(entryName);
        ZipEntry entry = new ZipEntry(normalizedName);
        zipOut.putNextEntry(entry);
        try {
            OutputStream bufferedEntryOutput = new BufferedOutputStream(
                    new NonClosingOutputStream(zipOut),
                    ZIP_ENTRY_BUFFER_BYTES
            );
            writer.write(bufferedEntryOutput);
            bufferedEntryOutput.flush();
        } finally {
            zipOut.closeEntry();
        }
    }

    public String writeStreamEntryWithSha256(ZipOutputStream zipOut,
                                             String entryName,
                                             EntryWriter writer) throws IOException {
        return writeStreamEntryWithSha256(zipOut, entryName, Deflater.DEFAULT_COMPRESSION, writer);
    }

    public String writeStreamEntryWithSha256(ZipOutputStream zipOut,
                                             String entryName,
                                             int compressionLevel,
                                             EntryWriter writer) throws IOException {
        MessageDigest digest = sha256Digest();
        String normalizedName = normalizeArchivePath(entryName);
        zipOut.setLevel(compressionLevel);
        try {
            zipOut.putNextEntry(new ZipEntry(normalizedName));
            try {
                OutputStream digestingOutput = new java.security.DigestOutputStream(
                        new NonClosingOutputStream(zipOut),
                        digest
                );
                OutputStream bufferedEntryOutput = new BufferedOutputStream(
                        digestingOutput,
                        ZIP_ENTRY_BUFFER_BYTES
                );
                writer.write(bufferedEntryOutput);
                bufferedEntryOutput.flush();
            } finally {
                zipOut.closeEntry();
            }
        } finally {
            zipOut.setLevel(Deflater.DEFAULT_COMPRESSION);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public Path extractZipToTempDir(InputStream inputStream, String prefix) throws IOException {
        return extractZipToTempDir(inputStream, prefix, ExtractionLimits.unbounded());
    }

    public Path extractZipToTempDir(InputStream inputStream,
                                    String prefix,
                                    ExtractionLimits limits) throws IOException {
        return extractZipToTempDirWithReport(inputStream, prefix, limits).directory();
    }

    public ExtractionResult extractZipToTempDirWithReport(InputStream inputStream,
                                                          String prefix,
                                                          ExtractionLimits limits) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        Objects.requireNonNull(limits, "limits");

        Path tempDir = Files.createTempDirectory(prefix);
        Set<String> extractedPaths = new HashSet<>();
        long totalExtractedBytes = 0L;
        int entryCount = 0;
        try (LimitedCountingInputStream limitedInput =
                     new LimitedCountingInputStream(inputStream, limits.maxArchiveBytes());
             ZipInputStream zipIn = new ZipInputStream(limitedInput)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > limits.maxEntries()) {
                    throw archiveLimitExceeded(
                            "Archive contains more than " + limits.maxEntries() + " entries"
                    );
                }

                String entryName = normalizeArchivePath(entry.getName());
                if (!extractedPaths.add(entryName)) {
                    throw new IllegalArgumentException("Duplicate archive entry: " + entryName);
                }
                Path resolvedPath = tempDir.resolve(entryName).normalize();
                if (!resolvedPath.startsWith(tempDir)) {
                    throw new IllegalArgumentException("Archive entry escapes destination directory: " + entry.getName());
                }

                validateDeclaredEntrySize(entry, entryName, totalExtractedBytes, limits);
                if (entry.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                } else {
                    Files.createDirectories(resolvedPath.getParent());
                    long extractedBytes = copyZipEntry(
                            zipIn,
                            resolvedPath,
                            entryName,
                            totalExtractedBytes,
                            limits
                    );
                    totalExtractedBytes += extractedBytes;
                    validateCompressionRatio(entry, entryName, extractedBytes, limits);
                }
                zipIn.closeEntry();
            }
        } catch (IOException | RuntimeException exception) {
            deleteRecursively(tempDir);
            throw exception;
        }
        return new ExtractionResult(tempDir, totalExtractedBytes, entryCount);
    }

    private void validateDeclaredEntrySize(ZipEntry entry,
                                           String entryName,
                                           long totalExtractedBytes,
                                           ExtractionLimits limits) {
        long declaredSize = entry.getSize();
        if (declaredSize < 0) {
            return;
        }
        if (declaredSize > limits.maxEntryBytes()) {
            throw archiveLimitExceeded(
                    "Archive entry exceeds the allowed size of " + limits.maxEntryBytes()
                            + " bytes: " + entryName
            );
        }
        if (declaredSize > limits.maxTotalBytes() - totalExtractedBytes) {
            throw archiveLimitExceeded(
                    "Archive exceeds the allowed extracted size of " + limits.maxTotalBytes() + " bytes"
            );
        }
    }

    private long copyZipEntry(ZipInputStream source,
                              Path target,
                              String entryName,
                              long totalExtractedBytes,
                              ExtractionLimits limits) throws IOException {
        long entryBytes = 0L;
        byte[] buffer = new byte[64 * 1024];
        try (OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = source.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (read > limits.maxEntryBytes() - entryBytes) {
                    throw archiveLimitExceeded(
                            "Archive entry exceeds the allowed size of " + limits.maxEntryBytes()
                                    + " bytes: " + entryName
                    );
                }
                if (read > limits.maxTotalBytes() - totalExtractedBytes - entryBytes) {
                    throw archiveLimitExceeded(
                            "Archive exceeds the allowed extracted size of "
                                    + limits.maxTotalBytes() + " bytes"
                    );
                }
                output.write(buffer, 0, read);
                entryBytes += read;
            }
        }
        return entryBytes;
    }

    private void validateCompressionRatio(ZipEntry entry,
                                          String entryName,
                                          long extractedBytes,
                                          ExtractionLimits limits) {
        if (extractedBytes == 0) {
            return;
        }
        long compressedBytes = entry.getCompressedSize();
        if (compressedBytes == 0) {
            throw archiveLimitExceeded("Archive entry has an invalid compression ratio: " + entryName);
        }
        if (compressedBytes < 0) {
            return;
        }
        double ratio = (double) extractedBytes / compressedBytes;
        if (ratio > limits.maxCompressionRatio()) {
            throw archiveLimitExceeded(
                    "Archive entry exceeds the allowed compression ratio of "
                            + limits.maxCompressionRatio() + ": " + entryName
            );
        }
    }

    private IllegalArgumentException archiveLimitExceeded(String message) {
        return new IllegalArgumentException(message);
    }

    public String normalizeArchivePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Archive entry path must not be empty");
        }

        String normalized = path.replace('\\', '/').trim();
        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException("Archive entry path is not safe: " + path);
        }

        if (normalized.contains("../") || normalized.equals("..") || normalized.startsWith("../")) {
            throw new IllegalArgumentException("Archive entry path is not safe: " + path);
        }

        Path normalizedPath = Path.of(normalized).normalize();
        String cleaned = normalizedPath.toString().replace('\\', '/');
        if (cleaned.startsWith("../") || cleaned.equals("..")) {
            throw new IllegalArgumentException("Archive entry path is not safe: " + path);
        }
        return cleaned;
    }

    private void deleteRecursively(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }

    @FunctionalInterface
    public interface ZipWriter {
        void write(ZipOutputStream zipOut) throws IOException;
    }

    @FunctionalInterface
    public interface EntryWriter {
        void write(OutputStream outputStream) throws IOException;
    }

    public record ExtractionLimits(
            long maxArchiveBytes,
            int maxEntries,
            long maxEntryBytes,
            long maxTotalBytes,
            double maxCompressionRatio
    ) {
        public ExtractionLimits {
            if (maxArchiveBytes < 1
                    || maxEntries < 1
                    || maxEntryBytes < 1
                    || maxTotalBytes < 1
                    || maxCompressionRatio < 1.0) {
                throw new IllegalArgumentException("Archive extraction limits must be positive");
            }
        }

        public static ExtractionLimits unbounded() {
            return new ExtractionLimits(
                    Long.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Long.MAX_VALUE,
                    Long.MAX_VALUE,
                    Double.MAX_VALUE
            );
        }
    }

    public record ExtractionResult(Path directory, long extractedBytes, int entryCount) {
    }

    private static final class LimitedCountingInputStream extends FilterInputStream {
        private final long maxBytes;
        private long bytesRead;

        private LimitedCountingInputStream(InputStream inputStream, long maxBytes) {
            super(inputStream);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                addBytes(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                addBytes(read);
            }
            return read;
        }

        private void addBytes(int count) {
            if (count > maxBytes - bytesRead) {
                throw new IllegalArgumentException(
                        "Archive exceeds the allowed compressed size of " + maxBytes + " bytes"
                );
            }
            bytesRead += count;
        }
    }

    private static class NonClosingOutputStream extends FilterOutputStream {
        private NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
