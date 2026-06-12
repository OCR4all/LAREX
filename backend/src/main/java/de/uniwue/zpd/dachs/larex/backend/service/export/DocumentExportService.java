package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PointDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.ReadingOrderDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionKind;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.WordDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.transform.stream.StreamSource;

@Service
@Transactional(readOnly = true)
public class DocumentExportService {

    private static final String PDF_FONT_RESOURCE_PATH = "/fonts/Junicode.ttf";
    private static final String PDF_A_ICC_RESOURCE_PATH = "/color/sRGB.icc";
    private static final String PAGE2TEI_XSLT_RESOURCE_PATH = "/xslt/page2tei-0.xsl";
    private static final double UNCLEAR_CONFIDENCE_THRESHOLD = 0.75d;
    private static final Comparator<PageXml> PAGE_XML_COMPARATOR =
            Comparator.comparing((PageXml xml) -> xml.getVariant() == null ? "" : xml.getVariant(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(PageXml::getId);
    private static final Comparator<PageImage> PAGE_IMAGE_COMPARATOR =
            Comparator.comparing((PageImage image) -> image.getVariant() == null ? "" : image.getVariant(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(PageImage::getId);

    private final ProjectRepository projectRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final AnnotationProcessingService annotationProcessingService;
    private final PageXmlConversionService pageXmlConversionService;
    private final PageOrderService pageOrderService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public DocumentExportService(ProjectRepository projectRepository,
                                 WorkspaceAccessService workspaceAccessService,
                                 AnnotationProcessingService annotationProcessingService,
                                 PageXmlConversionService pageXmlConversionService,
                                 PageOrderService pageOrderService) {
        this.projectRepository = projectRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.annotationProcessingService = annotationProcessingService;
        this.pageXmlConversionService = pageXmlConversionService;
        this.pageOrderService = pageOrderService;
    }

    public DocumentExportResult exportPage(String projectId,
                                           String pageId,
                                           String userId,
                                           DocumentExportDto.PageExportRequest request) throws IOException {
        StreamingDocumentExportResult streamingResult = exportPageStream(projectId, pageId, userId, request);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        streamingResult.writer().write(outputStream);
        return new DocumentExportResult(streamingResult.fileName(), streamingResult.contentType(), outputStream.toByteArray());
    }

    public StreamingDocumentExportResult exportPageStream(String projectId,
                                                          String pageId,
                                                          String userId,
                                                          DocumentExportDto.PageExportRequest request) throws IOException {
        DocumentExportDto.ExportFormat format = requireSupportedPageFormat(request);
        Project project = projectRepository.findWithAssociationsById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        workspaceAccessService.requireWorkspaceAccess(project.getLibrary().getWorkspaceId(), userId);

        Page page = project.getPages().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), pageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));

        if (format == DocumentExportDto.ExportFormat.PAGE_XML) {
            return exportPageXmlStream(page, request == null ? null : request.targetPageXmlVersion());
        }

        List<PreparedPageExport> preparedPages = preparePages(project, List.of(page));
        return renderExportStream(project, preparedPages, ExportOptions.fromPageRequest(request), true);
    }

    public StreamingDocumentExportResult exportPageXmlStream(String projectId,
                                                             String pageId,
                                                             String userId,
                                                             String targetPageXmlVersion) throws IOException {
        Project project = projectRepository.findWithAssociationsById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        workspaceAccessService.requireWorkspaceAccess(project.getLibrary().getWorkspaceId(), userId);

        Page page = project.getPages().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), pageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Page not found: " + pageId));

        return exportPageXmlStream(page, targetPageXmlVersion);
    }

    private StreamingDocumentExportResult exportPageXmlStream(Page page, String targetPageXmlVersion) throws IOException {
        PageXml pageXml = resolvePrimaryPageXml(page);
        if (pageXml == null) {
            throw new IllegalArgumentException("No PAGE XML file found for page: " + page.getId());
        }

        Path xmlPath = resolveUploadPath(pageXml.getFilePath());
        if (xmlPath == null || !Files.exists(xmlPath)) {
            throw new IllegalArgumentException("PAGE XML file not found for page: " + page.getId());
        }

        String normalizedTarget = pageXmlConversionService.normalizeTargetVersion(targetPageXmlVersion);
        String fileName = sanitizeFileName(pageXml.getFileName(), sanitizeFileName(page.getName(), "page") + ".xml");
        String contentType = pageXml.getMimeType() == null || pageXml.getMimeType().isBlank()
                ? DocumentExportDto.ExportFormat.PAGE_XML.getContentType()
                : pageXml.getMimeType();
        return new StreamingDocumentExportResult(
                fileName,
                contentType,
                outputStream -> pageXmlConversionService.writeFileToVersion(xmlPath, normalizedTarget, outputStream)
        );
    }

    public DocumentExportResult exportProject(String workspaceId,
                                              String projectId,
                                              String userId,
                                              DocumentExportDto.ProjectExportRequest request) throws IOException {
        StreamingDocumentExportResult streamingResult = exportProjectStream(workspaceId, projectId, userId, request);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        streamingResult.writer().write(outputStream);
        return new DocumentExportResult(streamingResult.fileName(), streamingResult.contentType(), outputStream.toByteArray());
    }

    public StreamingDocumentExportResult exportProjectStream(String workspaceId,
                                                             String projectId,
                                                             String userId,
                                                             DocumentExportDto.ProjectExportRequest request) throws IOException {
        DocumentExportDto.ExportFormat format = requireSupportedProjectFormat(request);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        Project project = projectRepository.findWithAssociationsById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!workspaceId.equals(project.getLibrary().getWorkspaceId())) {
            throw new IllegalArgumentException("Project does not belong to workspace");
        }

