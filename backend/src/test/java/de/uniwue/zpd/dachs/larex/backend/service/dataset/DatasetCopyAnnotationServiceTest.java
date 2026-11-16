package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItemCopyFile;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.PageXmlWriteResult;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.version.DatasetItemCopyXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlEditorCheck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetCopyAnnotationServiceTest {

    private static final String CACHE_KEY = "dataset-copy:dataset-1:item-1:xml-1";

    @TempDir
    Path uploadDir;

    @Mock
    private DatasetRepository datasetRepository;
    @Mock
    private DatasetItemRepository datasetItemRepository;
    @Mock
    private PageXmlRepository pageXmlRepository;
    @Mock
    private WorkspaceAccessService workspaceAccessService;
    @Mock
    private AuthorizationPolicyService authorizationPolicyService;
    @Mock
    private AnnotationToPageXmlExporter pageXmlExporter;
    @Mock
    private AnnotationReadCache annotationReadCache;
    @Mock
    private DatasetItemCopyXmlVersionService copyXmlVersionService;
    @Mock
    private WorkspaceQuotaRefreshService workspaceQuotaRefreshService;

    private DatasetCopyAnnotationService service;
    private DatasetItemCopyFile copyXml;
    private Path xmlPath;

    @BeforeEach
    void setUp() throws IOException {
        service = new DatasetCopyAnnotationService(datasetRepository, datasetItemRepository, pageXmlRepository,
                workspaceAccessService, authorizationPolicyService, mock(PageXmlToAnnotationParser.class), pageXmlExporter,
                annotationReadCache, copyXmlVersionService, mock(PageXmlEditorCheck.class), workspaceQuotaRefreshService);
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());

        Dataset dataset = new Dataset();
        dataset.setId("dataset-1");
        dataset.setWorkspaceId("workspace-1");
        copyXml = new DatasetItemCopyFile();
        copyXml.setId("xml-1");
        copyXml.setKind(DatasetItemCopyFile.Kind.XML);
        copyXml.setSourceFileId("source-xml");
        copyXml.setFilePath("copies/page.xml");
        DatasetItem item = new DatasetItem();
        item.setId("item-1");
        item.setDataset(dataset);
        item.setMode(DatasetItem.Mode.COPY);
        item.setCopyFiles(List.of(copyXml));

        xmlPath = uploadDir.resolve("copies/page.xml");
        Files.createDirectories(xmlPath.getParent());
        Files.writeString(xmlPath, "<PcGts/>");

        when(datasetRepository.findByIdAndWorkspaceId("dataset-1", "workspace-1")).thenReturn(Optional.of(dataset));
        when(datasetItemRepository.findByIdAndDatasetId("item-1", "dataset-1")).thenReturn(Optional.of(item));
        when(authorizationPolicyService.canAccessWorkspace("workspace-1", "user-1")).thenReturn(true);
        when(pageXmlRepository.findById("source-xml")).thenReturn(Optional.empty());
    }

    @Test
    void saveAnnotation_rejectedWriteCreatesNoVersion() throws Exception {
        when(pageXmlExporter.writeValidated(any(), any(), any(Path.class)))
                .thenThrow(new IOException("PAGE XML validation failed before persistence"));

        assertThrows(IOException.class, () -> save(pageDto()));

        assertEquals("<PcGts/>", Files.readString(xmlPath));
        verify(copyXmlVersionService, never()).createVersion(any(), any(), any());
        verify(copyXmlVersionService, never()).pruneOldVersions(anyString());
        verify(annotationReadCache, never()).evict(anyString());
    }

    @Test
    void saveAnnotation_versionsAfterValidationAndCachesWhatWasWritten() throws Exception {
        PageDto written = pageDto();
        doAnswer(invocation -> {
            Path tempPath = invocation.getArgument(2);
            Files.writeString(tempPath, "<PcGts>new</PcGts>");
            return new PageXmlWriteResult(Files.size(tempPath), List.of(), "2024-07-15", written);
        }).when(pageXmlExporter).writeValidated(any(), any(), any(Path.class));

        save(pageDto());

        assertEquals("<PcGts>new</PcGts>", Files.readString(xmlPath));
        InOrder order = inOrder(pageXmlExporter, copyXmlVersionService, annotationReadCache);
        order.verify(pageXmlExporter).writeValidated(any(), any(), any(Path.class));
        order.verify(copyXmlVersionService).createVersion(copyXml, "user-1", "Saved from annotation editor");
        order.verify(annotationReadCache).evict(CACHE_KEY);
        order.verify(copyXmlVersionService).pruneOldVersions("xml-1");
        order.verify(annotationReadCache).put(CACHE_KEY, xmlPath, written);
    }

    private void save(PageDto pageDto) throws IOException {
        service.saveAnnotation("workspace-1", "dataset-1", "item-1", "xml-1", pageDto, "user-1");
    }

    private static PageDto pageDto() {
        return mock(PageDto.class);
    }
}
