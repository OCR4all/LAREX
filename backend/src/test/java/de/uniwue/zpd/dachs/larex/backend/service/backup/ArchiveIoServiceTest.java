package de.uniwue.zpd.dachs.larex.backend.service.backup;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveIoServiceTest {

    private final ArchiveIoService service = new ArchiveIoService(new ObjectMapper());

    @Test
    void rejectsArchivesWithTooManyEntries() throws Exception {
        byte[] archive = zip(Map.of("one.txt", bytes("1"), "two.txt", bytes("2")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> extract(archive, limits(archive.length + 1L, 1, 100, 100, 100))
        );

        assertTrue(error.getMessage().contains("more than 1 entries"));
    }

    @Test
    void rejectsEntriesThatExceedThePerEntryBudget() throws Exception {
        byte[] archive = zip(Map.of("large.txt", bytes("123456")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> extract(archive, limits(archive.length + 1L, 10, 5, 100, 100))
        );

        assertTrue(error.getMessage().contains("entry exceeds the allowed size"));
    }

    @Test
    void rejectsArchivesThatExceedTheTotalExtractionBudget() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("one.txt", bytes("1234"));
        entries.put("two.txt", bytes("5678"));
        byte[] archive = zip(entries);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> extract(archive, limits(archive.length + 1L, 10, 10, 7, 100))
        );

        assertTrue(error.getMessage().contains("allowed extracted size"));
    }

    @Test
    void rejectsArchivesThatExceedTheCompressedInputBudget() throws Exception {
        byte[] archive = zip(Map.of("file.txt", bytes("content")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> extract(archive, limits(10, 10, 100, 100, 100))
        );

        assertTrue(error.getMessage().contains("allowed compressed size"));
    }

    @Test
    void rejectsHighlyCompressedEntries() throws Exception {
        byte[] repeated = "a".repeat(10_000).getBytes(StandardCharsets.UTF_8);
        byte[] archive = zip(Map.of("repeated.txt", repeated));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> extract(archive, limits(archive.length + 1L, 10, 20_000, 20_000, 2))
        );

        assertTrue(error.getMessage().contains("allowed compression ratio"));
    }

    @Test
    void reportsTheCumulativeExtractedSizeAndEntryCount() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("one.txt", bytes("1234"));
        entries.put("nested/two.txt", bytes("56789"));
        byte[] archive = zip(entries);

        ArchiveIoService.ExtractionResult result = service.extractZipToTempDirWithReport(
                new ByteArrayInputStream(archive),
                "archive-report-test-",
                limits(archive.length + 1L, 10, 100, 100, 100)
        );
        try {
            assertEquals(9L, result.extractedBytes());
            assertEquals(2, result.entryCount());
            assertEquals("1234", Files.readString(result.directory().resolve("one.txt")));
            assertEquals("56789", Files.readString(result.directory().resolve("nested/two.txt")));
        } finally {
            try (var paths = Files.walk(result.directory())) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void extract(byte[] archive, ArchiveIoService.ExtractionLimits limits) throws Exception {
        service.extractZipToTempDir(
                new ByteArrayInputStream(archive),
                "archive-limits-test-",
                limits
        );
    }

    private ArchiveIoService.ExtractionLimits limits(long maxArchiveBytes,
                                                     int maxEntries,
                                                     long maxEntryBytes,
                                                     long maxTotalBytes,
                                                     double maxCompressionRatio) {
        return new ArchiveIoService.ExtractionLimits(
                maxArchiveBytes,
                maxEntries,
                maxEntryBytes,
                maxTotalBytes,
                maxCompressionRatio
        );
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
