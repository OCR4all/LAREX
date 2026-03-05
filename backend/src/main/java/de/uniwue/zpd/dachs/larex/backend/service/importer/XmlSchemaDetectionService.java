package de.uniwue.zpd.dachs.larex.backend.service.importer;

import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for detecting XML schema types and extracting version information.
 */
@Service
public class XmlSchemaDetectionService {

    /**
     * Detect the XML schema from file content.
     */
    public XmlSchemaInfo detectSchema(Path xmlFilePath) throws IOException {
        byte[] xmlContent = Files.readAllBytes(xmlFilePath);
        return detectSchema(new ByteArrayInputStream(xmlContent));
    }

    /**
     * Detect the XML schema from input stream.
     */
    public XmlSchemaInfo detectSchema(InputStream xmlInputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            // Parse the XML to get root element and namespace
            Document document = builder.parse(xmlInputStream);
            Element rootElement = document.getDocumentElement();

            String rootElementName = rootElement.getLocalName();
            String namespace = rootElement.getNamespaceURI();

            // Detect schema based on namespace first, then root element
            XmlSchema schema = XmlSchema.detectFromNamespace(namespace);
            if (schema == XmlSchema.UNKNOWN) {
                schema = XmlSchema.detectFromRootElement(rootElementName);
            }

            // Extract version information
            String version = extractVersionInfo(rootElement, schema);

            return new XmlSchemaInfo(schema, version, namespace, rootElementName);

        } catch (ParserConfigurationException | SAXException | IOException e) {
            // If we can't parse the XML, return unknown
            return new XmlSchemaInfo(XmlSchema.UNKNOWN, null, null, null);
        }
    }

    /**
     * Extract version information based on the detected schema.
     */
    private String extractVersionInfo(Element rootElement, XmlSchema schema) {
        return switch (schema) {
            case PAGE_XML -> extractPageXmlVersion(rootElement);
            case ALTO_XML -> extractAltoXmlVersion(rootElement);
            default -> null;
        };
    }

    private String extractPageXmlVersion(Element rootElement) {
        // PAGE XML version can be in different attributes
        String version = rootElement.getAttribute("pcGtsVersion");
        if (version == null || version.isEmpty()) {
            version = rootElement.getAttribute("version");
        }

        // Check for schema location which might contain version info
        if (version == null || version.isEmpty()) {
            String schemaLocation = rootElement.getAttribute("xsi:schemaLocation");
            if (schemaLocation != null && schemaLocation.contains("pagecontent")) {
                // Extract version from schema location URL
                if (schemaLocation.contains("2019-07-15")) {
                    version = "2019-07-15";
                } else if (schemaLocation.contains("2013-07-15")) {
                    version = "2013-07-15";
                } else if (schemaLocation.contains("2010-03-19")) {
                    version = "2010-03-19";
                }
            }
        }

        return version;
    }

    private String extractAltoXmlVersion(Element rootElement) {
        // ALTO version is usually in the root element attribute
        String version = rootElement.getAttribute("version");
        if (version == null || version.isEmpty()) {
            version = rootElement.getAttribute("VERSION");
        }

        // Check for schema location
        if (version == null || version.isEmpty()) {
            String schemaLocation = rootElement.getAttribute("xsi:schemaLocation");
            if (schemaLocation != null) {
                if (schemaLocation.contains("alto-4")) {
                    version = "4.0";
                } else if (schemaLocation.contains("alto-3")) {
                    version = "3.0";
                } else if (schemaLocation.contains("alto-2")) {
                    version = "2.0";
                }
            }
        }

        return version;
    }

    /**
     * Information about detected XML schema.
     */
    public static class XmlSchemaInfo {
        private final XmlSchema schema;
        private final String version;
        private final String namespace;
        private final String rootElementName;

        public XmlSchemaInfo(XmlSchema schema, String version, String namespace, String rootElementName) {
            this.schema = schema;
            this.version = version;
            this.namespace = namespace;
            this.rootElementName = rootElementName;
        }

        public XmlSchema getSchema() { return schema; }
        public String getVersion() { return version; }
        public String getNamespace() { return namespace; }
        public String getRootElementName() { return rootElementName; }

        @Override
        public String toString() {
            return String.format("XmlSchemaInfo{schema=%s, version='%s', namespace='%s', rootElement='%s'}",
                schema, version, namespace, rootElementName);
        }
    }
}