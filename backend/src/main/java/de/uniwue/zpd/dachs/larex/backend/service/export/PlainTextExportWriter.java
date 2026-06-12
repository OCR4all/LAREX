package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class PlainTextExportWriter {

    DocumentExportService.StreamingDocumentExportResult render(String baseName,
                                                               List<ExportPage> pages,
                                                               boolean includePageDelimiters,
                                                               DocumentExportDto.TextLevel textLevel,
                                                               int textVariantIndex) {
        return new DocumentExportService.StreamingDocumentExportResult(
                baseName + ".txt",
                DocumentExportDto.ExportFormat.TXT.getContentType(),
                outputStream -> write(outputStream, pages, includePageDelimiters, textLevel, textVariantIndex)
        );
    }

    private byte[] render(List<ExportPage> pages,
                          boolean includePageDelimiters,
                          DocumentExportDto.TextLevel textLevel,
                          int textVariantIndex) {
        StringBuilder builder = new StringBuilder();
        DocumentExportDto.TextLevel resolvedTextLevel = resolveTextLevel(textLevel);

        for (int i = 0; i < pages.size(); i++) {
            ExportPage page = pages.get(i);
            if (i > 0) {
                builder.append("\n\n");
            }
            if (includePageDelimiters && pages.size() > 1) {
                builder.append("===== Page: ")
                        .append(page.page().getName())
                        .append(" =====\n\n");
            }
            builder.append(renderPageText(page, resolvedTextLevel, textVariantIndex));
        }

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void write(OutputStream outputStream,
                       List<ExportPage> pages,
                       boolean includePageDelimiters,
                       DocumentExportDto.TextLevel textLevel,
                       int textVariantIndex) throws IOException {
        outputStream.write(render(pages, includePageDelimiters, textLevel, textVariantIndex));
    }

    private String renderPageText(ExportPage page,
                                  DocumentExportDto.TextLevel textLevel,
                                  int textVariantIndex) {
        List<String> fragments = extractTextFragments(page, textLevel, textVariantIndex);
        if (fragments.isEmpty()) {
            return "";
        }
        String separator = textLevel == DocumentExportDto.TextLevel.TEXT_LINE ? "\n" : "\n\n";
        return String.join(separator, fragments);
    }

    private List<String> extractTextFragments(ExportPage page,
                                              DocumentExportDto.TextLevel textLevel,
                                              int textVariantIndex) {
        List<ExportRegion> regions = ExportRegionExtractor.extractRegions(page.pageDto(), textVariantIndex).stream()
                .filter(ExportRegion::hasText)
                .toList();
        if (regions.isEmpty()) {
            return List.of();
        }

        return switch (textLevel) {
            case PAGE -> List.of(regions.stream()
                    .map(ExportRegion::text)
                    .filter(text -> text != null && !text.isBlank())
                    .reduce((left, right) -> left + "\n\n" + right)
                    .orElse(""));
            case REGION -> regions.stream()
                    .map(ExportRegion::text)
                    .filter(text -> text != null && !text.isBlank())
                    .toList();
            case TEXT_LINE -> regions.stream()
                    .flatMap(region -> region.lines().isEmpty()
                            ? Stream.of(region.text())
                            : region.lines().stream().map(ExportTextLine::text))
                    .filter(text -> text != null && !text.isBlank())
                    .toList();
        };
    }

    private DocumentExportDto.TextLevel resolveTextLevel(DocumentExportDto.TextLevel requestedTextLevel) {
        return requestedTextLevel == null ? DocumentExportDto.TextLevel.PAGE : requestedTextLevel;
    }
}
