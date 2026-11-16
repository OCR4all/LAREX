package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter;

import com.maxnth.page4j.formats.AltoXml;
import com.maxnth.page4j.model.Page;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class AnnotationToAltoXmlExporter {

    private final DtoToPage4jMapper mapper;

    public AnnotationToAltoXmlExporter(DtoToPage4jMapper mapper) {
        this.mapper = mapper;
    }

    public void export(PageDto page, Path outputPath) throws IOException {
        try {
            Files.write(outputPath, AltoXml.create().write(preparePage(page)));
        } catch (RuntimeException e) {
            throw new IOException("Failed to write ALTO XML to " + outputPath, e);
        }
    }

    public String toXmlString(PageDto page) {
        try {
            return new String(AltoXml.create().write(preparePage(page)), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to convert PAGE annotations to ALTO XML", e);
        }
    }

    private Page preparePage(PageDto pageDto) {
        if (pageDto == null) {
            throw new IllegalArgumentException("PageDto cannot be null");
        }
        return mapper.toPage4j(pageDto);
    }
}
