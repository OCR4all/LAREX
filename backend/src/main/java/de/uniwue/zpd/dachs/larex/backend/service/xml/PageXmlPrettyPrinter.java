package de.uniwue.zpd.dachs.larex.backend.service.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Produces the canonical, human-readable representation used when PAGE XML is persisted.
 */
public final class PageXmlPrettyPrinter {

    private static final String INDENT_AMOUNT = "{http://xml.apache.org/xslt}indent-amount";

    private PageXmlPrettyPrinter() {
    }

    public static String prettyPrint(String xml) throws IOException {
        if (xml == null) {
            throw new IllegalArgumentException("PAGE XML cannot be null");
        }

        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            documentBuilderFactory.setXIncludeAware(false);
            documentBuilderFactory.setExpandEntityReferences(false);

            Document document = documentBuilderFactory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            removeFormattingWhitespace(document);

            // Use the JDK transformer so the widely supported Xalan indent amount is deterministic
            // even when Saxon is present on the application classpath.
            TransformerFactory transformerFactory = TransformerFactory.newDefaultInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(INDENT_AMOUNT, "2");

            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString().stripTrailing() + System.lineSeparator();
        } catch (Exception e) {
            throw new IOException("Could not pretty print PAGE XML", e);
        }
    }

    public static void prettyPrint(Path xmlPath) throws IOException {
        String formatted = prettyPrint(Files.readString(xmlPath, StandardCharsets.UTF_8));
        Path parent = xmlPath.toAbsolutePath().getParent();
        Path tempPath = Files.createTempFile(parent, xmlPath.getFileName().toString(), ".pretty-print.tmp");
        try {
            Files.writeString(tempPath, formatted, StandardCharsets.UTF_8);
            try {
                Files.move(tempPath, xmlPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, xmlPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private static void removeFormattingWhitespace(Node node) {
        NodeList children = node.getChildNodes();
        boolean hasElementChild = false;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                hasElementChild = true;
                break;
            }
        }

        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (hasElementChild && child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                node.removeChild(child);
            } else {
                removeFormattingWhitespace(child);
            }
        }
    }
}
