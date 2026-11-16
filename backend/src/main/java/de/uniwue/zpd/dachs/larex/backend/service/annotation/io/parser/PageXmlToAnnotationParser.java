package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser;

import com.maxnth.page4j.convert.PageConverter;
import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.pagexml.PageXmlReadResult;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlVersionGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PageXmlToAnnotationParser {

    private static final Logger log = LoggerFactory.getLogger(PageXmlToAnnotationParser.class);

    private final Page4jToDtoMapper mapper;

    public PageXmlToAnnotationParser(Page4jToDtoMapper mapper) {
        this.mapper = mapper;
    }

    public PageDto parse(Path xmlPath, PageXml pageXml) throws IOException {
        return mapper.toDto(read(xmlPath, readBytes(xmlPath)), PageXmlConversionService.PRIMARY_VERSION);
    }

    public PageXmlParseResult parseWithAttributes(Path xmlPath, PageXml pageXml) throws IOException {
        byte[] bytes = readBytes(xmlPath);
        PageDto dto = mapper.toDto(read(xmlPath, bytes), PageXmlConversionService.PRIMARY_VERSION);
        return new PageXmlParseResult(dto, PageXmlAttributeScanner.scan(bytes));
    }

    private static byte[] readBytes(Path xmlPath) throws IOException {
        if (!Files.exists(xmlPath)) {
            throw new IOException("XML file not found: " + xmlPath);
        }
        return Files.readAllBytes(xmlPath);
    }

    private static Page read(Path xmlPath, byte[] bytes) throws IOException {
        long startedAt = System.nanoTime();
        try {
            PageXmlReadResult result = PageXmlVersionGate.requireReadable(com.maxnth.page4j.pagexml.PageXml.create().read(bytes));
            Page page = result.version() == PageXmlConversionService.PRIMARY_VERSION
                    ? result.page()
                    : PageConverter.convert(result.page(), PageXmlConversionService.PRIMARY_VERSION).page();
            if (log.isDebugEnabled()) {
                log.debug("Read PAGE XML {} in {} ms", xmlPath, (System.nanoTime() - startedAt) / 1_000_000);
            }
            return page;
        } catch (Exception e) {
            throw new IOException("Failed to parse PAGE XML file: " + xmlPath + ": " + e.getMessage(), e);
        }
    }

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
