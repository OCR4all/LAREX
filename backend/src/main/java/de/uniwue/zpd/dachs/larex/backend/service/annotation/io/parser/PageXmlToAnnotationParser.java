package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser;

import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.io.FileInput;
import com.maxnth.page4j.dla.page.io.xml.PageXmlInputOutput;
import com.maxnth.page4j.dla.page.io.xml.XmlPageReadResult;
import com.maxnth.page4j.dla.page.io.xml.XmlPageReader;
import com.maxnth.page4j.io.xml.IOError;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parser for PAGE XML format using the page4j library.
 * Converts PAGE XML files to PageDto for the frontend editor.
 */
@Component
public class PageXmlToAnnotationParser {

    private static final Logger log = LoggerFactory.getLogger(PageXmlToAnnotationParser.class);

    private final Page4jToDtoMapper mapper;

    public PageXmlToAnnotationParser(Page4jToDtoMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Parse a PAGE XML file to a PageDto.
     *
     * @param xmlPath Path to the PAGE XML file
     * @param pageXml PageXml entity with metadata
     * @return PageDto containing all parsed data
     * @throws IOException if the file cannot be read or parsed
     */
    public PageDto parse(Path xmlPath, PageXml pageXml) throws IOException {
        return parseWithPresence(xmlPath, pageXml).pageDto();
    }

    /** Parse PAGE XML and retain the source-presence information gathered in the same read flow. */
    public PageXmlParseResult parseWithPresence(Path xmlPath, PageXml pageXml) throws IOException {
        if (!Files.exists(xmlPath)) {
            throw new IOException("XML file not found: " + xmlPath);
        }

        try {
            long startedAt = System.nanoTime();
            XmlPageReader reader = PageXmlInputOutput.getReader();
            XmlPageReadResult readResult = reader.readWithSourceMetadata(new FileInput(xmlPath.toFile()));
            if (readResult == null) {
                String errors = reader.getErrors() == null
                    ? "unknown parser error"
                    : reader.getErrors().stream().map(IOError::getMessage).toList().toString();
                throw new IOException("page4j could not read PAGE XML: " + errors);
            }
            long parseMs = (System.nanoTime() - startedAt) / 1_000_000;

            startedAt = System.nanoTime();
            Page page = readResult.page();
            PageXmlPresenceIndex presenceIndex = PageXmlPresenceIndex.fromSourceMetadata(readResult.sourceMetadata());
            long presenceIndexMs = (System.nanoTime() - startedAt) / 1_000_000;

            // Convert to DTO using sparse presence information from source XML
            startedAt = System.nanoTime();
            PageDto dto = mapper.toDto(page, presenceIndex);
            long mapMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (log.isDebugEnabled()) {
                log.debug(
                        "Parsed PAGE XML {} in {} ms (page4j={} ms, source-index={} ms, mapper={} ms)",
                        xmlPath, presenceIndexMs + parseMs + mapMs, parseMs, presenceIndexMs, mapMs
                );
            }
            return new PageXmlParseResult(dto, presenceIndex);
        } catch (Exception e) {
            throw new IOException("Failed to parse PAGE XML file: " + xmlPath, e);
        }
    }

    /**
     * Check if a file is a valid PAGE XML file.
     */
    public boolean canParse(Path xmlPath) {
        try {
            String content = Files.readString(xmlPath);
            return content.contains("PcGts") || content.contains("pcGts") ||
                   content.contains("http://schema.primaresearch.org/PAGE");
        } catch (IOException e) {
            return false;
        }
    }
}
