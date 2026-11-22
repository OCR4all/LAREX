package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter;

import com.maxnth.page4j.formats.AltoXml;
import com.maxnth.page4j.formats.FineReaderXml;
import com.maxnth.page4j.formats.Hocr;
import com.maxnth.page4j.model.Page;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class AnnotationToOcrFormatExporter {

    private final DtoToPage4jMapper mapper;

    public AnnotationToOcrFormatExporter(DtoToPage4jMapper mapper) {
        this.mapper = mapper;
    }

    public static boolean supports(XmlSchema format) {
        return format == XmlSchema.ALTO_XML || format == XmlSchema.FINE_READER_XML || format == XmlSchema.HOCR;
    }

    public String toXmlString(PageDto page, XmlSchema format) {
        if (!supports(format)) {
            throw new UnsupportedOperationException("No exporter available for schema: " + format);
        }
        Page prepared = preparePage(page);
        try {
            byte[] xml = switch (format) {
                case FINE_READER_XML -> FineReaderXml.create().write(prepared);
                case HOCR -> Hocr.create().write(prepared);
                default -> AltoXml.create().write(prepared);
            };
            return new String(xml, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to convert PAGE annotations to " + format.getDisplayName(), e);
        }
    }

    private Page preparePage(PageDto pageDto) {
        if (pageDto == null) {
            throw new IllegalArgumentException("PageDto cannot be null");
        }
        return mapper.toPage4j(pageDto);
    }
}
