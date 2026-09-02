package de.uniwue.zpd.dachs.larex.backend.service.xml;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.Serializer;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
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

            StringWriter output = new StringWriter();
            Processor processor = new Processor(false);
            Serializer serializer = processor.newSerializer(output);
            serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
            serializer.setOutputProperty(Serializer.Property.ENCODING, StandardCharsets.UTF_8.name());
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            serializer.setOutputProperty(Serializer.Property.STANDALONE, "omit");
            serializer.serialize(new DOMSource(document));
            return output.toString().stripTrailing() + "\n";
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