        List<Page> selectedPages = resolvePages(project, request.pageIds());
        List<PreparedPageExport> preparedPages = preparePages(project, selectedPages);
        return renderExportStream(project, preparedPages, ExportOptions.fromProjectRequest(request), false);
    }

    public List<EmbeddedProjectOutput> exportEmbeddedProjectOutputs(Project project,
                                                                    List<Page> pages,
                                                                    List<DocumentExportDto.EmbeddedProjectOutputRequest> requests) throws IOException {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<PreparedPageExport> preparedPages = preparePages(project, pages);
        Map<String, EmbeddedProjectOutput> outputsByPath = new LinkedHashMap<>();

        for (DocumentExportDto.EmbeddedProjectOutputRequest request : requests) {
            if (request == null || request.format() == null) {
                continue;
            }
            if (!request.format().supportsProjectPackageEmbedding()) {
                throw new IllegalArgumentException("Unsupported embedded project output format: " + request.format());
            }

            StreamingDocumentExportResult export = renderExportStream(project, preparedPages, ExportOptions.fromEmbeddedRequest(request), false);
            String archivePath = "exports/" + export.fileName();
            if (outputsByPath.containsKey(archivePath)) {
                continue;
            }

            Path tempFile = Files.createTempFile("larex-embedded-export-", fileExtension(export.fileName()));
            try {
                try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                    export.writer().write(outputStream);
                }
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(tempFile);
                throw e;
            }
            outputsByPath.put(archivePath, new EmbeddedProjectOutput(archivePath, tempFile, Files.size(tempFile)));
        }

        return List.copyOf(outputsByPath.values());
    }

    private DocumentExportDto.ExportFormat requireSupportedPageFormat(DocumentExportDto.PageExportRequest request) {
        DocumentExportDto.ExportFormat format = request == null ? null : request.format();
        if (format == null || !format.supportsPageExportEndpoint()) {
            throw new IllegalArgumentException("Unsupported page export format: " + format);
        }
        return format;
    }

    private DocumentExportDto.ExportFormat requireSupportedProjectFormat(DocumentExportDto.ProjectExportRequest request) {
        DocumentExportDto.ExportFormat format = request == null ? null : request.format();
        if (format == null || !format.supportsProjectExportEndpoint()) {
            throw new IllegalArgumentException("Unsupported project export format: " + format);
        }
        return format;
    }

    private List<Page> resolvePages(Project project, List<String> selectedPageIds) {
        List<Page> sortedPages = project.getPages().stream()
                .sorted(pageOrderService.projectOrderComparator())
                .toList();

        if (selectedPageIds == null || selectedPageIds.isEmpty()) {
            return sortedPages;
        }

        Set<String> selectedIds = new HashSet<>(selectedPageIds);
        return sortedPages.stream()
                .filter(page -> selectedIds.contains(page.getId()))
                .toList();
    }

    private List<PreparedPageExport> preparePages(Project project, List<Page> pages) throws IOException {
        int gtIndex = project.getEffectiveDefaultGtIndex();
        List<PreparedPageExport> preparedPages = new ArrayList<>();

        for (Page page : pages) {
            PageXml primaryXml = resolvePrimaryXml(page);
            if (primaryXml == null) {
                throw new IllegalArgumentException("No PAGE XML file found for page: " + page.getId());
            }

            PageDto pageDto = annotationProcessingService.parseXmlToAnnotation(primaryXml.getId());
            PageImage primaryImage = resolvePrimaryImage(page);
            Path imagePath = primaryImage == null ? null : resolveUploadPath(primaryImage.getFilePath());
            preparedPages.add(new PreparedPageExport(
                    page,
                    primaryXml,
                    pageDto,
                    extractRegions(pageDto, gtIndex),
                    primaryImage,
                    imagePath
            ));
        }

        return preparedPages;
    }

    private StreamingDocumentExportResult renderExportStream(Project project,
                                                             List<PreparedPageExport> pages,
                                                             ExportOptions options,
                                                             boolean pageScope) throws IOException {
        if (options.format() == DocumentExportDto.ExportFormat.CSV || options.format() == DocumentExportDto.ExportFormat.XLSX) {
            if (pageScope) {
                throw new IllegalArgumentException("Spreadsheet export is only available for projects");
            }
            return renderSpreadsheetExportStream(project, pages, options);
        }

        String baseName = pages.size() == 1
                ? sanitizeFileName(pages.get(0).page().getName(), "page")
                : sanitizeFileName(project.getName(), "project");

        return switch (options.format()) {
            case TXT -> new StreamingDocumentExportResult(
                    baseName + ".txt",
                    DocumentExportDto.ExportFormat.TXT.getContentType(),
                    outputStream -> writeText(outputStream, pages, options.includePageDelimiters(), options.textLevel(), options.textVariantIndex())
            );
            case DOCX -> new StreamingDocumentExportResult(
                    baseName + ".docx",
                    DocumentExportDto.ExportFormat.DOCX.getContentType(),
                    outputStream -> writeDocx(outputStream, project, pages, options.docxOptions(), pageScope)
            );
            case TEI -> new StreamingDocumentExportResult(
                    baseName + ".tei.xml",
                    DocumentExportDto.ExportFormat.TEI.getContentType(),
                    outputStream -> outputStream.write(renderTei(project, pages, options.teiProfile()))
            );
            case PDF -> new StreamingDocumentExportResult(
                    baseName + ".pdf",
                    DocumentExportDto.ExportFormat.PDF.getContentType(),
                    outputStream -> writePdf(outputStream, project, pages, options.pdfProfile())
            );
            case ALTO_XML -> renderAltoStream(project, pages);
            case PAGE_XML -> throw new IllegalArgumentException("PAGE XML export is only supported on the legacy page endpoint");
            case CSV, XLSX -> throw new IllegalStateException("Spreadsheet formats handled above");
        };
    }

    private StreamingDocumentExportResult renderAltoStream(Project project, List<PreparedPageExport> pages) throws IOException {
        if (pages.size() == 1) {
            PreparedPageExport page = pages.getFirst();
            String xml = annotationProcessingService.exportAnnotationToXml(page.pageDto(), XmlSchema.ALTO_XML, page.pageXml().getId());
            String fileName = sanitizeFileName(page.page().getName(), "page") + ".alto.xml";
            return new StreamingDocumentExportResult(
                    fileName,
                    DocumentExportDto.ExportFormat.ALTO_XML.getContentType(),
                    outputStream -> outputStream.write(xml.getBytes(StandardCharsets.UTF_8))
            );
        }

        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (PreparedPageExport page : pages) {
            String xml = annotationProcessingService.exportAnnotationToXml(page.pageDto(), XmlSchema.ALTO_XML, page.pageXml().getId());
            String fileName = sanitizeFileName(page.page().getName(), "page") + ".alto.xml";
            entries.put(fileName, xml.getBytes(StandardCharsets.UTF_8));
        }
        String baseName = sanitizeFileName(project.getName(), "project");
        return new StreamingDocumentExportResult(
                baseName + ".alto.zip",
                "application/zip",
                outputStream -> writeZipEntries(outputStream, entries)
        );
    }

    private byte[] renderText(List<PreparedPageExport> pages,
                              boolean includePageDelimiters,
                              DocumentExportDto.TextLevel textLevel,
                              int textVariantIndex) {
        StringBuilder builder = new StringBuilder();
        DocumentExportDto.TextLevel resolvedTextLevel = resolveTextLevel(textLevel);

        for (int i = 0; i < pages.size(); i++) {
            PreparedPageExport page = pages.get(i);
            if (i > 0) {
                builder.append("\n\n");
            }
            if (includePageDelimiters && pages.size() > 1) {
                builder.append("===== Page: ")
                        .append(page.page().getName())
                        .append(" =====\n\n");
            }
            builder.append(renderPageText(page, resolvedTextLevel, textVariantIndex));
        }

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void writeText(OutputStream outputStream,
                           List<PreparedPageExport> pages,
                           boolean includePageDelimiters,
                           DocumentExportDto.TextLevel textLevel,
                           int textVariantIndex) throws IOException {
        outputStream.write(renderText(pages, includePageDelimiters, textLevel, textVariantIndex));
    }

    private void writeDocx(OutputStream outputStream,
                           Project project,
                           List<PreparedPageExport> pages,
                           ResolvedDocxOptions options,
                           boolean pageScope) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText(pages.size() == 1 ? pages.get(0).page().getName() : project.getName());

            for (int i = 0; i < pages.size(); i++) {
                PreparedPageExport page = pages.get(i);

                XWPFParagraph heading = document.createParagraph();
                heading.setStyle("Heading1");
                XWPFRun headingRun = heading.createRun();
                headingRun.setBold(true);
                headingRun.setFontSize(14);
                headingRun.setText(page.page().getName());

                if (options.includeImageNames() && page.image() != null && page.image().getFileName() != null) {
                    XWPFParagraph imageParagraph = document.createParagraph();
                    XWPFRun imageRun = imageParagraph.createRun();
                    imageRun.setItalic(true);
                    imageRun.setUnderline(UnderlinePatterns.SINGLE);
                    imageRun.setText(page.image().getFileName());
                }

                for (PreparedRegion region : page.regions()) {
                    if (!region.hasText()) {
                        continue;
                    }
                    XWPFParagraph paragraph = document.createParagraph();
                    appendRegionToDocx(paragraph, region, options);
                }

                if (!pageScope && options.forcePageBreaks() && i < pages.size() - 1) {
                    XWPFParagraph breakParagraph = document.createParagraph();
                    XWPFRun breakRun = breakParagraph.createRun();
                    breakRun.addBreak(BreakType.PAGE);
                }
            }

            document.write(outputStream);
        }
    }

    private void appendRegionToDocx(XWPFParagraph paragraph,
                                    PreparedRegion region,
                                    ResolvedDocxOptions options) {
        if (options.preserveLineBreaks()) {
            List<PreparedTextLine> lines = region.lines().stream().filter(PreparedTextLine::hasText).toList();
            if (!lines.isEmpty()) {
                for (int i = 0; i < lines.size(); i++) {
                    appendLineToDocx(paragraph, lines.get(i), options.markUnclearWords());
                    if (i < lines.size() - 1) {
                        paragraph.createRun().addBreak();
                    }
                }
                return;
            }
        }

        if (!region.lines().isEmpty()) {
            boolean firstToken = true;
            for (PreparedTextLine line : region.lines()) {
                if (!line.hasText()) {
                    continue;
                }
                if (!firstToken) {
                    paragraph.createRun().setText(" ");
                }
                appendLineInlineToDocx(paragraph, line, options.markUnclearWords());
                firstToken = false;
            }
            return;
        }

        XWPFRun run = paragraph.createRun();
        run.setText(nullToEmpty(region.text()));
    }

    private void appendLineToDocx(XWPFParagraph paragraph,
                                  PreparedTextLine line,
                                  boolean markUnclearWords) {
        if (!line.words().isEmpty()) {
            boolean first = true;
            for (PreparedWord word : line.words()) {
                if (!word.hasText()) {
                    continue;
                }
                if (!first) {
                    paragraph.createRun().setText(" ");
                }
                XWPFRun run = paragraph.createRun();
                styleRunForUnclear(run, markUnclearWords && isUnclear(word, line));
                run.setText(word.text());
                first = false;
            }
            return;
        }

        XWPFRun run = paragraph.createRun();
        styleRunForUnclear(run, markUnclearWords && isUnclear(line));
        run.setText(nullToEmpty(line.text()));
    }

    private void appendLineInlineToDocx(XWPFParagraph paragraph,
                                        PreparedTextLine line,
                                        boolean markUnclearWords) {
        appendLineToDocx(paragraph, line, markUnclearWords);
    }

    private void styleRunForUnclear(XWPFRun run, boolean unclear) {
        if (!unclear) {
            return;
        }
        run.setItalic(true);
        run.setTextHighlightColor("yellow");
    }

    private boolean isUnclear(PreparedWord word, PreparedTextLine line) {
        Double confidence = firstNonNull(word.confidence(), word.variantConfidence(), line.variantConfidence(), line.confidence());
        return confidence != null && confidence < UNCLEAR_CONFIDENCE_THRESHOLD;
    }

    private boolean isUnclear(PreparedTextLine line) {
        Double confidence = firstNonNull(line.variantConfidence(), line.confidence());
        return confidence != null && confidence < UNCLEAR_CONFIDENCE_THRESHOLD;
    }

    private byte[] renderTei(Project project,
                             List<PreparedPageExport> pages,
                             DocumentExportDto.TeiProfile teiProfile) throws IOException {
        if (resolveTeiProfile(teiProfile) == DocumentExportDto.TeiProfile.LAYOUT) {
            return renderTeiLayoutWithPage2Tei(pages);
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();
            String namespace = "http://www.tei-c.org/ns/1.0";

            Element tei = document.createElementNS(namespace, "TEI");
            document.appendChild(tei);
            appendTeiHeader(document, tei, pages.size() == 1 ? pages.get(0).page().getName() : project.getName());

            Element text = document.createElementNS(namespace, "text");
            tei.appendChild(text);
            Element body = document.createElementNS(namespace, "body");
            text.appendChild(body);

            for (int i = 0; i < pages.size(); i++) {
                PreparedPageExport page = pages.get(i);
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

                for (PreparedRegion region : page.regions()) {
                    if (!region.hasText()) {
                        continue;
                    }
                    Element ab = document.createElementNS(namespace, "ab");
                    ab.setAttributeNS(XMLConstants.XML_NS_URI, "xml:id", sanitizeXmlId("region-" + region.id()));

                    if (!region.lines().isEmpty()) {
                        boolean firstLine = true;
                        for (PreparedTextLine line : region.lines()) {
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

    private byte[] renderTeiLayoutWithPage2Tei(List<PreparedPageExport> pages) throws IOException {
        Path tempDir = Files.createTempDirectory("larex-page2tei-");
        try {
            Path xmlDir = Files.createDirectories(tempDir.resolve("xml"));
            Path metsPath = tempDir.resolve("mets.xml");

            List<Page2TeiPageRef> pageRefs = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                PreparedPageExport page = pages.get(i);
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

    private void writePdf(OutputStream outputStream,
                          Project project,
                          List<PreparedPageExport> pages,
                          DocumentExportDto.PdfProfile pdfProfile) throws IOException {
        DocumentExportDto.PdfProfile resolvedProfile = resolvePdfProfile(pdfProfile);
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadPdfFont(document);

            for (PreparedPageExport preparedPage : pages) {
                switch (resolvedProfile) {
                    case SEARCHABLE, PDFA_SEARCHABLE -> addPdfPage(document, font, preparedPage, true, true, RenderingMode.NEITHER);
                    case IMAGES_ONLY -> addPdfPage(document, font, preparedPage, true, false, null);
                    case TEXT_PAGES -> {
                        addPdfPage(document, font, preparedPage, true, false, null);
                        addPdfPage(document, font, preparedPage, false, true, RenderingMode.FILL);
                    }
                }
            }

            if (resolvedProfile == DocumentExportDto.PdfProfile.PDFA_SEARCHABLE) {
                applyPdfaMetadata(document, pages.size() == 1 ? pages.get(0).page().getName() : project.getName());
            }

            document.save(outputStream);
        }
    }

    private void addPdfPage(PDDocument document,
                            PDFont font,
                            PreparedPageExport preparedPage,
                            boolean drawImage,
                            boolean drawText,
                            RenderingMode renderingMode) throws IOException {
        PDRectangle pageSize = new PDRectangle(preparedPage.pageDto().imageWidth(), preparedPage.pageDto().imageHeight());
        PDPage pdfPage = new PDPage(pageSize);
        document.addPage(pdfPage);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, pdfPage)) {
            if (drawImage) {
                BufferedImage image = readImage(preparedPage.imagePath());
                if (image != null) {
                    var pdImage = LosslessFactory.createFromImage(document, image);
                    contentStream.drawImage(pdImage, 0, 0, pageSize.getWidth(), pageSize.getHeight());
                }
            }

            if (!drawText || renderingMode == null) {
                return;
            }

            contentStream.setRenderingMode(renderingMode);
            contentStream.setFont(font, 1);

            for (PreparedTextLine line : collectPdfLines(preparedPage)) {
                renderTextLine(contentStream, font, line, preparedPage.pageDto());
            }
        }
    }

    private void applyPdfaMetadata(PDDocument document, String title) throws IOException {
        document.setVersion(1.7f);
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle(title);
        info.setProducer("LAREX");
        info.setCreator("LAREX");

        PDDocumentCatalog catalog = document.getDocumentCatalog();
        catalog.setLanguage("en-US");

        try (InputStream iccStream = DocumentExportService.class.getResourceAsStream(PDF_A_ICC_RESOURCE_PATH)) {
            if (iccStream == null) {
                throw new IOException("Bundled PDF/A ICC profile not found: " + PDF_A_ICC_RESOURCE_PATH);
            }
            PDOutputIntent outputIntent = new PDOutputIntent(document, iccStream);
            outputIntent.setInfo("sRGB IEC61966-2.1");
            outputIntent.setOutputCondition("sRGB IEC61966-2.1");
            outputIntent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
            outputIntent.setRegistryName("http://www.color.org");
            catalog.addOutputIntent(outputIntent);
        }

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String xmp = """
                <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title>
                        <rdf:Alt>
                          <rdf:li xml:lang="x-default">%s</rdf:li>
                        </rdf:Alt>
                      </dc:title>
                    </rdf:Description>
                    <rdf:Description rdf:about="" xmlns:pdf="http://ns.adobe.com/pdf/1.3/">
                      <pdf:Producer>LAREX</pdf:Producer>
                    </rdf:Description>
                    <rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                      <xmp:CreatorTool>LAREX</xmp:CreatorTool>
                      <xmp:CreateDate>%s</xmp:CreateDate>
                      <xmp:ModifyDate>%s</xmp:ModifyDate>
                    </rdf:Description>
                    <rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/">
                      <pdfaid:part>2</pdfaid:part>
                      <pdfaid:conformance>B</pdfaid:conformance>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>
                """.formatted(escapeXml(title), timestamp, timestamp);
        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
        catalog.setMetadata(metadata);
    }

    private StreamingDocumentExportResult renderSpreadsheetExportStream(Project project,
                                                                        List<PreparedPageExport> pages,
                                                                        ExportOptions options) throws IOException {
        List<DocumentExportDto.SpreadsheetProfile> profiles = resolveSpreadsheetProfiles(options.spreadsheetProfiles());
        String baseName = sanitizeFileName(project.getName(), "project");
        Map<String, byte[]> entries = new LinkedHashMap<>();

        for (DocumentExportDto.SpreadsheetProfile profile : profiles) {
            String profileSlug = profile.name().toLowerCase(Locale.ROOT);
            if (options.format() == DocumentExportDto.ExportFormat.CSV) {
                entries.put(baseName + "-" + profileSlug + ".csv", renderCsv(project, pages, profile));
            } else {
                entries.put(baseName + "-" + profileSlug + ".xlsx", renderXlsx(project, pages, profile));
            }
        }

        if (entries.size() == 1) {
            Map.Entry<String, byte[]> entry = entries.entrySet().iterator().next();
            return new StreamingDocumentExportResult(
                    entry.getKey(),
                    options.format().getContentType(),
                    outputStream -> outputStream.write(entry.getValue())
            );
        }

        String suffix = options.format() == DocumentExportDto.ExportFormat.CSV ? "-csv.zip" : "-xlsx.zip";
        return new StreamingDocumentExportResult(
                baseName + suffix,
                "application/zip",
                outputStream -> writeZipEntries(outputStream, entries)
        );
    }

    private byte[] renderCsv(Project project,
                             List<PreparedPageExport> pages,
                             DocumentExportDto.SpreadsheetProfile profile) {
        List<List<String>> rows = spreadsheetRows(project, pages, profile);
        StringBuilder builder = new StringBuilder();
        for (List<String> row : rows) {
            builder.append(row.stream().map(this::csvCell).reduce((left, right) -> left + "," + right).orElse(""));
            builder.append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] renderXlsx(Project project,
                              List<PreparedPageExport> pages,
                              DocumentExportDto.SpreadsheetProfile profile) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(profile.name());
            List<List<String>> rows = spreadsheetRows(project, pages, profile);

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                List<String> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                    row.createCell(columnIndex).setCellValue(values.get(columnIndex));
                }
            }

            for (int i = 0; i < rows.getFirst().size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private List<List<String>> spreadsheetRows(Project project,
                                               List<PreparedPageExport> pages,
                                               DocumentExportDto.SpreadsheetProfile profile) {
        return switch (profile) {
            case PAGE_METADATA -> pageMetadataRows(project, pages);
            case TAGS -> tagRows(project, pages);
            case REGIONS -> regionRows(project, pages);
        };
    }

    private List<List<String>> pageMetadataRows(Project project, List<PreparedPageExport> pages) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "workspaceId", "workspaceName", "projectId", "projectName", "pageId", "pageName", "pageDescription",
                "created", "updated", "locked", "imageCount", "xmlFileCount", "primaryImageFileName", "primaryXmlFileName", "defaultGtIndex"
        ));

        for (PreparedPageExport page : pages) {
            rows.add(List.of(
                    nullToEmpty(project.getLibrary().getWorkspaceId()),
                    nullToEmpty(project.getLibrary().getName()),
                    nullToEmpty(project.getId()),
                    nullToEmpty(project.getName()),
                    nullToEmpty(page.page().getId()),
                    nullToEmpty(page.page().getName()),
                    nullToEmpty(page.page().getDescription()),
                    timeValue(page.page().getCreated()),
                    timeValue(page.page().getUpdated()),
                    Boolean.toString(page.page().isLocked()),
                    Integer.toString(page.page().getImages() == null ? 0 : page.page().getImages().size()),
                    Integer.toString(page.page().getXmlFiles() == null ? 0 : page.page().getXmlFiles().size()),
                    page.image() == null ? "" : nullToEmpty(page.image().getFileName()),
                    page.pageXml() == null ? "" : nullToEmpty(page.pageXml().getFileName()),
                    Integer.toString(project.getEffectiveDefaultGtIndex())
            ));
        }
        return rows;
    }

    private List<List<String>> tagRows(Project project, List<PreparedPageExport> pages) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("scope", "workspaceId", "projectId", "projectName", "pageId", "pageName", "tag"));

        for (String tag : project.getTags() == null ? List.<String>of() : project.getTags()) {
            rows.add(List.of(
                    "PROJECT",
                    nullToEmpty(project.getLibrary().getWorkspaceId()),
                    nullToEmpty(project.getId()),
                    nullToEmpty(project.getName()),
                    "",
                    "",
                    nullToEmpty(tag)
            ));
        }

        for (PreparedPageExport page : pages) {
            for (String tag : page.page().getTags() == null ? List.<String>of() : page.page().getTags()) {
                rows.add(List.of(
                        "PAGE",
                        nullToEmpty(project.getLibrary().getWorkspaceId()),
                        nullToEmpty(project.getId()),
                        nullToEmpty(project.getName()),
                        nullToEmpty(page.page().getId()),
                        nullToEmpty(page.page().getName()),
                        nullToEmpty(tag)
                ));
            }
        }

        return rows;
    }

    private List<List<String>> regionRows(Project project, List<PreparedPageExport> pages) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "workspaceId", "projectId", "projectName", "pageId", "pageName", "regionId", "parentRegionId", "kind", "type",
                "readingOrderIndex", "text", "bboxX", "bboxY", "bboxWidth", "bboxHeight", "polygon", "rows", "columns", "labelIds", "custom"
        ));

        for (PreparedPageExport page : pages) {
            for (PreparedRegion region : page.regions()) {
                PolygonDto.BoundingBoxDto box = region.coords() == null ? null : region.coords().getBoundingBox();
                rows.add(List.of(
                        nullToEmpty(project.getLibrary().getWorkspaceId()),
                        nullToEmpty(project.getId()),
                        nullToEmpty(project.getName()),
                        nullToEmpty(page.page().getId()),
                        nullToEmpty(page.page().getName()),
                        nullToEmpty(region.id()),
                        nullToEmpty(region.parentRegionId()),
                        region.kind() == null ? "" : region.kind().name(),
                        nullToEmpty(region.type()),
                        region.readingOrderIndex() == null ? "" : Integer.toString(region.readingOrderIndex()),
                        nullToEmpty(region.text()),
                        box == null ? "" : doubleToString(box.x()),
                        box == null ? "" : doubleToString(box.y()),
                        box == null ? "" : doubleToString(box.width()),
                        box == null ? "" : doubleToString(box.height()),
                        polygonToString(region.coords()),
                        region.rows() == null ? "" : Integer.toString(region.rows()),
                        region.columns() == null ? "" : Integer.toString(region.columns()),
                        region.labelIds() == null ? "" : String.join("|", region.labelIds()),
                        nullToEmpty(region.custom())
                ));
            }
        }
        return rows;
    }

    private String renderPageText(PreparedPageExport page,
                                  DocumentExportDto.TextLevel textLevel,
                                  int textVariantIndex) {
        List<String> fragments = extractTextFragments(page.pageDto(), textLevel, textVariantIndex);
        if (fragments.isEmpty()) {
            return "";
        }
        String separator = textLevel == DocumentExportDto.TextLevel.TEXT_LINE ? "\n" : "\n\n";
        return String.join(separator, fragments);
    }

    private List<String> extractTextFragments(PageDto pageDto,
                                              DocumentExportDto.TextLevel textLevel,
                                              int textVariantIndex) {
        List<PreparedRegion> regions = extractRegions(pageDto, textVariantIndex).stream()
                .filter(PreparedRegion::hasText)
                .toList();
        if (regions.isEmpty()) {
            return List.of();
        }

        return switch (textLevel) {
            case PAGE -> List.of(regions.stream()
                    .map(PreparedRegion::text)
                    .filter(text -> text != null && !text.isBlank())
                    .reduce((left, right) -> left + "\n\n" + right)
                    .orElse(""));
            case REGION -> regions.stream()
                    .map(PreparedRegion::text)
                    .filter(text -> text != null && !text.isBlank())
                    .toList();
            case TEXT_LINE -> regions.stream()
                    .flatMap(region -> region.lines().isEmpty()
                            ? java.util.stream.Stream.of(region.text())
                            : region.lines().stream().map(PreparedTextLine::text))
                    .filter(text -> text != null && !text.isBlank())
                    .toList();
        };
    }

    private List<PreparedRegion> extractRegions(PageDto pageDto, int gtIndex) {
        Map<String, RegionDto> regionById = new LinkedHashMap<>();
        Map<String, String> parentByRegionId = new HashMap<>();
        List<RegionDto> structuralOrder = new ArrayList<>();
        collectRegions(pageDto.regions(), null, regionById, parentByRegionId, structuralOrder);

        List<String> readingOrderIds = new ArrayList<>();
        flattenReadingOrder(pageDto.readingOrder(), readingOrderIds);

        List<PreparedRegion> regions = new ArrayList<>();
        Set<String> visitedRegionIds = new HashSet<>();
        int readingOrderIndex = 0;

        for (String regionId : readingOrderIds) {
            RegionDto region = regionById.get(regionId);
            PreparedRegion preparedRegion = toPreparedRegion(region, parentByRegionId.get(regionId), gtIndex, readingOrderIndex);
            if (preparedRegion != null && visitedRegionIds.add(preparedRegion.id())) {
                regions.add(preparedRegion);
                readingOrderIndex++;
            }
        }

        for (RegionDto region : structuralOrder) {
            PreparedRegion preparedRegion = toPreparedRegion(region, parentByRegionId.get(region.id()), gtIndex, readingOrderIndex);
            if (preparedRegion != null && visitedRegionIds.add(preparedRegion.id())) {
                regions.add(preparedRegion);
                readingOrderIndex++;
            }
        }

        return regions;
    }

    private void collectRegions(List<RegionDto> regions,
                                String parentRegionId,
                                Map<String, RegionDto> regionById,
                                Map<String, String> parentByRegionId,
                                List<RegionDto> structuralOrder) {
        if (regions == null) {
            return;
        }
        for (RegionDto region : regions) {
            if (region == null || region.id() == null) {
                continue;
            }
            regionById.put(region.id(), region);
            parentByRegionId.put(region.id(), parentRegionId);
            structuralOrder.add(region);
            collectRegions(region.nestedRegions(), region.id(), regionById, parentByRegionId, structuralOrder);
        }
    }

    private void flattenReadingOrder(ReadingOrderDto readingOrder, List<String> orderedIds) {
        if (readingOrder == null || readingOrder.root() == null) {
            return;
        }
        flattenGroup(readingOrder.root(), orderedIds);
    }

    private void flattenGroup(ReadingOrderDto.GroupDto group, List<String> orderedIds) {
        if (group == null || group.members() == null) {
            return;
        }
        for (ReadingOrderDto.GroupMemberDto member : group.members()) {
            if (member instanceof ReadingOrderDto.RegionRefDto regionRef && regionRef.regionRef() != null) {
                orderedIds.add(regionRef.regionRef());
            } else if (member instanceof ReadingOrderDto.NestedGroupDto nestedGroup) {
                flattenGroup(nestedGroup.group(), orderedIds);
            }
        }
    }

    private PreparedRegion toPreparedRegion(RegionDto region,
                                            String parentRegionId,
                                            int gtIndex,
                                            int readingOrderIndex) {
        if (region == null || region.id() == null) {
            return null;
        }

        List<PreparedTextLine> lines = extractLines(region.textLines(), gtIndex);
        String text = !lines.isEmpty()
                ? String.join("\n", lines.stream().map(PreparedTextLine::text).filter(Objects::nonNull).toList())
                : resolveVariant(region.textContentVariants(), gtIndex).text();

        return new PreparedRegion(
                region.id(),
                parentRegionId,
                region.kind(),
                region.type(),
                readingOrderIndex,
                region.coords(),
                region.rows(),
                region.columns(),
                region.labelIds(),
                region.custom(),
                text,
                lines
        );
    }

    private List<PreparedTextLine> extractLines(List<TextLineDto> textLines, int gtIndex) {
        if (textLines == null || textLines.isEmpty()) {
            return List.of();
        }

        List<TextLineDto> sortedTextLines = textLines.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((TextLineDto line) -> line.index() == null ? Integer.MAX_VALUE : line.index())
                        .thenComparing(line -> line.id() == null ? "" : line.id()))
                .toList();

        List<PreparedTextLine> lines = new ArrayList<>();
        for (TextLineDto line : sortedTextLines) {
            VariantSelection variant = resolveVariant(line.textContentVariants(), gtIndex);
            String text = variant.text();
            if ((text == null || text.isBlank()) && line.getText() != null && !line.getText().isBlank()) {
                text = line.getText();
            }
            List<PreparedWord> words = extractWords(line.words(), gtIndex);
            if ((text == null || text.isBlank()) && !words.isEmpty()) {
                text = words.stream().map(PreparedWord::text).filter(Objects::nonNull).reduce((left, right) -> left + " " + right).orElse(null);
            }
            lines.add(new PreparedTextLine(
                    line.id(),
                    text,
                    line.coords(),
                    line.baseline(),
                    line.confidence(),
                    variant.confidence(),
                    words
            ));
        }
        return lines;
    }

    private List<PreparedWord> extractWords(List<WordDto> words, int gtIndex) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }

        List<PreparedWord> preparedWords = new ArrayList<>();
        for (WordDto word : words) {
            if (word == null) {
                continue;
            }
            VariantSelection variant = resolveVariant(word.textContentVariants(), gtIndex);
            String text = variant.text();
            if ((text == null || text.isBlank()) && word.getText() != null && !word.getText().isBlank()) {
                text = word.getText();
            }
            preparedWords.add(new PreparedWord(word.id(), text, word.coords(), word.confidence(), variant.confidence()));
        }
        return preparedWords;
    }

    private VariantSelection resolveVariant(List<TextContentVariantDto> variants, int gtIndex) {
        if (variants == null || variants.isEmpty()) {
            return VariantSelection.EMPTY;
        }

        for (TextContentVariantDto variant : variants) {
            if (variant != null && Objects.equals(variant.index(), gtIndex) && hasText(variant.unicode())) {
                return new VariantSelection(variant.unicode(), variant.confidence());
            }
        }

        if (gtIndex != 0) {
            for (TextContentVariantDto variant : variants) {
                if (variant != null && Objects.equals(variant.index(), 0) && hasText(variant.unicode())) {
                    return new VariantSelection(variant.unicode(), variant.confidence());
                }
            }
        }

        return variants.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((TextContentVariantDto variant) -> variant.index() == null ? Integer.MAX_VALUE : variant.index()))
                .filter(variant -> hasText(variant.unicode()))
                .findFirst()
                .map(variant -> new VariantSelection(variant.unicode(), variant.confidence()))
                .orElse(VariantSelection.EMPTY);
    }

    private List<PreparedTextLine> collectPdfLines(PreparedPageExport page) {
        List<PreparedTextLine> lines = new ArrayList<>();
        for (PreparedRegion region : page.regions()) {
            if (!region.lines().isEmpty()) {
                lines.addAll(region.lines().stream().filter(PreparedTextLine::hasText).toList());
                continue;
            }
            if (region.hasText()) {
                lines.add(new PreparedTextLine(
                        region.id(),
                        region.text(),
                        region.coords(),
                        null,
                        null,
                        null,
                        List.of()
                ));
            }
        }
        return lines;
    }

    private void renderTextLine(PDPageContentStream contentStream,
                                PDFont font,
                                PreparedTextLine line,
                                PageDto pageDto) throws IOException {
        String pdfText = sanitizePdfText(line.text());
        if (pdfText == null || pdfText.isBlank()) {
            return;
        }

        LinePlacement placement = computePlacement(line, pageDto);
        if (placement == null) {
            return;
        }

        float textWidthUnits = font.getStringWidth(pdfText) / 1000f;
        if (textWidthUnits <= 0.001f) {
            return;
        }

        float horizontalScale = placement.length() / textWidthUnits;
        float verticalScale = placement.fontSize();
        float cos = (float) Math.cos(placement.angleRadians());
        float sin = (float) Math.sin(placement.angleRadians());

        Matrix matrix = new Matrix(
                horizontalScale * cos,
                horizontalScale * sin,
                -verticalScale * sin,
                verticalScale * cos,
                placement.startX(),
                placement.startY()
        );

        contentStream.beginText();
        contentStream.setTextMatrix(matrix);
        contentStream.showText(pdfText);
        contentStream.endText();
    }

    private LinePlacement computePlacement(PreparedTextLine line, PageDto pageDto) {
        PolygonDto baseline = line.baseline();
        if (baseline != null && baseline.points() != null && baseline.points().size() >= 2) {
            PointDto start = baseline.points().getFirst();
            PointDto end = baseline.points().getLast();
            float startX = CoordinateUtils.worldToPixelX(start.x(), pageDto.imageWidth());
            float startY = pageDto.imageHeight() - CoordinateUtils.worldToPixelY(start.y(), pageDto.imageHeight());
            float endX = CoordinateUtils.worldToPixelX(end.x(), pageDto.imageWidth());
            float endY = pageDto.imageHeight() - CoordinateUtils.worldToPixelY(end.y(), pageDto.imageHeight());

            PolygonDto.BoundingBoxDto box = line.coords() == null ? null : line.coords().getBoundingBox();
            float boxHeight = box == null
                    ? 12f
                    : Math.max(8f, Math.abs(CoordinateUtils.worldToPixelY(box.y(), pageDto.imageHeight())
                    - CoordinateUtils.worldToPixelY(box.y() + box.height(), pageDto.imageHeight())) * 0.8f);

            return new LinePlacement(
                    startX,
                    startY,
                    (float) Math.atan2(endY - startY, endX - startX),
                    (float) Math.max(1d, Math.hypot(endX - startX, endY - startY)),
                    boxHeight
            );
        }

        PolygonDto coords = line.coords();
        if (coords == null || coords.points() == null || coords.points().isEmpty()) {
            return null;
        }

        PolygonDto.BoundingBoxDto box = coords.getBoundingBox();
        float minX = CoordinateUtils.worldToPixelX(box.x(), pageDto.imageWidth());
        float maxX = CoordinateUtils.worldToPixelX(box.x() + box.width(), pageDto.imageWidth());
        float topY = CoordinateUtils.worldToPixelY(box.y() + box.height(), pageDto.imageHeight());
        float bottomY = CoordinateUtils.worldToPixelY(box.y(), pageDto.imageHeight());
        float fontSize = Math.max(8f, Math.abs(bottomY - topY) * 0.8f);

        return new LinePlacement(
                minX,
                pageDto.imageHeight() - bottomY + (fontSize * 0.1f),
                0f,
                Math.max(1f, maxX - minX),
                fontSize
        );
    }

    private BufferedImage readImage(Path imagePath) throws IOException {
        if (imagePath == null || !Files.exists(imagePath)) {
            return null;
        }
        return ImageIO.read(imagePath.toFile());
    }

    private PageXml resolvePrimaryXml(Page page) {
        PageXml pageXml = resolvePrimaryPageXml(page);
        if (pageXml != null) {
            return pageXml;
        }

        return page.getXmlFiles().stream()
                .sorted(PAGE_XML_COMPARATOR)
                .findFirst()
                .orElse(null);
    }

    private PageXml resolvePrimaryPageXml(Page page) {
        return page.getXmlFiles().stream()
                .filter(xml -> xml.getSchema() == XmlSchema.PAGE_XML)
                .sorted(PAGE_XML_COMPARATOR)
                .findFirst()
                .orElse(null);
    }

    private PageImage resolvePrimaryImage(Page page) {
        return page.getImages().stream()
                .sorted(PAGE_IMAGE_COMPARATOR)
                .findFirst()
                .orElse(null);
    }

    private Path resolveUploadPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return Paths.get(uploadDir).resolve(relativePath);
    }

    private String sanitizeFileName(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String sanitized = value.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private String sanitizeXmlId(String value) {
        String sanitized = value == null ? "id" : value.trim().replaceAll("[^A-Za-z0-9_.-]+", "-");
        return sanitized.isBlank() ? "id" : sanitized.toLowerCase(Locale.ROOT);
    }

    private String zoneId(String prefix, String id) {
        return sanitizeXmlId(prefix + "-" + id);
    }

    private PDType0Font loadPdfFont(PDDocument document) throws IOException {
        try (InputStream inputStream = DocumentExportService.class.getResourceAsStream(PDF_FONT_RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new IOException("Bundled PDF font not found: " + PDF_FONT_RESOURCE_PATH);
            }
            return PDType0Font.load(document, inputStream, true);
        }
    }

    private String sanitizePdfText(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\R+", " ").trim();
    }

    private DocumentExportDto.TextLevel resolveTextLevel(DocumentExportDto.TextLevel requestedTextLevel) {
        return requestedTextLevel == null ? DocumentExportDto.TextLevel.PAGE : requestedTextLevel;
    }

    private int resolveTextVariantIndex(Integer requestedTextVariantIndex) {
        return requestedTextVariantIndex == null ? 0 : Math.max(0, requestedTextVariantIndex);
    }

    private DocumentExportDto.PdfProfile resolvePdfProfile(DocumentExportDto.PdfProfile pdfProfile) {
        return pdfProfile == null ? DocumentExportDto.PdfProfile.SEARCHABLE : pdfProfile;
    }

    private DocumentExportDto.TeiProfile resolveTeiProfile(DocumentExportDto.TeiProfile teiProfile) {
        return teiProfile == null ? DocumentExportDto.TeiProfile.STANDARD : teiProfile;
    }

    private List<DocumentExportDto.SpreadsheetProfile> resolveSpreadsheetProfiles(List<DocumentExportDto.SpreadsheetProfile> spreadsheetProfiles) {
        if (spreadsheetProfiles == null || spreadsheetProfiles.isEmpty()) {
            return List.of(DocumentExportDto.SpreadsheetProfile.PAGE_METADATA);
        }
        return spreadsheetProfiles.stream().filter(Objects::nonNull).distinct().toList();
    }

    private ResolvedDocxOptions resolveDocxOptions(DocumentExportDto.DocxOptions options, boolean pageScope) {
        return new ResolvedDocxOptions(
                options == null || options.preserveLineBreaks() == null || options.preserveLineBreaks(),
                !pageScope && (options == null || options.forcePageBreaks() == null || options.forcePageBreaks()),
                options != null && Boolean.TRUE.equals(options.includeImageNames()),
                options != null && Boolean.TRUE.equals(options.markUnclearWords())
        );
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
        URL xslUrl = DocumentExportService.class.getResource(PAGE2TEI_XSLT_RESOURCE_PATH);
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

    private byte[] zipEntries(Map<String, byte[]> entries) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue());
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private void writeZipEntries(OutputStream outputStream, Map<String, byte[]> entries) throws IOException {
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
            try {
                zipOutputStream.write(entry.getValue());
            } finally {
                zipOutputStream.closeEntry();
            }
        }
        zipOutputStream.finish();
    }

    private String fileExtension(String fileNameOrPath) {
        if (fileNameOrPath == null || fileNameOrPath.isBlank()) {
            return ".tmp";
        }
        String normalized = fileNameOrPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return ".tmp";
        }
        return name.substring(dot);
    }

    private String csvCell(String value) {
        String normalized = value == null ? "" : value;
        boolean needsQuotes = normalized.contains(",")
                || normalized.contains("\"")
                || normalized.contains("\n")
                || normalized.contains("\r");
        String escaped = normalized.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    private String polygonToString(PolygonDto polygon) {
        if (polygon == null || polygon.points() == null || polygon.points().isEmpty()) {
            return "";
        }
        return polygon.points().stream()
                .map(point -> doubleToString(point.x()) + "," + doubleToString(point.y()))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private String doubleToString(Double value) {
        if (value == null) {
            return "";
        }
        return value % 1d == 0d ? Long.toString(value.longValue()) : Double.toString(value);
    }

    private String timeValue(java.time.LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
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

    public record DocumentExportResult(
            String fileName,
            String contentType,
            byte[] bytes
    ) {
    }

    public record StreamingDocumentExportResult(
            String fileName,
            String contentType,
            ExportWriter writer
    ) {
    }

    @FunctionalInterface
    public interface ExportWriter {
        void write(OutputStream outputStream) throws IOException;
    }

    public record EmbeddedProjectOutput(
            String archivePath,
            Path absolutePath,
            long contentLength
    ) {
    }

    private record ExportOptions(
            DocumentExportDto.ExportFormat format,
            boolean includePageDelimiters,
            DocumentExportDto.TextLevel textLevel,
            int textVariantIndex,
            DocumentExportDto.PdfProfile pdfProfile,
            DocumentExportDto.TeiProfile teiProfile,
            List<DocumentExportDto.SpreadsheetProfile> spreadsheetProfiles,
            ResolvedDocxOptions docxOptions
    ) {
        private static ExportOptions fromPageRequest(DocumentExportDto.PageExportRequest request) {
            return new ExportOptions(
                    request.format(),
                    Boolean.TRUE.equals(request.includePageDelimiters()),
                    request.textLevel() == null ? DocumentExportDto.TextLevel.PAGE : request.textLevel(),
                    request.textVariantIndex() == null ? 0 : Math.max(0, request.textVariantIndex()),
                    request.pdfProfile(),
                    request.teiProfile(),
                    request.spreadsheetProfiles(),
                    ResolvedDocxOptions.from(request.docxOptions(), true)
            );
        }

        private static ExportOptions fromProjectRequest(DocumentExportDto.ProjectExportRequest request) {
            return new ExportOptions(
                    request.format(),
                    Boolean.TRUE.equals(request.includePageDelimiters()),
                    request.textLevel() == null ? DocumentExportDto.TextLevel.PAGE : request.textLevel(),
                    request.textVariantIndex() == null ? 0 : Math.max(0, request.textVariantIndex()),
                    request.pdfProfile(),
                    request.teiProfile(),
                    request.spreadsheetProfiles(),
                    ResolvedDocxOptions.from(request.docxOptions(), false)
            );
        }

        private static ExportOptions fromEmbeddedRequest(DocumentExportDto.EmbeddedProjectOutputRequest request) {
            return new ExportOptions(
                    request.format(),
                    Boolean.TRUE.equals(request.includePageDelimiters()),
                    request.textLevel() == null ? DocumentExportDto.TextLevel.PAGE : request.textLevel(),
                    request.textVariantIndex() == null ? 0 : Math.max(0, request.textVariantIndex()),
                    request.pdfProfile(),
                    request.teiProfile(),
                    request.spreadsheetProfiles(),
                    ResolvedDocxOptions.from(request.docxOptions(), false)
            );
        }
    }

    private record ResolvedDocxOptions(
            boolean preserveLineBreaks,
            boolean forcePageBreaks,
            boolean includeImageNames,
            boolean markUnclearWords
    ) {
        private static ResolvedDocxOptions from(DocumentExportDto.DocxOptions options, boolean pageScope) {
            return new ResolvedDocxOptions(
                    options == null || options.preserveLineBreaks() == null || options.preserveLineBreaks(),
                    !pageScope && (options == null || options.forcePageBreaks() == null || options.forcePageBreaks()),
                    options != null && Boolean.TRUE.equals(options.includeImageNames()),
                    options != null && Boolean.TRUE.equals(options.markUnclearWords())
            );
        }
    }

    private record PreparedPageExport(
            Page page,
            PageXml pageXml,
            PageDto pageDto,
            List<PreparedRegion> regions,
            PageImage image,
            Path imagePath
    ) {
    }

    private record PreparedRegion(
            String id,
            String parentRegionId,
            RegionKind kind,
            String type,
            Integer readingOrderIndex,
            PolygonDto coords,
            Integer rows,
            Integer columns,
            List<String> labelIds,
            String custom,
            String text,
            List<PreparedTextLine> lines
    ) {
        private boolean hasText() {
            return hasText(text) || lines.stream().anyMatch(PreparedTextLine::hasText);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    private record PreparedTextLine(
            String id,
            String text,
            PolygonDto coords,
            PolygonDto baseline,
            Double confidence,
            Double variantConfidence,
            List<PreparedWord> words
    ) {
        private boolean hasText() {
            return text != null && !text.isBlank();
        }
    }

    private record PreparedWord(
            String id,
            String text,
            PolygonDto coords,
            Double confidence,
            Double variantConfidence
    ) {
        private boolean hasText() {
            return text != null && !text.isBlank();
        }
    }

    private record VariantSelection(
            String text,
            Double confidence
    ) {
        private static final VariantSelection EMPTY = new VariantSelection(null, null);
    }

    private record Page2TeiPageRef(
            int sequence,
            String href
    ) {
    }

    private record LinePlacement(
            float startX,
            float startY,
            float angleRadians,
            float length,
            float fontSize
    ) {
    }
}
