package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class PageXmlAttributeScanner {

    private static final Logger log = LoggerFactory.getLogger(PageXmlAttributeScanner.class);
    private static final XMLInputFactory FACTORY = createFactory();

    public record AttributeOccurrence(String elementName, String attributeName, String value) {}

    private PageXmlAttributeScanner() {}

    public static Set<AttributeOccurrence> scan(Path xmlPath) {
        if (xmlPath == null) {
            return Set.of();
        }
        try {
            return scan(Files.readAllBytes(xmlPath));
        } catch (IOException e) {
            log.warn("Could not read {} for the PAGE attribute index: {}", xmlPath, e.getMessage());
            return Set.of();
        }
    }

    public static Set<AttributeOccurrence> scan(byte[] xml) {
        Set<AttributeOccurrence> occurrences = new HashSet<>();
        XMLStreamReader reader = null;
        try {
            reader = FACTORY.createXMLStreamReader(new ByteArrayInputStream(xml));
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String elementName = reader.getLocalName();
                for (int i = 0; i < reader.getAttributeCount(); i++) {
                    occurrences.add(new AttributeOccurrence(elementName, reader.getAttributeLocalName(i), reader.getAttributeValue(i)));
                }
            }
        } catch (XMLStreamException e) {
            log.warn("Could not scan PAGE XML attributes for the attribute index: {}", e.getMessage());
            return Set.of();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                }
            }
        }
        return Set.copyOf(occurrences);
    }

    private static XMLInputFactory createFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, false);
        return factory;
    }
}
