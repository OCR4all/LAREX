package de.uniwue.zpd.dachs.larex.backend.service.annotation.exporter;

import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.io.xml.PageXmlInputOutput;
import com.maxnth.page4j.dla.page.io.xml.StreamTarget;
import com.maxnth.page4j.dla.page.io.xml.XmlPageWriter;
import com.maxnth.page4j.io.xml.XmlFormatVersion;

import de.uniwue.zpd.dachs.larex.backend.dto.page.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper.DtoToPage4jMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

    /**
     * Export a PageDto to PAGE XML string.
     *
     * @param pageDto The PageDto to export
     * @param originalXml Original PageXml entity (for metadata preservation, can be null)
     * @return PAGE XML string
     * @throws IOException if export fails
     */
    public String export(PageDto pageDto, PageXml originalXml) throws IOException {
        if (pageDto == null) {
            throw new IllegalArgumentException("PageDto cannot be null");
        }

        try {
            log.info("Starting export of PageDto with {} regions", 
                     pageDto.regions() != null ? pageDto.regions().size() : 0);
            
            // Convert DTO to page4j Page
            Page page = mapper.toPage4j(pageDto);
            
            log.info("Converted to page4j Page: imageFilename={}, layout size={}x{}, regions={}, formatVersion={}", 
                     page.getImageFilename(),
                     page.getLayout().getWidth(), 
                     page.getLayout().getHeight(),
                     page.getLayout().getRegionCount(),
                     page.getFormatVersion());

            XmlPageWriter writer = PageXmlInputOutput.getWriter(LATEST_VERSION);
            log.info("Got writer for version {}", LATEST_VERSION);

            // Write to a ByteArrayOutputStream
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            boolean writeSuccess = writer.write(page, new StreamTarget(outputStream));
            log.info("Writer.write() returned: {}", writeSuccess);
            
            // Check for errors from the writer
            if (writer instanceof com.maxnth.page4j.dla.page.io.xml.XmlPageWriter_2019_07_15 writer2019) {
                var errors = writer2019.getErrors();
                var warnings = writer2019.getWarnings();
                if (errors != null && !errors.isEmpty()) {
                    log.error("Writer has {} validation errors:", errors.size());
                    for (var error : errors) {
                        log.error("  - {} at {}", error.getMessage(), 
                                  error instanceof com.maxnth.page4j.io.xml.XmlValidationError ve ? ve.getLocation() : "unknown");
                    }
                }
                if (warnings != null && !warnings.isEmpty()) {
                    log.warn("Writer warnings: {}", warnings);
                }
            }

            String result = outputStream.toString(StandardCharsets.UTF_8);
            log.info("Export produced {} bytes of XML", result.length());
            
            if (result.isEmpty()) {
                log.error("Export produced empty XML!");
            } else {
                log.debug("First 500 chars: {}", result.substring(0, Math.min(500, result.length())));
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to export PAGE XML", e);
            throw new IOException("Failed to export PAGE XML", e);
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

}
