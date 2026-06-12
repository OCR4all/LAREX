package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Component
public class TeiExportWriter {

    private static final String PAGE2TEI_XSLT_RESOURCE_PATH = "/xslt/page2tei-0.xsl";

    private final AnnotationProcessingService annotationProcessingService;

    public TeiExportWriter(AnnotationProcessingService annotationProcessingService) {
        this.annotationProcessingService = annotationProcessingService;
    }

    DocumentExportService.StreamingDocumentExportResult render(String baseName,
                                                               Project project,
                                                               List<ExportPage> pages,
                                                               DocumentExportDto.TeiProfile teiProfile) {
        return new DocumentExportService.StreamingDocumentExportResult(
                baseName + ".tei.xml",
                DocumentExportDto.ExportFormat.TEI.getContentType(),
                outputStream -> outputStream.write(render(project, pages, teiProfile))
        );
    }

    private byte[] render(Project project,
                          List<ExportPage> pages,
                          DocumentExportDto.TeiProfile teiProfile) throws IOException {
        if (resolveTeiProfile(teiProfile) == DocumentExportDto.TeiProfile.LAYOUT) {
            return renderLayoutWithPage2Tei(pages);
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();
            String namespace = "http://www.tei-c.org/ns/1.0";

            Element tei = document.createElementNS(namespace, "TEI");
            document.appendChild(tei);
            appendTeiHeader(document, tei, pages.size() == 1 ? pages.getFirst().page().getName() : project.getName());

            Element text = document.createElementNS(namespace, "text");
            tei.appendChild(text);
            Element body = document.createElementNS(namespace, "body");
            text.appendChild(body);

            for (int i = 0; i < pages.size(); i++) {
                ExportPage page = pages.get(i);
                Element div = document.createElementNS(namespace, "div");
                div.setAttribute("type", "page");
                div.setAttributeNS(XMLConstants.XML_NS_URI, "xml:id", sanitizeXmlId("page-" + page.page().getId()));
                body.appendChild(div);

                Element head = document.createElementNS(namespace, "head");
                head.setTextContent(page.page().getName());
                div.appendChild(head);

                Element pb = document.createElementNS(namespace, "pb");
                pb.setAttribute("n", Integer.toString(i + 1));
                if (page.pageDto().imageFilename() != null && !page.pageDto().imageFilename().isBlank()) {
                    pb.setAttribute("facs", page.pageDto().imageFilename());
                }
                div.appendChild(pb);

                for (ExportRegion region : page.regions()) {
                    if (!region.hasText()) {
                        continue;
                    }
                    Element ab = document.createElementNS(namespace, "ab");
                    ab.setAttributeNS(XMLConstants.XML_NS_URI, "xml:id", sanitizeXmlId("region-" + region.id()));

                    if (!region.lines().isEmpty()) {
                        boolean firstLine = true;
                        for (ExportTextLine line : region.lines()) {
                            if (!line.hasText()) {
                                continue;
                            }
                            if (!firstLine) {
                                Element lb = document.createElementNS(namespace, "lb");
                                ab.appendChild(lb);
                            }
                            ab.appendChild(document.createTextNode(line.text()));
                            firstLine = false;
                        }
                    } else {
                        ab.setTextContent(region.text());
                    }
                    div.appendChild(ab);
                }
            }

            return serializeXml(document);
        } catch (Exception e) {
            throw new IOException("Failed to render TEI export", e);
        }
    }

