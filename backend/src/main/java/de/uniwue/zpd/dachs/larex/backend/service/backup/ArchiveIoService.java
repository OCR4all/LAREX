package de.uniwue.zpd.dachs.larex.backend.service.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class ArchiveIoService {

    private final ObjectMapper objectMapper;

    public ArchiveIoService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] createZip(ZipWriter writer) throws IOException {
        Path temp = Files.createTempFile("larex-archive-", ".zip");
        try {
            writeZip(temp, writer);
            return Files.readAllBytes(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public void writeZip(Path outputPath, ZipWriter writer) throws IOException {
        Path parent = outputPath.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             ZipOutputStream zipOut = new ZipOutputStream(out)) {
            writer.write(zipOut);
        }
    }

    public void writeJsonEntry(ZipOutputStream zipOut, String entryName, Object payload) throws IOException {
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
        writeBytesEntry(zipOut, entryName, bytes);
    }

    public <T> T readJson(Path jsonPath, Class<T> targetClass) throws IOException {
        return objectMapper.readValue(jsonPath.toFile(), targetClass);
    }

    public void writeBytesEntry(ZipOutputStream zipOut, String entryName, byte[] bytes) throws IOException {
        String normalizedName = normalizeArchivePath(entryName);
        ZipEntry entry = new ZipEntry(normalizedName);
        zipOut.putNextEntry(entry);
        zipOut.write(bytes);
        zipOut.closeEntry();
    }

    public void writeFileEntry(ZipOutputStream zipOut, String entryName, Path sourceFile) throws IOException {
        String normalizedName = normalizeArchivePath(entryName);
        ZipEntry entry = new ZipEntry(normalizedName);
        zipOut.putNextEntry(entry);
        Files.copy(sourceFile, zipOut);
        zipOut.closeEntry();
    }

    public Path extractZipToTempDir(InputStream inputStream, String prefix) throws IOException {
        Path tempDir = Files.createTempDirectory(prefix);
        try (ZipInputStream zipIn = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String entryName = normalizeArchivePath(entry.getName());
                Path resolvedPath = tempDir.resolve(entryName).normalize();
                if (!resolvedPath.startsWith(tempDir)) {
                    throw new IllegalArgumentException("Archive entry escapes destination directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                } else {
                    Files.createDirectories(resolvedPath.getParent());
                    Files.copy(zipIn, resolvedPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipIn.closeEntry();
            }
        }
        return tempDir;
    }

    public String normalizeArchivePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Archive entry path must not be empty");
        }

        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
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

    @FunctionalInterface
    public interface ZipWriter {
        void write(ZipOutputStream zipOut) throws IOException;
    }
}
