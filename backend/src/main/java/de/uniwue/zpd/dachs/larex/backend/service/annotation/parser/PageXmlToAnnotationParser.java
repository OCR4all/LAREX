package de.uniwue.zpd.dachs.larex.backend.service.annotation.parser;

import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.io.xml.PageXmlInputOutput;

import de.uniwue.zpd.dachs.larex.backend.dto.page.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper.Page4jToDtoMapper;
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
        if (!Files.exists(xmlPath)) {
            throw new IOException("XML file not found: " + xmlPath);
        }

        try {
            String originalXml = Files.readString(xmlPath);
            PageXmlPresenceIndex presenceIndex = PageXmlPresenceIndex.fromXml(originalXml);

            // Use page4j to parse the PAGE XML file
            Page page = PageXmlInputOutput.readPage(xmlPath.toString());

            // Convert to DTO using sparse presence information from source XML
            return mapper.toDto(page, presenceIndex);
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
