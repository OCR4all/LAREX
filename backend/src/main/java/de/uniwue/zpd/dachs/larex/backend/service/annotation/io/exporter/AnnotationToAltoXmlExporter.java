package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter;

import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.io.xml.StreamTarget;
import com.maxnth.page4j.dla.page.io.xml.XmlPageWriter_Alto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exporter for PageDto to ALTO XML format.
 */
@Component
public class AnnotationToAltoXmlExporter {

    private final DtoToPage4jMapper mapper;

    public AnnotationToAltoXmlExporter(DtoToPage4jMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Export a PageDto to ALTO XML format.
     * Currently a stub - throws UnsupportedOperationException.
     *
     * @param page PageDto to export
     * @param outputPath Path where ALTO XML should be saved
     * @throws IOException if export fails
     */
    public void export(PageDto page, Path outputPath) throws IOException {
        Page page4j = preparePage(page);
        try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
            write(page4j, new StreamTarget(outputStream));
        } catch (Exception e) {
            throw new IOException("Failed to write ALTO XML to " + outputPath, e);
        }
    }

    /**
     * Convert a PageDto to ALTO XML string.
     * Currently a stub - throws UnsupportedOperationException.
     *
     * @param page PageDto to convert
     * @return ALTO XML as string
     */
    public String toXmlString(PageDto page) {
        Page page4j = preparePage(page);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            write(page4j, new StreamTarget(outputStream));
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to convert PAGE annotations to ALTO XML", e);
        }
    }

    private Page preparePage(PageDto pageDto) {
        if (pageDto == null) {
            throw new IllegalArgumentException("PageDto cannot be null");
        }
        return mapper.toPage4j(pageDto);
    }

    private void write(Page page, StreamTarget target) throws Exception {
        XmlPageWriter_Alto writer = new XmlPageWriter_Alto(null);
        boolean success = writer.write(page, target);
        List<String> errors = extractMessages(writer.getErrors());
        if (!success || !errors.isEmpty()) {
            throw new IOException("ALTO XML validation failed: " + String.join("; ", errors));
        }
    }

    private List<String> extractMessages(List<?> errors) {
        if (errors == null || errors.isEmpty()) {
            return List.of();
        }

        List<String> messages = new ArrayList<>();
        for (Object error : errors) {
            messages.add(error == null ? "unknown error" : error.toString());
        }
        return List.copyOf(messages);
    }
}
