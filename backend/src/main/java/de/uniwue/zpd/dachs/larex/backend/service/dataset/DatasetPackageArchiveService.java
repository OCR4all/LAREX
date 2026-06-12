package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.DatasetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DatasetPackageArchiveService {

    private final ArchiveIoService archiveIoService;
    private final ObjectMapper objectMapper;

    public DatasetPackageArchiveService(ArchiveIoService archiveIoService, ObjectMapper objectMapper) {
        this.archiveIoService = archiveIoService;
        this.objectMapper = objectMapper;
    }

    public long estimatePackageBytes(ExportSnapshot exportSnapshot) {
        long fileBytes = 0L;
        for (ExportFile exportFile : exportSnapshot.files()) {
            try {
                fileBytes += Files.size(exportFile.absolutePath());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read export file size", e);
            }
        }
        return fileBytes + 1_048_576L;
    }

    public void writePackageZip(Path outputPath, ExportSnapshot exportSnapshot) throws IOException {
        archiveIoService.writeZip(outputPath, zipOut -> writePackageEntries(zipOut, exportSnapshot));
    }

    public void writePackageZip(OutputStream outputStream, ExportSnapshot exportSnapshot) throws IOException {
        archiveIoService.writeZip(outputStream, zipOut -> writePackageEntries(zipOut, exportSnapshot));
    }

    private void writePackageEntries(java.util.zip.ZipOutputStream zipOut, ExportSnapshot exportSnapshot) throws IOException {
        archiveIoService.writeJsonEntry(zipOut, "manifest.json", exportSnapshot.manifest());
        archiveIoService.writeJsonEntry(zipOut, "stats.json", exportSnapshot.stats());
        for (Map.Entry<DatasetItem.Split, List<Map<String, Object>>> entry : exportSnapshot.jsonlRowsBySplit().entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            String splitName = entry.getKey().name().toLowerCase(Locale.ROOT);
            archiveIoService.writeStreamEntry(zipOut, "splits/" + splitName + ".jsonl", entryOut -> {
                Writer writer = new BufferedWriter(new OutputStreamWriter(entryOut, StandardCharsets.UTF_8));
                for (Map<String, Object> row : entry.getValue()) {
                    writer.write(objectMapper.writeValueAsString(row));
                    writer.write('\n');
                }
                writer.flush();
            });
        }
        for (ExportFile exportFile : exportSnapshot.files()) {
            archiveIoService.writeFileEntry(zipOut, exportFile.archivePath(), exportFile.absolutePath());
        }
    }

    public record ExportFile(String archivePath, Path absolutePath) {
    }

    public record ExportSnapshot(Map<String, Object> manifest,
                                 DatasetDto.StatsResponse stats,
                                 Map<DatasetItem.Split, List<Map<String, Object>>> jsonlRowsBySplit,
                                 List<ExportFile> files) {
    }
}
