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
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExportServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private WorkspaceAccessService workspaceAccessService;
    @Mock
    private AnnotationProcessingService annotationProcessingService;
    @Mock
    private PageXmlConversionService pageXmlConversionService;
    @Mock
    private PageOrderService pageOrderService;

    private DocumentExportService service;

    @BeforeEach
    void setUp() {
        service = new DocumentExportService(
                projectRepository,
                workspaceAccessService,
                annotationProcessingService,
                pageXmlConversionService,
                pageOrderService,
                new TextDocumentExportWriter(
                        new PlainTextExportWriter(),
                        new DocxExportWriter(),
                        new TeiExportWriter(annotationProcessingService),
                        new PdfExportWriter()
                ),
                new AltoExportWriter(annotationProcessingService),
                new SpreadsheetExportWriter()
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        lenient().when(pageOrderService.projectOrderComparator())
                .thenReturn(Comparator.comparing(Page::getName, String.CASE_INSENSITIVE_ORDER));
    }

    @Test
    void exportProjectTxt_usesReadingOrderAndPageDelimiters() throws Exception {
        Project project = project("project-1", "Demo Project");
        Page alpha = page(project, "page-1", "Alpha", "alpha.xml", null);
        Page beta = page(project, "page-2", "beta", "beta.xml", null);
        project.setPages(new ArrayList<>(List.of(beta, alpha)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(pageDto(
                "alpha.png",
                List.of(
                        textRegion("r1", List.of(textLine("l1", "first", 0, simpleBaseline(10, 30, 120, 30)))),
                        textRegion("r2", List.of(textLine("l2", "second", 0, simpleBaseline(10, 60, 150, 60))))
                ),
                readingOrder("r2", "r1")
        ));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-2")).thenReturn(pageDto(
                "beta.png",
                List.of(textRegion("r3", List.of(textLine("l3", "beta", 0, simpleBaseline(10, 30, 120, 30))))),
                null
        ));

        DocumentExportService.DocumentExportResult result = service.exportProject(
                "ws-1",
                "project-1",
                "user-1",
                new DocumentExportDto.ProjectExportRequest(DocumentExportDto.ExportFormat.TXT, null, null, true, null, null, null, null, null, null)
        );

        String text = new String(result.bytes(), StandardCharsets.UTF_8);
        assertEquals("Demo Project.txt", result.fileName());
        assertTrue(text.contains("===== Page: Alpha ====="));
        assertTrue(text.indexOf("second") < text.indexOf("first"));
        assertTrue(text.indexOf("===== Page: Alpha =====") < text.indexOf("===== Page: beta ====="));
        verify(workspaceAccessService).requireWorkspaceAccess("ws-1", "user-1");
    }

    @Test
    void exportPageDocx_containsPageHeadingAndText() throws Exception {
        Project project = project("project-1", "Demo Project");
        Page page = page(project, "page-1", "Alpha", "alpha.xml", null);
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(pageDto(
                "alpha.png",
                List.of(textRegion("r1", List.of(textLine("l1", "hello world", 0, simpleBaseline(10, 30, 160, 30))))),
                null
        ));

        DocumentExportService.DocumentExportResult result = service.exportPage(
                "project-1",
                "page-1",
                "user-1",
                new DocumentExportDto.PageExportRequest(DocumentExportDto.ExportFormat.DOCX, null, null, null, null, null, null, null, null)
        );

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result.bytes()))) {
            String combinedText = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(combinedText.contains("Alpha"));
            assertTrue(combinedText.contains("hello world"));
        }
    }

    @Test
    void exportPageTei_containsTeiRootAndPageStructure() throws Exception {
        Project project = project("project-1", "Demo Project");
        Page page = page(project, "page-1", "Alpha", "alpha.xml", null);
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(pageDto(
                "alpha.png",
                List.of(textRegion("r1", List.of(textLine("l1", "tei text", 0, simpleBaseline(10, 30, 160, 30))))),
                null
        ));

        DocumentExportService.DocumentExportResult result = service.exportPage(
                "project-1",
                "page-1",
                "user-1",
                new DocumentExportDto.PageExportRequest(DocumentExportDto.ExportFormat.TEI, null, null, null, null, null, null, null, null)
        );

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(result.bytes()));

        assertEquals("TEI", document.getDocumentElement().getLocalName());
        assertEquals(1, document.getElementsByTagNameNS("http://www.tei-c.org/ns/1.0", "div").getLength());
        assertEquals(1, document.getElementsByTagNameNS("http://www.tei-c.org/ns/1.0", "ab").getLength());
    }

    @Test
    void exportPageTeiLayout_containsFacsimileAndZones() throws Exception {
        Project project = project("project-1", "Demo Project");
        Page page = page(project, "page-1", "Alpha", "alpha.xml", null);
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        PageDto dto = pageDto(
                "alpha.png",
                List.of(textRegion("r1", List.of(textLine("l1", "layout text", 0, simpleBaseline(10, 30, 160, 30))))),
                null
        );
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(dto);
        when(annotationProcessingService.exportAnnotationToXml(any(PageDto.class), eq(XmlSchema.PAGE_XML), eq("xml-page-1")))
                .thenReturn("""
                        <PcGts xmlns=\"http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15\">
                          <Page imageFilename=\"alpha.png\" imageWidth=\"400\" imageHeight=\"200\">
                            <TextRegion id=\"r1\">
                              <Coords points=\"10,20 300,20 300,100 10,100\"/>
                              <TextLine id=\"l1\">
                                <Coords points=\"10,20 280,20 280,40 10,40\"/>
                                <Baseline points=\"10,30 160,30\"/>
                                <TextEquiv><Unicode>layout text</Unicode></TextEquiv>
                              </TextLine>
                            </TextRegion>
                          </Page>
                        </PcGts>
                        """);

        DocumentExportService.DocumentExportResult result = service.exportPage(
                "project-1",
                "page-1",
                "user-1",
                new DocumentExportDto.PageExportRequest(DocumentExportDto.ExportFormat.TEI, null, null, null, null, null, DocumentExportDto.TeiProfile.LAYOUT, null, null)
        );

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(result.bytes()));

        assertEquals(1, document.getElementsByTagNameNS("http://www.tei-c.org/ns/1.0", "facsimile").getLength());
        assertEquals(2, document.getElementsByTagNameNS("http://www.tei-c.org/ns/1.0", "zone").getLength());
    }

    @Test
    void exportPageAlto_returnsAltoXml() throws Exception {
        Project project = project("project-1", "Demo Project");
        Page page = page(project, "page-1", "Alpha", "alpha.xml", null);
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        PageDto dto = pageDto(
                "alpha.png",
                List.of(textRegion("r1", List.of(textLine("l1", "alto text", 0, simpleBaseline(10, 30, 160, 30))))),
                null
        );
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(dto);
        when(annotationProcessingService.exportAnnotationToXml(any(PageDto.class), eq(XmlSchema.ALTO_XML), eq("xml-page-1")))
                .thenReturn("<alto xmlns=\"http://www.loc.gov/standards/alto/ns-v4#\"/>");

        DocumentExportService.DocumentExportResult result = service.exportPage(
                "project-1",
                "page-1",
                "user-1",
                new DocumentExportDto.PageExportRequest(DocumentExportDto.ExportFormat.ALTO_XML, null, null, null, null, null, null, null, null)
        );

        assertEquals("Alpha.alto.xml", result.fileName());
        assertTrue(new String(result.bytes(), StandardCharsets.UTF_8).contains("<alto"));
    }

    @Test
    void exportProjectAlto_streamsZipEntriesFromAltoExporter() throws Exception {
        Project project = project("project-1", "Demo Project");
        Page alpha = page(project, "page-1", "Alpha", "alpha.xml", null);
        Page beta = page(project, "page-2", "Beta", "beta.xml", null);
        project.setPages(new ArrayList<>(List.of(beta, alpha)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(pageDto(
                "alpha.png",
                List.of(textRegion("r1", List.of(textLine("l1", "alpha", 0, simpleBaseline(10, 30, 160, 30))))),
                null
        ));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-2")).thenReturn(pageDto(
                "beta.png",
                List.of(textRegion("r2", List.of(textLine("l2", "beta", 0, simpleBaseline(10, 30, 160, 30))))),
                null
        ));
        when(annotationProcessingService.exportAnnotationToXml(any(PageDto.class), eq(XmlSchema.ALTO_XML), eq("xml-page-1")))
                .thenReturn("<alto>alpha</alto>");
        when(annotationProcessingService.exportAnnotationToXml(any(PageDto.class), eq(XmlSchema.ALTO_XML), eq("xml-page-2")))
                .thenReturn("<alto>beta</alto>");

        DocumentExportService.DocumentExportResult result = service.exportProject(
                "ws-1",
                "project-1",
                "user-1",
                new DocumentExportDto.ProjectExportRequest(DocumentExportDto.ExportFormat.ALTO_XML, null, null, false, null, null, null, null, null, null)
        );

        Map<String, String> entries = zipEntries(result.bytes());
        assertEquals("Demo Project.alto.zip", result.fileName());
        assertEquals("<alto>alpha</alto>", entries.get("Alpha.alto.xml"));
        assertEquals("<alto>beta</alto>", entries.get("Beta.alto.xml"));
        verify(annotationProcessingService).exportAnnotationToXml(any(PageDto.class), eq(XmlSchema.ALTO_XML), eq("xml-page-1"));
        verify(annotationProcessingService).exportAnnotationToXml(any(PageDto.class), eq(XmlSchema.ALTO_XML), eq("xml-page-2"));
    }

    @Test
    void exportPagePdf_containsSelectableText() throws Exception {
        Path imagePath = tempDir.resolve("images/sample.png");
        Files.createDirectories(imagePath.getParent());
        BufferedImage bufferedImage = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < bufferedImage.getHeight(); y++) {
            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                bufferedImage.setRGB(x, y, Color.WHITE.getRGB());
            }
        }
        ImageIO.write(bufferedImage, "png", imagePath.toFile());

        Project project = project("project-1", "Demo Project");
        Page page = page(project, "page-1", "Alpha", "alpha.xml", "images/sample.png");
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(pageDto(
                "sample.png",
                List.of(textRegion("r1", List.of(textLine("l1", "searchable text", 0, simpleBaseline(30, 80, 240, 80))))),
                null
        ));

        DocumentExportService.DocumentExportResult result = service.exportPage(
                "project-1",
                "page-1",
                "user-1",
                new DocumentExportDto.PageExportRequest(DocumentExportDto.ExportFormat.PDF, null, null, null, null, null, null, null, null)
        );

        try (var pdf = Loader.loadPDF(result.bytes())) {
            String extracted = new PDFTextStripper().getText(pdf);
            assertTrue(extracted.contains("searchable text"));
        }
    }

    @Test
    void exportPagePdf_imagesOnlyContainsNoExtractableText() throws Exception {
        Path imagePath = tempDir.resolve("images/image-only.png");
        Files.createDirectories(imagePath.getParent());
        BufferedImage bufferedImage = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(bufferedImage, "png", imagePath.toFile());

        Project project = project("project-1", "Demo Project");
        Page page = page(project, "page-1", "Alpha", "alpha.xml", "images/image-only.png");
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(pageDto(
                "image-only.png",
                List.of(textRegion("r1", List.of(textLine("l1", "hidden text", 0, simpleBaseline(30, 80, 240, 80))))),
                null
        ));

        DocumentExportService.DocumentExportResult result = service.exportPage(
                "project-1",
                "page-1",
                "user-1",
                new DocumentExportDto.PageExportRequest(DocumentExportDto.ExportFormat.PDF, null, null, null, null, DocumentExportDto.PdfProfile.IMAGES_ONLY, null, null, null)
        );

        try (var pdf = Loader.loadPDF(result.bytes())) {
            String extracted = new PDFTextStripper().getText(pdf).trim();
            assertFalse(extracted.contains("hidden text"));
        }
    }

    @Test
    void exportPagePdf_pdfaAddsMetadataAndOutputIntent() throws Exception {
        Path imagePath = tempDir.resolve("images/pdfa.png");
        Files.createDirectories(imagePath.getParent());
        BufferedImage bufferedImage = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(bufferedImage, "png", imagePath.toFile());

        Project project = project("project-1", "Demo Project");
        Page page = page(project, "page-1", "Alpha", "alpha.xml", "images/pdfa.png");
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(pageDto(
                "pdfa.png",
                List.of(textRegion("r1", List.of(textLine("l1", "pdfa text", 0, simpleBaseline(30, 80, 240, 80))))),
                null
        ));

        DocumentExportService.DocumentExportResult result = service.exportPage(
                "project-1",
                "page-1",
                "user-1",
                new DocumentExportDto.PageExportRequest(DocumentExportDto.ExportFormat.PDF, null, null, null, null, DocumentExportDto.PdfProfile.PDFA_SEARCHABLE, null, null, null)
        );

        try (var pdf = Loader.loadPDF(result.bytes())) {
            assertTrue(pdf.getDocumentCatalog().getMetadata() != null);
            assertFalse(pdf.getDocumentCatalog().getOutputIntents().isEmpty());
        }
    }

    @Test
    void exportProjectTxt_usesRequestedTextLevelAndVariantIndex() throws Exception {
        Project project = project("project-1", "Demo Project");
        project.setDefaultGtIndex(7);
        Page page = page(project, "page-1", "Alpha", "alpha.xml", null);
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(pageDto(
                "alpha.png",
                List.of(textRegion("r1", List.of(
                        textLine("l1", "zero-one", 0, simpleBaseline(10, 30, 120, 30), "one-one", 1),
                        textLine("l2", "zero-two", 0, simpleBaseline(10, 60, 150, 60), "one-two", 1)
                ))),
                null
        ));

        DocumentExportService.DocumentExportResult result = service.exportProject(
                "ws-1",
                "project-1",
                "user-1",
                new DocumentExportDto.ProjectExportRequest(
                        DocumentExportDto.ExportFormat.TXT,
                        null,
                        null,
                        false,
                        DocumentExportDto.TextLevel.TEXT_LINE,
                        1,
                        null,
                        null,
                        null,
                        null
                )
        );

        assertEquals("one-one\none-two", new String(result.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void exportProjectCsv_includesMetadataHeader() throws Exception {
        Project project = project("project-1", "Demo Project");
        Page page = page(project, "page-1", "Alpha", "alpha.xml", null);
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(pageDto(
                "alpha.png",
                List.of(textRegion("r1", List.of(textLine("l1", "csv text", 0, simpleBaseline(10, 30, 120, 30))))),
                null
        ));

        DocumentExportService.DocumentExportResult result = service.exportProject(
                "ws-1",
                "project-1",
                "user-1",
                new DocumentExportDto.ProjectExportRequest(
                        DocumentExportDto.ExportFormat.CSV,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        List.of(DocumentExportDto.SpreadsheetProfile.PAGE_METADATA),
                        null
                )
        );

        String csv = new String(result.bytes(), StandardCharsets.UTF_8);
        assertEquals("Demo Project-page_metadata.csv", result.fileName());
        assertTrue(csv.contains("workspaceId,workspaceName,projectId,projectName,pageId,pageName"));
        assertTrue(csv.contains("project-1"));
    }

    @Test
    void exportProjectCsvWithMultipleProfiles_streamsZipEntries() throws Exception {
        Project project = project("project-1", "Demo Project");
        project.setTags(List.of("project-tag"));
        Page page = page(project, "page-1", "Alpha", "alpha.xml", null);
        page.setTags(List.of("page-tag"));
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(pageDto(
                "alpha.png",
                List.of(textRegion("r1", List.of(textLine("l1", "csv text", 0, simpleBaseline(10, 30, 120, 30))))),
                null
        ));

        DocumentExportService.DocumentExportResult result = service.exportProject(
                "ws-1",
                "project-1",
                "user-1",
                new DocumentExportDto.ProjectExportRequest(
                        DocumentExportDto.ExportFormat.CSV,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        List.of(DocumentExportDto.SpreadsheetProfile.PAGE_METADATA, DocumentExportDto.SpreadsheetProfile.TAGS),
                        null
                )
        );

        Map<String, String> entries = zipEntries(result.bytes());
        assertEquals("Demo Project-csv.zip", result.fileName());
        assertTrue(entries.get("Demo Project-page_metadata.csv").contains("workspaceId,workspaceName,projectId,projectName,pageId,pageName"));
        assertTrue(entries.get("Demo Project-tags.csv").contains("project-tag"));
        assertTrue(entries.get("Demo Project-tags.csv").contains("page-tag"));
    }

    @Test
    void exportPagePdf_supportsUnicodeText() throws Exception {
        Path imagePath = tempDir.resolve("images/unicode.png");
        Files.createDirectories(imagePath.getParent());
        BufferedImage bufferedImage = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < bufferedImage.getHeight(); y++) {
            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                bufferedImage.setRGB(x, y, Color.WHITE.getRGB());
            }
        }
        ImageIO.write(bufferedImage, "png", imagePath.toFile());

        String unicodeText = "latin \u1EFD text";
        Project project = project("project-1", "Demo Project");
        Page page = page(project, "page-1", "Alpha", "alpha.xml", "images/unicode.png");
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(annotationProcessingService.parseXmlToAnnotation("xml-page-1")).thenReturn(pageDto(
                "unicode.png",
                List.of(textRegion("r1", List.of(textLine("l1", unicodeText, 0, simpleBaseline(30, 80, 240, 80))))),
                null
        ));

        DocumentExportService.DocumentExportResult result = service.exportPage(
                "project-1",
                "page-1",
                "user-1",
                new DocumentExportDto.PageExportRequest(DocumentExportDto.ExportFormat.PDF, null, null, null, null, null, null, null, null)
        );

        try (var pdf = Loader.loadPDF(result.bytes())) {
            String extracted = new PDFTextStripper().getText(pdf);
            assertTrue(extracted.contains(unicodeText));
        }
    }

    @Test
    void exportPagePageXml_usesConversionService() throws Exception {
        Path xmlPath = tempDir.resolve("alpha.xml");
        Files.writeString(xmlPath, "<PcGts/>", StandardCharsets.UTF_8);

        Project project = project("project-1", "Demo Project");
        Page page = page(project, "page-1", "Alpha", "alpha.xml", null);
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(pageXmlConversionService.normalizeTargetVersion("2017-07-15")).thenReturn("2017-07-15");
        doAnswer(invocation -> {
            invocation.<java.io.OutputStream>getArgument(2).write("<converted/>".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(pageXmlConversionService).writeFileToVersion(eq(xmlPath), eq("2017-07-15"), any());

        DocumentExportService.DocumentExportResult result = service.exportPage(
                "project-1",
                "page-1",
                "user-1",
                new DocumentExportDto.PageExportRequest(DocumentExportDto.ExportFormat.PAGE_XML, "2017-07-15", null, null, null, null, null, null, null)
        );

        assertEquals("Alpha.xml", result.fileName());
        assertEquals("<converted/>", new String(result.bytes(), StandardCharsets.UTF_8));
    }

    private Project project(String id, String name) {
        Library library = new Library("ws-1", "Library");
        library.setId("lib-1");

        Project project = new Project(name, "desc", library);
        project.setId(id);
        project.setDefaultGtIndex(0);
        return project;
    }

    private Map<String, String> zipEntries(byte[] zipBytes) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            var entry = zipInputStream.getNextEntry();
            while (entry != null) {
                entries.put(entry.getName(), new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
        }
        return entries;
    }

    private Page page(Project project, String id, String name, String xmlRelativePath, String imageRelativePath) {
        Page page = new Page(name, "desc", project);
        page.setId(id);

        PageXml xml = new PageXml();
        xml.setId("xml-" + id);
        xml.setFileName(name + ".xml");
        xml.setFilePath(xmlRelativePath);
        xml.setMimeType("application/xml");
        xml.setSchema(XmlSchema.PAGE_XML);
        xml.setVariant("main");
        xml.setBaseName(name.toLowerCase());
        xml.setPage(page);
        page.setXmlFiles(new HashSet<>(List.of(xml)));

        if (imageRelativePath != null) {
            PageImage image = new PageImage();
            image.setId("img-" + id);
            image.setFileName(name + ".png");
            image.setFilePath(imageRelativePath);
            image.setMimeType("image/png");
            image.setVariant("main");
            image.setBaseName(name.toLowerCase());
            image.setPage(page);
            page.setImages(new HashSet<>(List.of(image)));
        }

        return page;
    }

    private PageDto pageDto(String imageFilename, List<RegionDto> regions, ReadingOrderDto readingOrder) {
        return new PageDto(
                imageFilename,
                400,
                200,
                null,
                null,
                null,
                null,
                "pcgts-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                regions,
                readingOrder,
                "2019-07-15"
        );
    }

    private ReadingOrderDto readingOrder(String... regionIds) {
        List<ReadingOrderDto.GroupMemberDto> members = new ArrayList<>();
        for (int i = 0; i < regionIds.length; i++) {
            members.add(new ReadingOrderDto.RegionRefDto("ref-" + i, regionIds[i], i));
        }
        return new ReadingOrderDto(
                new ReadingOrderDto.GroupDto("root", true, "root", null, members, null, null),
                null
        );
    }

    private RegionDto textRegion(String id, List<TextLineDto> lines) {
        return new RegionDto(
                id,
                RegionKind.TextRegion,
                simplePolygon(10, 20, 300, 100),
                lines,
                null,
                "paragraph",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private TextLineDto textLine(String id, String text, int gtIndex, PolygonDto baseline) {
        return textLine(id, text, gtIndex, baseline, null, null);
    }

    private TextLineDto textLine(String id,
                                 String text,
                                 int gtIndex,
                                 PolygonDto baseline,
                                 String alternateText,
                                 Integer alternateGtIndex) {
        List<TextContentVariantDto> variants = new ArrayList<>();
        variants.add(new TextContentVariantDto(text, null, gtIndex));
        if (alternateText != null && alternateGtIndex != null) {
            variants.add(new TextContentVariantDto(alternateText, null, alternateGtIndex));
        }
        return new TextLineDto(
                id,
                simplePolygon(10, 20, 280, 40),
                baseline,
                variants,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                gtIndex,
                null,
                null
        );
    }

    private PolygonDto simplePolygon(int minX, int minY, int maxX, int maxY) {
        return new PolygonDto(List.of(
                point(minX, minY),
                point(maxX, minY),
                point(maxX, maxY),
                point(minX, maxY)
        ));
    }

    private PolygonDto simpleBaseline(int startX, int startY, int endX, int endY) {
        return new PolygonDto(List.of(point(startX, startY), point(endX, endY)));
    }

    private PointDto point(int pixelX, int pixelY) {
        double worldX = (pixelX / 400d) * 2d - 1d;
        double worldY = 1d - (pixelY / 200d) * 2d;
        return new PointDto(worldX, worldY);
    }
}
