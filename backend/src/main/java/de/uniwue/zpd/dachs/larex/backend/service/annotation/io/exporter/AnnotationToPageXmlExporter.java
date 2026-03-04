package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter;

import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.io.xml.PageXmlInputOutput;
import com.maxnth.page4j.dla.page.io.xml.StreamTarget;
import com.maxnth.page4j.dla.page.io.xml.XmlPageWriter;
import com.maxnth.page4j.dla.page.io.xml.XmlPageWriter_2019_07_15;
import com.maxnth.page4j.io.xml.IOError;
import com.maxnth.page4j.io.xml.XmlFormatVersion;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exporter for PageDto to PAGE XML format using the page4j library.
 * Uses the latest PAGE XML schema version (2019-07-15).
 */
@Component
public class AnnotationToPageXmlExporter {

    private static final Logger log = LoggerFactory.getLogger(AnnotationToPageXmlExporter.class);
    private static final XmlFormatVersion LATEST_VERSION = new XmlFormatVersion("2019-07-15");

    private final DtoToPage4jMapper mapper;

    public AnnotationToPageXmlExporter(DtoToPage4jMapper mapper) {
        this.mapper = mapper;
    }

    public String export(PageDto pageDto, PageXml originalXml) throws IOException {
        return exportToString(pageDto, originalXml);
    }

    public String exportToString(PageDto pageDto, PageXml originalXml) throws IOException {
        Page page = preparePage(pageDto);
        long startedAt = System.nanoTime();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PageXmlWriteResult result = writePage(page, new StreamTarget(outputStream), null);
            String xml = outputStream.toString(StandardCharsets.UTF_8);
            if (log.isDebugEnabled()) {
                log.debug("Exported PAGE XML in {} ms ({} bytes, warnings={})",
                        (System.nanoTime() - startedAt) / 1_000_000, result.bytesWritten(), result.warnings().size());
            }
            return xml;
        } catch (Exception e) {
            log.error("Failed to export PAGE XML", e);
            throw new IOException("Failed to export PAGE XML", e);
        }
    }

    public PageXmlWriteResult writeValidated(PageDto pageDto, PageXml originalXml, Path target) throws IOException {
        Page page = preparePage(pageDto);
        long startedAt = System.nanoTime();

        try (OutputStream outputStream = Files.newOutputStream(target)) {
            PageXmlWriteResult result = writePage(page, new StreamTarget(outputStream), target);
            if (log.isDebugEnabled()) {
                log.debug("Wrote validated PAGE XML to {} in {} ms ({} bytes, warnings={})",
                        target, (System.nanoTime() - startedAt) / 1_000_000, result.bytesWritten(), result.warnings().size());
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to write PAGE XML to {}", target, e);
            throw new IOException("Failed to write PAGE XML to " + target, e);
        }
    }

    /**
     * Get the MIME type for PAGE XML.
     */
    public String getMimeType() {
        return "application/xml";
    }

    /**
     * Get the file extension for PAGE XML.
     */
    public String getFileExtension() {
        return "xml";
    }

    private Page preparePage(PageDto pageDto) {
        if (pageDto == null) {
            throw new IllegalArgumentException("PageDto cannot be null");
        }

        long startedAt = System.nanoTime();
        Page page = mapper.toPage4j(pageDto);
        if (log.isDebugEnabled()) {
            log.debug("Mapped PageDto to page4j Page in {} ms (imageFilename={}, regions={}, formatVersion={})",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    page.getImageFilename(),
                    page.getLayout().getRegionCount(),
                    page.getFormatVersion());
        }
        return page;
    }

    private PageXmlWriteResult writePage(Page page, StreamTarget target, Path fileTarget) throws Exception {
        XmlPageWriter writer = PageXmlInputOutput.getWriter(LATEST_VERSION);
        boolean writeSuccess = writer.write(page, target);

        List<String> warnings = extractMessages(writer, false);
        List<String> errors = extractMessages(writer, true);
        long bytesWritten = fileTarget != null ? Files.size(fileTarget) : bytesWritten(target);

        if (!writeSuccess || !errors.isEmpty() || bytesWritten <= 0) {
            if (!errors.isEmpty()) {
                for (String error : errors) {
                    log.error("PAGE writer validation error: {}", error);
                }
            }
            if (!warnings.isEmpty()) {
                for (String warning : warnings) {
                    log.warn("PAGE writer validation warning: {}", warning);
                }
            }
            throw new IOException("PAGE XML validation failed before persistence");
        }

        if (!warnings.isEmpty()) {
            for (String warning : warnings) {
                log.warn("PAGE writer validation warning: {}", warning);
            }
        }

        return new PageXmlWriteResult(bytesWritten, List.copyOf(warnings), LATEST_VERSION.toString());
    }

    private long bytesWritten(StreamTarget target) {
        if (target.outputStream() instanceof ByteArrayOutputStream byteArrayOutputStream) {
            return byteArrayOutputStream.size();
        }
        return 0L;
    }

    private List<String> extractMessages(XmlPageWriter writer, boolean errorMessages) {
        List<String> messages = new ArrayList<>();
        if (writer instanceof XmlPageWriter_2019_07_15 writer2019) {
            List<IOError> errors = errorMessages ? writer2019.getErrors() : writer2019.getWarnings();
            if (errors == null) {
                return List.of();
            }
            for (IOError error : errors) {
                messages.add(formatError(error));
            }
        }
        return messages;
    }

    private String formatError(IOError error) {
        if (error == null) {
            return "unknown error";
        }
        return error.getMessage() != null ? error.getMessage() : "unknown error";
    }
}
