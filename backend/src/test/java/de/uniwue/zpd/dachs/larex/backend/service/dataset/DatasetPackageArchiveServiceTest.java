package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import tools.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.DatasetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetPackageArchiveServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DatasetPackageArchiveService service = new DatasetPackageArchiveService(
            new ArchiveIoService(objectMapper),
            objectMapper
    );

    @TempDir
    Path tempDir;

    @Test
    void createsPackageEntriesAndEstimatesBytes() throws IOException {
        Path sourceFile = tempDir.resolve("page.xml");
        Files.writeString(sourceFile, "<PcGts />", StandardCharsets.UTF_8);
        DatasetPackageArchiveService.ExportSnapshot snapshot = new DatasetPackageArchiveService.ExportSnapshot(
                Map.of("datasetId", "dataset-1"),
                new DatasetDto.StatsResponse(1, 1, 0, 0, Map.of(), Map.of(), Map.of(), Map.of()),
                Map.of(DatasetItem.Split.TRAIN, List.of(Map.of("id", "item-1"))),
                List.of(new DatasetPackageArchiveService.ExportFile("files/page.xml", sourceFile))
        );

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.writePackageZip(outputStream, snapshot);
        byte[] packageBytes = outputStream.toByteArray();

        Map<String, String> entries = zipEntries(packageBytes);
        assertThat(entries).containsKeys("manifest.json", "stats.json", "splits/train.jsonl", "files/page.xml");
        assertThat(entries.get("splits/train.jsonl")).contains("\"id\":\"item-1\"");
        assertThat(entries.get("files/page.xml")).isEqualTo("<PcGts />");
        assertThat(service.estimatePackageBytes(snapshot)).isEqualTo(Files.size(sourceFile) + 1_048_576L);
    }

    private Map<String, String> zipEntries(byte[] packageBytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(packageBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zipIn.readAllBytes(), StandardCharsets.UTF_8));
                zipIn.closeEntry();
            }
        }
        return entries;
    }
}
