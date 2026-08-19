package de.uniwue.zpd.dachs.larex.backend.controller.page;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.service.export.DocumentExportService;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageTextConfidenceStatsService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusReadService;
import de.uniwue.zpd.dachs.larex.backend.service.search.SearchPreviewService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.tag.TagLookupService;
import de.uniwue.zpd.dachs.larex.backend.service.task.SubtaskService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlRawEditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageControllerExportTest {

    @Mock
    private PageService pageService;
    @Mock
    private SubtaskService subtaskService;
    @Mock
    private PageFilterIndexService pageFilterIndexService;
    @Mock
    private PageIndexStatusReadService pageIndexStatusReadService;
    @Mock
    private TagLookupService tagLookupService;
    @Mock
    private PageXmlRawEditService pageXmlRawEditService;
    @Mock
    private PageXmlConversionService pageXmlConversionService;
    @Mock
    private DocumentExportService documentExportService;
    @Mock
    private WorkspaceQuotaGuardService workspaceQuotaGuardService;
    @Mock
    private SearchPreviewService searchPreviewService;
    @Mock
    private PageOrderService pageOrderService;
    @Mock
    private PageTextConfidenceStatsService pageTextConfidenceStatsService;
    @Mock
    private de.uniwue.zpd.dachs.larex.backend.service.page.PageWorkflowService pageWorkflowService;
    @Mock
    private ArchiveIoService archiveIoService;

    @org.junit.jupiter.api.io.TempDir
    Path tempDir;

    @Test
    void exportPage_returnsAttachmentHeadersAndBody() throws Exception {
        PageController controller = new PageController(
                pageService,
                subtaskService,
                pageFilterIndexService,
                pageIndexStatusReadService,
                tagLookupService,
                pageXmlRawEditService,
                pageXmlConversionService,
                documentExportService,
                workspaceQuotaGuardService,
                searchPreviewService,
                pageOrderService,
                pageTextConfidenceStatsService,
                pageWorkflowService,
                archiveIoService
        );

        byte[] body = "hello".getBytes();
        when(documentExportService.exportPageStream("project-1", "page-1", "user-1",
                new DocumentExportDto.PageExportRequest(DocumentExportDto.ExportFormat.TXT, null, null, null, null, null, null, null, null)))
                .thenReturn(new DocumentExportService.StreamingDocumentExportResult("Alpha.txt", "text/plain", outputStream -> outputStream.write(body)));

        ResponseEntity<?> response = controller.exportPage(
                "project-1",
                "page-1",
                new DocumentExportDto.PageExportRequest(DocumentExportDto.ExportFormat.TXT, null, null, null, null, null, null, null, null),
                "user-1"
        );
        ByteArrayOutputStream streamedBody = new ByteArrayOutputStream();
        ((StreamingResponseBody) response.getBody()).writeTo(streamedBody);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("text/plain", response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"Alpha.txt\"", response.getHeaders().getFirst("Content-Disposition"));
        assertArrayEquals(body, streamedBody.toByteArray());
    }

    @Test
    void exportXml_fallsBackToApplicationXmlForInvalidMimeType() throws Exception {
        PageController controller = new PageController(
                pageService,
                subtaskService,
                pageFilterIndexService,
                pageIndexStatusReadService,
                tagLookupService,
                pageXmlRawEditService,
                pageXmlConversionService,
                documentExportService,
                workspaceQuotaGuardService,
                searchPreviewService,
                pageOrderService,
                pageTextConfidenceStatsService,
                pageWorkflowService,
                archiveIoService
        );
        ReflectionTestUtils.setField(controller, "uploadDir", tempDir.toString());

        Path xmlFile = tempDir.resolve("sample.xml");
        Files.writeString(xmlFile, "<PcGts/>", StandardCharsets.UTF_8);

        PageXml xml = new PageXml();
        xml.setId("xml-1");
        xml.setFileName("sample.xml");
        xml.setFilePath("sample.xml");
        xml.setMimeType("application/xml; charset==utf-8");
        xml.setSchema(XmlSchema.UNKNOWN);
        Page page = new Page();
        Project project = new Project();
        page.setProject(project);
        xml.setPage(page);

        when(pageService.getXmlById("xml-1", "user-1")).thenReturn(xml);

        ResponseEntity<?> response = controller.exportXml(
                "project-1",
                "xml-1",
                "2019-07-15",
                "user-1"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/xml", response.getHeaders().getContentType().toString());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Resource);
    }
}
