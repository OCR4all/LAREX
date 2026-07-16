package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class SpreadsheetExportWriter {

    DocumentExportService.StreamingDocumentExportResult render(Project project,
                                                               List<ExportPage> pages,
                                                               DocumentExportDto.ExportFormat format,
                                                               List<DocumentExportDto.SpreadsheetProfile> spreadsheetProfiles) throws IOException {
        List<DocumentExportDto.SpreadsheetProfile> profiles = resolveSpreadsheetProfiles(spreadsheetProfiles);
        String baseName = DocumentExportFileNames.sanitizeFileName(project.getName(), "project");

        if (profiles.size() == 1) {
            DocumentExportDto.SpreadsheetProfile profile = profiles.getFirst();
            String fileName = spreadsheetFileName(baseName, profile, format);
            return new DocumentExportService.StreamingDocumentExportResult(
                    fileName,
                    format.getContentType(),
                    outputStream -> writeSpreadsheetProfile(outputStream, project, pages, profile, format)
            );
        }

        String suffix = format == DocumentExportDto.ExportFormat.CSV ? "-csv.zip" : "-xlsx.zip";
        return new DocumentExportService.StreamingDocumentExportResult(
                baseName + suffix,
                "application/zip",
                outputStream -> writeSpreadsheetZipEntries(outputStream, project, pages, profiles, format, baseName)
        );
    }

    private void writeSpreadsheetZipEntries(OutputStream outputStream,
                                            Project project,
                                            List<ExportPage> pages,
                                            List<DocumentExportDto.SpreadsheetProfile> profiles,
                                            DocumentExportDto.ExportFormat format,
                                            String baseName) throws IOException {
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
        for (DocumentExportDto.SpreadsheetProfile profile : profiles) {
            zipOutputStream.putNextEntry(new ZipEntry(spreadsheetFileName(baseName, profile, format)));
            try {
                writeSpreadsheetProfile(zipOutputStream, project, pages, profile, format);
            } finally {
                zipOutputStream.closeEntry();
            }
        }
        zipOutputStream.finish();
    }

    private void writeSpreadsheetProfile(OutputStream outputStream,
                                         Project project,
                                         List<ExportPage> pages,
                                         DocumentExportDto.SpreadsheetProfile profile,
                                         DocumentExportDto.ExportFormat format) throws IOException {
        if (format == DocumentExportDto.ExportFormat.CSV) {
            writeCsv(outputStream, project, pages, profile);
            return;
        }
        writeXlsx(outputStream, project, pages, profile);
    }

    private String spreadsheetFileName(String baseName,
                                       DocumentExportDto.SpreadsheetProfile profile,
                                       DocumentExportDto.ExportFormat format) {
        String profileSlug = profile.name().toLowerCase(Locale.ROOT);
        String extension = format == DocumentExportDto.ExportFormat.CSV ? ".csv" : ".xlsx";
        return baseName + "-" + profileSlug + extension;
    }

    private void writeCsv(OutputStream outputStream,
                          Project project,
                          List<ExportPage> pages,
                          DocumentExportDto.SpreadsheetProfile profile) throws IOException {
        List<List<String>> rows = spreadsheetRows(project, pages, profile);
        for (List<String> row : rows) {
            outputStream.write(row.stream().map(this::csvCell).reduce((left, right) -> left + "," + right).orElse("").getBytes(StandardCharsets.UTF_8));
            outputStream.write('\n');
        }
    }

    private void writeXlsx(OutputStream outputStream,
                           Project project,
                           List<ExportPage> pages,
                           DocumentExportDto.SpreadsheetProfile profile) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(profile.name());
            List<List<String>> rows = spreadsheetRows(project, pages, profile);

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                List<String> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                    row.createCell(columnIndex).setCellValue(values.get(columnIndex));
                }
            }

            for (int i = 0; i < rows.getFirst().size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
        }
    }

    private List<List<String>> spreadsheetRows(Project project,
                                               List<ExportPage> pages,
                                               DocumentExportDto.SpreadsheetProfile profile) {
        return switch (profile) {
            case PAGE_METADATA -> pageMetadataRows(project, pages);
            case TAGS -> tagRows(project, pages);
            case REGIONS -> regionRows(project, pages);
        };
    }

    private List<List<String>> pageMetadataRows(Project project, List<ExportPage> pages) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "workspaceId", "workspaceName", "projectId", "projectName", "pageId", "pageName", "pageDescription",
                "created", "updated", "workflowState", "locked", "imageCount", "xmlFileCount", "primaryImageFileName", "primaryXmlFileName", "defaultGtIndex"
        ));

        for (ExportPage page : pages) {
            rows.add(List.of(
                    nullToEmpty(project.getLibrary().getWorkspaceId()),
                    nullToEmpty(project.getLibrary().getName()),
                    nullToEmpty(project.getId()),
                    nullToEmpty(project.getName()),
                    nullToEmpty(page.page().getId()),
                    nullToEmpty(page.page().getName()),
                    nullToEmpty(page.page().getDescription()),
                    timeValue(page.page().getCreated()),
                    timeValue(page.page().getUpdated()),
                    page.page().getWorkflowState().name(),
                    Boolean.toString(page.page().isEffectivelyLocked()),
                    Integer.toString(page.page().getImages() == null ? 0 : page.page().getImages().size()),
                    Integer.toString(page.page().getXmlFiles() == null ? 0 : page.page().getXmlFiles().size()),
                    page.image() == null ? "" : nullToEmpty(page.image().getFileName()),
                    page.pageXml() == null ? "" : nullToEmpty(page.pageXml().getFileName()),
                    Integer.toString(project.getEffectiveDefaultGtIndex())
            ));
        }
        return rows;
    }

    private List<List<String>> tagRows(Project project, List<ExportPage> pages) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("scope", "workspaceId", "projectId", "projectName", "pageId", "pageName", "tag"));

        for (String tag : project.getTags() == null ? List.<String>of() : project.getTags()) {
            rows.add(List.of(
                    "PROJECT",
                    nullToEmpty(project.getLibrary().getWorkspaceId()),
                    nullToEmpty(project.getId()),
                    nullToEmpty(project.getName()),
                    "",
                    "",
                    nullToEmpty(tag)
            ));
        }

        for (ExportPage page : pages) {
            for (String tag : page.page().getTags() == null ? List.<String>of() : page.page().getTags()) {
                rows.add(List.of(
                        "PAGE",
                        nullToEmpty(project.getLibrary().getWorkspaceId()),
                        nullToEmpty(project.getId()),
                        nullToEmpty(project.getName()),
                        nullToEmpty(page.page().getId()),
                        nullToEmpty(page.page().getName()),
                        nullToEmpty(tag)
                ));
            }
        }

        return rows;
    }

    private List<List<String>> regionRows(Project project, List<ExportPage> pages) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "workspaceId", "projectId", "projectName", "pageId", "pageName", "regionId", "parentRegionId", "kind", "type",
                "readingOrderIndex", "text", "bboxX", "bboxY", "bboxWidth", "bboxHeight", "polygon", "rows", "columns", "custom"
        ));

        for (ExportPage page : pages) {
            for (ExportRegion region : page.regions()) {
                PolygonDto.BoundingBoxDto box = region.coords() == null ? null : region.coords().getBoundingBox();
                rows.add(List.of(
                        nullToEmpty(project.getLibrary().getWorkspaceId()),
                        nullToEmpty(project.getId()),
                        nullToEmpty(project.getName()),
                        nullToEmpty(page.page().getId()),
                        nullToEmpty(page.page().getName()),
                        nullToEmpty(region.id()),
                        nullToEmpty(region.parentRegionId()),
                        region.kind() == null ? "" : region.kind().name(),
                        nullToEmpty(region.type()),
                        region.readingOrderIndex() == null ? "" : Integer.toString(region.readingOrderIndex()),
                        nullToEmpty(region.text()),
                        box == null ? "" : doubleToString(box.x()),
                        box == null ? "" : doubleToString(box.y()),
                        box == null ? "" : doubleToString(box.width()),
                        box == null ? "" : doubleToString(box.height()),
                        polygonToString(region.coords()),
                        region.rows() == null ? "" : Integer.toString(region.rows()),
                        region.columns() == null ? "" : Integer.toString(region.columns()),
                        nullToEmpty(region.custom())
                ));
            }
        }
        return rows;
    }

    private List<DocumentExportDto.SpreadsheetProfile> resolveSpreadsheetProfiles(List<DocumentExportDto.SpreadsheetProfile> spreadsheetProfiles) {
        if (spreadsheetProfiles == null || spreadsheetProfiles.isEmpty()) {
            return List.of(DocumentExportDto.SpreadsheetProfile.PAGE_METADATA);
        }
        return spreadsheetProfiles.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String csvCell(String value) {
        String normalized = value == null ? "" : value;
        boolean needsQuotes = normalized.contains(",")
                || normalized.contains("\"")
                || normalized.contains("\n")
                || normalized.contains("\r");
        String escaped = normalized.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    private String polygonToString(PolygonDto polygon) {
        if (polygon == null || polygon.points() == null || polygon.points().isEmpty()) {
            return "";
        }
        return polygon.points().stream()
                .map(point -> doubleToString(point.x()) + "," + doubleToString(point.y()))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private String doubleToString(Double value) {
        if (value == null) {
            return "";
        }
        return value % 1d == 0d ? Long.toString(value.longValue()) : Double.toString(value);
    }

    private String timeValue(java.time.LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