    private byte[] renderLayoutWithPage2Tei(List<ExportPage> pages) throws IOException {
        Path tempDir = Files.createTempDirectory("larex-page2tei-");
        try {
            Path xmlDir = Files.createDirectories(tempDir.resolve("xml"));
            Path metsPath = tempDir.resolve("mets.xml");

            List<Page2TeiPageRef> pageRefs = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                ExportPage page = pages.get(i);
                String xmlFileName = String.format(Locale.ROOT, "page-%04d.xml", i + 1);
                Path xmlPath = xmlDir.resolve(xmlFileName);
                String pageXml = annotationProcessingService.exportAnnotationToXml(page.pageDto(), XmlSchema.PAGE_XML, page.pageXml().getId());
                Files.writeString(xmlPath, pageXml, StandardCharsets.UTF_8);
                pageRefs.add(new Page2TeiPageRef(i + 1, xmlPath.toUri().toString()));
            }

            Files.write(metsPath, buildPage2TeiMets(pageRefs));
            return transformWithPage2Tei(metsPath);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private void appendTeiHeader(Document document, Element tei, String titleText) {
        String namespace = tei.getNamespaceURI();
        Element teiHeader = document.createElementNS(namespace, "teiHeader");
        tei.appendChild(teiHeader);

        Element fileDesc = document.createElementNS(namespace, "fileDesc");
        teiHeader.appendChild(fileDesc);

        Element titleStmt = document.createElementNS(namespace, "titleStmt");
        fileDesc.appendChild(titleStmt);
        Element title = document.createElementNS(namespace, "title");
        title.setTextContent(titleText);
        titleStmt.appendChild(title);

        Element publicationStmt = document.createElementNS(namespace, "publicationStmt");
        fileDesc.appendChild(publicationStmt);
        Element publisher = document.createElementNS(namespace, "p");
        publisher.setTextContent("Generated by LAREX");
        publicationStmt.appendChild(publisher);

        Element sourceDesc = document.createElementNS(namespace, "sourceDesc");
        fileDesc.appendChild(sourceDesc);
        Element source = document.createElementNS(namespace, "p");
        source.setTextContent("Derived from PAGE XML annotations.");
        sourceDesc.appendChild(source);
    }

    private String sanitizeXmlId(String value) {
        String sanitized = value == null ? "id" : value.trim().replaceAll("[^A-Za-z0-9_.-]+", "-");
        return sanitized.isBlank() ? "id" : sanitized.toLowerCase(Locale.ROOT);
    }

    private DocumentExportDto.TeiProfile resolveTeiProfile(DocumentExportDto.TeiProfile teiProfile) {
        return teiProfile == null ? DocumentExportDto.TeiProfile.STANDARD : teiProfile;
    }

    private byte[] serializeXml(Document document) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        var transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.transform(new DOMSource(document), new StreamResult(outputStream));
        return outputStream.toByteArray();
    }

    private byte[] buildPage2TeiMets(List<Page2TeiPageRef> pageRefs) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();

            String metsNs = "http://www.loc.gov/METS/";
            String xlinkNs = "http://www.w3.org/1999/xlink";
            Element mets = document.createElementNS(metsNs, "mets:mets");
            mets.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:mets", metsNs);
            mets.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xlink", xlinkNs);
            document.appendChild(mets);

            Element fileSec = document.createElementNS(metsNs, "mets:fileSec");
            mets.appendChild(fileSec);

            Element fileGrp = document.createElementNS(metsNs, "mets:fileGrp");
            fileGrp.setAttribute("USE", "PAGEXML");
            fileSec.appendChild(fileGrp);

            for (Page2TeiPageRef pageRef : pageRefs) {
                Element file = document.createElementNS(metsNs, "mets:file");
                file.setAttribute("ID", "PAGE_" + pageRef.sequence());
                file.setAttribute("SEQ", Integer.toString(pageRef.sequence()));
                fileGrp.appendChild(file);

                Element flocat = document.createElementNS(metsNs, "mets:FLocat");
                flocat.setAttribute("LOCTYPE", "URL");
                flocat.setAttributeNS(xlinkNs, "xlink:href", pageRef.href());
                file.appendChild(flocat);
            }

            return serializeXml(document);
        } catch (Exception e) {
            throw new IOException("Failed to build temporary METS for page2tei", e);
        }
    }

    private byte[] transformWithPage2Tei(Path metsPath) throws IOException {
        URL xslUrl = TeiExportWriter.class.getResource(PAGE2TEI_XSLT_RESOURCE_PATH);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (xslUrl == null) {
                throw new IOException("Bundled page2tei stylesheet not found: " + PAGE2TEI_XSLT_RESOURCE_PATH);
            }

            Processor processor = new Processor(false);
            XsltCompiler compiler = processor.newXsltCompiler();
            StreamSource stylesheetSource = new StreamSource(xslUrl.toExternalForm());
            stylesheetSource.setSystemId(xslUrl.toExternalForm());
            XsltExecutable executable = compiler.compile(stylesheetSource);
            XsltTransformer transformer = executable.load();
            transformer.setSource(new StreamSource(metsPath.toFile()));

            Serializer serializer = processor.newSerializer(outputStream);
            serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            transformer.setDestination(serializer);
            transformer.transform();
            return outputStream.toByteArray();
        } catch (SaxonApiException e) {
            throw new IOException("Failed to transform PAGE XML to TEI via page2tei", e);
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private record Page2TeiPageRef(
            int sequence,
            String href
    ) {
    }
}
