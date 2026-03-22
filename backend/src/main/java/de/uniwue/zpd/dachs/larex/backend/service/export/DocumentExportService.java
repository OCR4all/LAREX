package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PointDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.ReadingOrderDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Service
@Transactional(readOnly = true)
public class DocumentExportService {

    private static final String PDF_FONT_RESOURCE_PATH = "/fonts/Junicode.ttf";
    private static final Comparator<Page> PAGE_NAME_COMPARATOR =
            Comparator.comparing(Page::getName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Page::getId);
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

    @Value("${file.upload-dir}")
    private String uploadDir;

    public DocumentExportService(ProjectRepository projectRepository,
                                 WorkspaceAccessService workspaceAccessService,
                                 AnnotationProcessingService annotationProcessingService,
                                 PageXmlConversionService pageXmlConversionService) {
        this.projectRepository = projectRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.annotationProcessingService = annotationProcessingService;
        this.pageXmlConversionService = pageXmlConversionService;
    }

    public DocumentExportResult exportPage(String projectId,
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
            return exportPageXml(page, request == null ? null : request.targetPageXmlVersion());
        }

        List<PreparedPageExport> preparedPages = preparePages(project, List.of(page));
        return renderDirectExport(
                project,
                preparedPages,
                format,
                Boolean.TRUE.equals(request.includePageDelimiters()),
                request == null ? null : request.textLevel(),
                request == null ? null : request.textVariantIndex()
        );
    }

    public DocumentExportResult exportProject(String workspaceId,
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
        return renderDirectExport(
                project,
                preparedPages,
                format,
                Boolean.TRUE.equals(request.includePageDelimiters()),
                request == null ? null : request.textLevel(),
                request == null ? null : request.textVariantIndex()
        );
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

            boolean includePageDelimiters = Boolean.TRUE.equals(request.includePageDelimiters());
            DocumentExportResult export = renderDirectExport(
                    project,
                    preparedPages,
                    request.format(),
                    includePageDelimiters,
                    request.textLevel(),
                    request.textVariantIndex()
            );
            String archivePath = "exports/" + sanitizeFileName(project.getName(), "project") + "." + request.format().getFileExtension();
            outputsByPath.putIfAbsent(archivePath, new EmbeddedProjectOutput(archivePath, export.bytes()));
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
                .sorted(PAGE_NAME_COMPARATOR)
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
                    pageDto,
                    extractTextBlocks(pageDto, gtIndex),
                    primaryImage,
                    imagePath
            ));
        }

        return preparedPages;
    }

    private DocumentExportResult renderDirectExport(Project project,
                                                    List<PreparedPageExport> pages,
                                                    DocumentExportDto.ExportFormat format,
                                                    boolean includePageDelimiters,
                                                    DocumentExportDto.TextLevel textLevel,
                                                    Integer textVariantIndex) throws IOException {
        byte[] bytes = switch (format) {
            case TXT -> renderText(pages, includePageDelimiters, textLevel, textVariantIndex);
            case DOCX -> renderDocx(project, pages);
            case TEI -> renderTei(project, pages);
            case PDF -> renderPdf(pages);
            case PAGE_XML, ALTO_XML -> throw new IllegalArgumentException("Unsupported direct export format: " + format);
        };

        String baseName = pages.size() == 1
                ? sanitizeFileName(pages.get(0).page().getName(), "page")
                : sanitizeFileName(project.getName(), "project");
        String fileName = baseName + "." + format.getFileExtension();
        return new DocumentExportResult(fileName, format.getContentType(), bytes);
    }

    private byte[] renderText(List<PreparedPageExport> pages,
                              boolean includePageDelimiters,
                              DocumentExportDto.TextLevel requestedTextLevel,
                              Integer requestedTextVariantIndex) {
        StringBuilder builder = new StringBuilder();
        DocumentExportDto.TextLevel textLevel = resolveTextLevel(requestedTextLevel);
        int textVariantIndex = resolveTextVariantIndex(requestedTextVariantIndex);

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
            builder.append(renderPageText(page, textLevel, textVariantIndex));
        }

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] renderDocx(Project project, List<PreparedPageExport> pages) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
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

                for (PreparedTextBlock block : page.blocks()) {
                    XWPFParagraph paragraph = document.createParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setText(block.text());
                }

                if (i < pages.size() - 1) {
                    XWPFParagraph breakParagraph = document.createParagraph();
                    XWPFRun breakRun = breakParagraph.createRun();
                    breakRun.addBreak(BreakType.PAGE);
                }
            }

            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] renderTei(Project project, List<PreparedPageExport> pages) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();

            Element tei = document.createElementNS("http://www.tei-c.org/ns/1.0", "TEI");
            document.appendChild(tei);

            Element teiHeader = document.createElementNS(tei.getNamespaceURI(), "teiHeader");
            tei.appendChild(teiHeader);

            Element fileDesc = document.createElementNS(tei.getNamespaceURI(), "fileDesc");
            teiHeader.appendChild(fileDesc);

            Element titleStmt = document.createElementNS(tei.getNamespaceURI(), "titleStmt");
            fileDesc.appendChild(titleStmt);
            Element title = document.createElementNS(tei.getNamespaceURI(), "title");
            title.setTextContent(pages.size() == 1 ? pages.get(0).page().getName() : project.getName());
            titleStmt.appendChild(title);

            Element publicationStmt = document.createElementNS(tei.getNamespaceURI(), "publicationStmt");
            fileDesc.appendChild(publicationStmt);
            Element publisher = document.createElementNS(tei.getNamespaceURI(), "p");
            publisher.setTextContent("Generated by LAREX");
            publicationStmt.appendChild(publisher);

            Element sourceDesc = document.createElementNS(tei.getNamespaceURI(), "sourceDesc");
            fileDesc.appendChild(sourceDesc);
            Element source = document.createElementNS(tei.getNamespaceURI(), "p");
            source.setTextContent("Derived from PAGE XML annotations.");
            sourceDesc.appendChild(source);

            Element text = document.createElementNS(tei.getNamespaceURI(), "text");
            tei.appendChild(text);
            Element body = document.createElementNS(tei.getNamespaceURI(), "body");
            text.appendChild(body);

            for (int i = 0; i < pages.size(); i++) {
                PreparedPageExport page = pages.get(i);
                Element div = document.createElementNS(tei.getNamespaceURI(), "div");
                div.setAttribute("type", "page");
                div.setAttributeNS(XMLConstants.XML_NS_URI, "xml:id", sanitizeXmlId("page-" + page.page().getId()));
                body.appendChild(div);

                Element head = document.createElementNS(tei.getNamespaceURI(), "head");
                head.setTextContent(page.page().getName());
                div.appendChild(head);

                if (pages.size() > 1) {
                    Element pb = document.createElementNS(tei.getNamespaceURI(), "pb");
                    pb.setAttribute("n", Integer.toString(i + 1));
                    if (page.pageDto().imageFilename() != null && !page.pageDto().imageFilename().isBlank()) {
                        pb.setAttribute("facs", page.pageDto().imageFilename());
                    }
                    div.appendChild(pb);
                }

                for (PreparedTextBlock block : page.blocks()) {
                    Element ab = document.createElementNS(tei.getNamespaceURI(), "ab");
                    ab.setAttributeNS(XMLConstants.XML_NS_URI, "xml:id", sanitizeXmlId("region-" + block.regionId()));
                    ab.setTextContent(block.text());
                    div.appendChild(ab);
                }
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.transform(new DOMSource(document), new StreamResult(outputStream));
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IOException("Failed to render TEI export", e);
        }
    }

    private byte[] renderPdf(List<PreparedPageExport> pages) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDFont font = loadPdfFont(document);

            for (PreparedPageExport preparedPage : pages) {
                PDRectangle pageSize = new PDRectangle(preparedPage.pageDto().imageWidth(), preparedPage.pageDto().imageHeight());
                PDPage pdfPage = new PDPage(pageSize);
                document.addPage(pdfPage);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, pdfPage)) {
                    BufferedImage image = readImage(preparedPage.imagePath());
                    if (image != null) {
                        var pdImage = LosslessFactory.createFromImage(document, image);
                        contentStream.drawImage(pdImage, 0, 0, pageSize.getWidth(), pageSize.getHeight());
                    }

                    contentStream.setRenderingMode(RenderingMode.NEITHER);
                    contentStream.setFont(font, 1);

                    for (PreparedTextLine line : collectPdfLines(preparedPage)) {
                        renderInvisibleTextLine(contentStream, font, line, preparedPage.pageDto());
                    }
                }
            }

            document.save(outputStream);
            try (PDDocument loaded = Loader.loadPDF(outputStream.toByteArray())) {
                loaded.getNumberOfPages();
            }
            return outputStream.toByteArray();
        }
    }

    private List<PreparedTextLine> collectPdfLines(PreparedPageExport page) {
        List<PreparedTextLine> lines = new ArrayList<>();
        for (PreparedTextBlock block : page.blocks()) {
            if (!block.lines().isEmpty()) {
                lines.addAll(block.lines());
                continue;
            }
            if (block.text() != null && !block.text().isBlank()) {
                lines.add(new PreparedTextLine(
                        block.regionId(),
                        block.text(),
                        block.coords(),
                        null
                ));
            }
        }
        return lines;
    }

    private void renderInvisibleTextLine(PDPageContentStream contentStream,
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
            PointDto start = baseline.points().get(0);
            PointDto end = baseline.points().get(baseline.points().size() - 1);
            float startX = CoordinateUtils.worldToPixelX(start.x(), pageDto.imageWidth());
            float startY = pageDto.imageHeight() - CoordinateUtils.worldToPixelY(start.y(), pageDto.imageHeight());
            float endX = CoordinateUtils.worldToPixelX(end.x(), pageDto.imageWidth());
            float endY = pageDto.imageHeight() - CoordinateUtils.worldToPixelY(end.y(), pageDto.imageHeight());

            PolygonDto.BoundingBoxDto box = (line.coords() == null ? null : line.coords().getBoundingBox());
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

    private String renderPageText(PreparedPageExport page,
                                  DocumentExportDto.TextLevel textLevel,
                                  int textVariantIndex) {
        List<String> fragments = extractTextFragments(page.pageDto(), textVariantIndex, textLevel);
        if (fragments.isEmpty()) {
            return "";
        }
        String separator = textLevel == DocumentExportDto.TextLevel.TEXT_LINE ? "\n" : "\n\n";
        return String.join(separator, fragments);
    }

    private List<String> extractTextFragments(PageDto pageDto,
                                              int gtIndex,
                                              DocumentExportDto.TextLevel textLevel) {
        List<PreparedTextBlock> blocks = extractTextBlocks(pageDto, gtIndex);
        if (blocks.isEmpty()) {
            return List.of();
        }

        return switch (textLevel) {
            case PAGE -> List.of(blocks.stream()
                    .map(PreparedTextBlock::text)
                    .filter(text -> text != null && !text.isBlank())
                    .reduce((left, right) -> left + "\n\n" + right)
                    .orElse(""));
            case REGION -> blocks.stream()
                    .map(PreparedTextBlock::text)
                    .filter(text -> text != null && !text.isBlank())
                    .toList();
            case TEXT_LINE -> blocks.stream()
                    .flatMap(block -> {
                        if (!block.lines().isEmpty()) {
                            return block.lines().stream().map(PreparedTextLine::text);
                        }
                        return java.util.stream.Stream.of(block.text());
                    })
                    .filter(text -> text != null && !text.isBlank())
                    .toList();
        };
    }

    private List<PreparedTextBlock> extractTextBlocks(PageDto pageDto, int gtIndex) {
        Map<String, RegionDto> regionById = new LinkedHashMap<>();
        collectRegions(pageDto.regions(), regionById);

        Set<String> orderedIds = new LinkedHashSet<>();
        flattenReadingOrder(pageDto.readingOrder(), orderedIds);

        List<PreparedTextBlock> blocks = new ArrayList<>();
        Set<String> visitedRegionIds = new HashSet<>();

        for (String regionId : orderedIds) {
            RegionDto region = regionById.get(regionId);
            PreparedTextBlock block = toTextBlock(region, gtIndex);
            if (block != null && visitedRegionIds.add(block.regionId())) {
                blocks.add(block);
            }
        }

        appendFallbackBlocks(pageDto.regions(), gtIndex, visitedRegionIds, blocks);
        return blocks;
    }

    private void collectRegions(List<RegionDto> regions, Map<String, RegionDto> regionById) {
        if (regions == null) {
            return;
        }
        for (RegionDto region : regions) {
            if (region == null || region.id() == null) {
                continue;
            }
            regionById.put(region.id(), region);
            collectRegions(region.nestedRegions(), regionById);
        }
    }

    private void flattenReadingOrder(ReadingOrderDto readingOrder, Set<String> orderedIds) {
        if (readingOrder == null || readingOrder.root() == null) {
            return;
        }
        flattenGroup(readingOrder.root(), orderedIds);
    }

    private void flattenGroup(ReadingOrderDto.GroupDto group, Set<String> orderedIds) {
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

    private void appendFallbackBlocks(List<RegionDto> regions,
                                      int gtIndex,
                                      Set<String> visitedRegionIds,
                                      List<PreparedTextBlock> blocks) {
        if (regions == null) {
            return;
        }
        for (RegionDto region : regions) {
            PreparedTextBlock block = toTextBlock(region, gtIndex);
            if (block != null && visitedRegionIds.add(block.regionId())) {
                blocks.add(block);
            }
            appendFallbackBlocks(region == null ? null : region.nestedRegions(), gtIndex, visitedRegionIds, blocks);
        }
    }

    private PreparedTextBlock toTextBlock(RegionDto region, int gtIndex) {
        if (region == null || region.id() == null) {
            return null;
        }

        List<PreparedTextLine> lines = extractLines(region.textLines(), gtIndex);
        String text;
        if (!lines.isEmpty()) {
            text = String.join("\n", lines.stream().map(PreparedTextLine::text).toList());
        } else {
            text = resolveVariantText(region.textContentVariants(), gtIndex);
        }

        if (text == null || text.isBlank()) {
            return null;
        }

        return new PreparedTextBlock(region.id(), text, region.coords(), lines);
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
            String text = resolveVariantText(line.textContentVariants(), gtIndex);
            if ((text == null || text.isBlank()) && line.getText() != null && !line.getText().isBlank()) {
                text = line.getText();
            }
            if (text == null || text.isBlank()) {
                continue;
            }
            lines.add(new PreparedTextLine(line.id(), text, line.coords(), line.baseline()));
        }
        return lines;
    }

    private String resolveVariantText(List<TextContentVariantDto> variants, int gtIndex) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }

        String exactMatch = variants.stream()
                .filter(Objects::nonNull)
                .filter(variant -> Objects.equals(variant.index(), gtIndex))
                .map(TextContentVariantDto::unicode)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);
        if (exactMatch != null) {
            return exactMatch;
        }

        if (gtIndex != 0) {
            String zeroMatch = variants.stream()
                    .filter(Objects::nonNull)
                    .filter(variant -> Objects.equals(variant.index(), 0))
                    .map(TextContentVariantDto::unicode)
                    .filter(text -> text != null && !text.isBlank())
                    .findFirst()
                    .orElse(null);
            if (zeroMatch != null) {
                return zeroMatch;
            }
        }

        return variants.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((TextContentVariantDto variant) -> variant.index() == null ? Integer.MAX_VALUE : variant.index()))
                .map(TextContentVariantDto::unicode)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);
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
        return requestedTextVariantIndex == null ? 0 : requestedTextVariantIndex;
    }

    private DocumentExportResult exportPageXml(Page page, String targetPageXmlVersion) throws IOException {
        PageXml pageXml = resolvePrimaryPageXml(page);
        if (pageXml == null) {
            throw new IllegalArgumentException("No PAGE XML file found for page: " + page.getId());
        }

        Path xmlPath = resolveUploadPath(pageXml.getFilePath());
        if (xmlPath == null || !Files.exists(xmlPath)) {
            throw new IllegalArgumentException("PAGE XML file not found for page: " + page.getId());
        }

        String normalizedTarget = pageXmlConversionService.normalizeTargetVersion(targetPageXmlVersion);
        byte[] bytes = pageXmlConversionService.convertFileToVersion(xmlPath, normalizedTarget);
        String fileName = sanitizeFileName(pageXml.getFileName(), sanitizeFileName(page.getName(), "page") + ".xml");
        String contentType = pageXml.getMimeType() == null || pageXml.getMimeType().isBlank()
                ? DocumentExportDto.ExportFormat.PAGE_XML.getContentType()
                : pageXml.getMimeType();
        return new DocumentExportResult(fileName, contentType, bytes);
    }

    public record DocumentExportResult(
            String fileName,
            String contentType,
            byte[] bytes
    ) {
    }

    public record EmbeddedProjectOutput(
            String archivePath,
            byte[] bytes
    ) {
    }

    private record PreparedPageExport(
            Page page,
            PageDto pageDto,
            List<PreparedTextBlock> blocks,
            PageImage image,
            Path imagePath
    ) {
    }

    private record PreparedTextBlock(
            String regionId,
            String text,
            PolygonDto coords,
            List<PreparedTextLine> lines
    ) {
    }

    private record PreparedTextLine(
            String id,
            String text,
            PolygonDto coords,
            PolygonDto baseline
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
