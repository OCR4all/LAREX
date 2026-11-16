package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter;

import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.pagexml.PageWriteException;
import com.maxnth.page4j.pagexml.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.PageDtoMappingException;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlPrettyPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class AnnotationToPageXmlExporter {

    private static final Logger log = LoggerFactory.getLogger(AnnotationToPageXmlExporter.class);
    private static final PageXmlVersion VERSION = PageXmlVersion.V2019_07_15;
    private static final String VALIDATION_FAILED = "PAGE XML validation failed before persistence";

    private final DtoToPage4jMapper mapper;

    public AnnotationToPageXmlExporter(DtoToPage4jMapper mapper) {
        this.mapper = mapper;
    }

    public String export(PageDto pageDto, PageXml originalXml) throws IOException {
        return exportToString(pageDto, originalXml);
    }

    public String exportToString(PageDto pageDto, PageXml originalXml) throws IOException {
        long startedAt = System.nanoTime();
        try {
            byte[] xml = write(preparePage(pageDto));
            if (log.isDebugEnabled()) {
                log.debug("Exported PAGE XML in {} ms ({} bytes)", (System.nanoTime() - startedAt) / 1_000_000, xml.length);
            }
            return new String(xml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to export PAGE XML: {}", e.getMessage());
            throw new IOException("Failed to export PAGE XML", e);
        }
    }

    public PageXmlWriteResult writeValidated(PageDto pageDto, PageXml originalXml, Path target) throws IOException {
        long startedAt = System.nanoTime();
        try {
            byte[] xml = write(preparePage(pageDto));
            Files.write(target, xml);
            if (log.isDebugEnabled()) {
                log.debug("Wrote validated PAGE XML to {} in {} ms ({} bytes)",
                        target, (System.nanoTime() - startedAt) / 1_000_000, xml.length);
            }
            return new PageXmlWriteResult(xml.length, List.of(), VERSION.date());
        } catch (IOException e) {
            log.error("Failed to write PAGE XML to {}: {}", target, e.getMessage());
            throw new IOException("Failed to write PAGE XML to " + target, e);
        }
    }

    public String getMimeType() {
        return "application/xml";
    }

    public String getFileExtension() {
        return "xml";
    }

    private Page preparePage(PageDto pageDto) throws IOException {
        if (pageDto == null) {
            throw new IllegalArgumentException("PageDto cannot be null");
        }
        try {
            return mapper.toPage4j(pageDto);
        } catch (PageDtoMappingException e) {
            throw new IOException(VALIDATION_FAILED + ": " + e.getMessage(), e);
        }
    }

    private static byte[] write(Page page) throws IOException {
        try {
            byte[] xml = com.maxnth.page4j.pagexml.PageXml.create().write(page, VERSION);
            return PageXmlPrettyPrinter.prettyPrint(new String(xml, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        } catch (PageWriteException e) {
            e.problems().forEach(problem -> log.error("PAGE writer validation error: {}", problem));
            throw new IOException(VALIDATION_FAILED + ": " + String.join("; ", e.problems()), e);
        }
    }
}
