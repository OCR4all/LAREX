package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Parser for ALTO XML format to PageDto.
 * TODO(larex): Implement ALTO parsing via page4j ALTO support.
 */
@Component
public class AltoXmlToAnnotationParser {

    /**
     * Parse an ALTO XML file to PageDto.
     * Currently a stub - throws UnsupportedOperationException.
     *
     * @param xmlPath Path to the ALTO XML file
     * @param pageXml PageXml entity with metadata
     * @return PageDto containing parsed data
     * @throws IOException if parsing fails
     */
    public PageDto parse(Path xmlPath, PageXml pageXml) throws IOException {
        // TODO(larex): Implement ALTO parsing with page4j's ALTO reader.

        if (!Files.exists(xmlPath)) {
            throw new IOException("XML file not found: " + xmlPath);
        }

        throw new UnsupportedOperationException(
            "ALTO XML parsing not yet migrated to page4j. " +
            "Use PAGE XML format or implement ALTO support using page4j's ALTO reader."
        );
    }

    /**
     * Check if a file is a valid ALTO XML file.
     */
    public boolean canParse(Path xmlPath) {
        try {
            String content = Files.readString(xmlPath);
            return content.contains("<alto") || content.contains("<ALTO") ||
                   content.contains("http://www.loc.gov/standards/alto/");
        } catch (IOException e) {
            return false;
        }
    }
}
