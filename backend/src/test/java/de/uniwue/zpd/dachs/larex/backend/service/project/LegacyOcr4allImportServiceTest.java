package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService.StoredFileDescriptor;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyOcr4allImportServiceTest {

    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private PageRepository pageRepository;
    @Mock
    private PageXmlRepository pageXmlRepository;
    @Mock
    private WorkspaceAccessService workspaceAccessService;
    @Mock
    private HierarchicalFileStorageService hierarchicalFileStorageService;
    @Mock
    private WorkspaceQuotaGuardService workspaceQuotaGuardService;
    @Mock
    private PageFilterIndexService pageFilterIndexService;
    @Mock
    private PageXmlCanonicalizationService pageXmlCanonicalizationService;

    @Test
    void importProjectCreatesProjectFromInputAndProcessingDirectories() throws Exception {
        LegacyOcr4allImportService service = new LegacyOcr4allImportService(
                libraryRepository,
                projectRepository,
                pageRepository,
                pageXmlRepository,
                workspaceAccessService,
                hierarchicalFileStorageService,
                workspaceQuotaGuardService,
                pageFilterIndexService,
                pageXmlCanonicalizationService
        );

        Library library = new Library("ws-1", "Library");
        library.setId("lib-1");

        MockMultipartFile originalImage = new MockMultipartFile(
                "files",
                "legacy/input/0001.png",
                "image/png",
                "original".getBytes()
        );
        MockMultipartFile processingImage = new MockMultipartFile(
                "files",
                "legacy/processing/bin/0001.png",
                "image/png",
                "variant".getBytes()
        );
        MockMultipartFile pageXml = new MockMultipartFile(
                "files",
                "legacy/processing/0001.xml",
                "application/xml",
                "<PcGts/>".getBytes()
        );

        when(libraryRepository.findByWorkspaceId("ws-1")).thenReturn(Optional.of(library));
        when(projectRepository.existsByNameAndLibraryId("legacy", "lib-1")).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId("project-legacy");
            return project;
        });
        when(pageRepository.save(any(Page.class))).thenAnswer(invocation -> {
            Page page = invocation.getArgument(0);
            page.setId("page-" + page.getName());
            return page;
        });
        when(pageXmlRepository.save(any(PageXml.class))).thenAnswer(invocation -> {
            PageXml xml = invocation.getArgument(0);
            xml.setId("xml-1");
            return xml;
        });
        when(workspaceQuotaGuardService.reserveBytesOrThrow(eq("ws-1"), anyLong(), eq("legacy-ocr4all-import")))
                .thenReturn(20L);
        when(hierarchicalFileStorageService.storeMultipartFile(
                any(MultipartFile.class),
                eq("ws-1"),
                eq("project-legacy"),
                any(StoredFileType.class),
                eq("user-1")
        )).thenAnswer(invocation -> {
            MultipartFile file = invocation.getArgument(0);
            StoredFileType fileType = invocation.getArgument(3);
            String fileName = Path.of(file.getOriginalFilename()).getFileName().toString();
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
            String mimeType = fileType == StoredFileType.XML ? "application/xml" : file.getContentType();
            return new StoredFileDescriptor(
                    fileName + "-uuid",
                    "stored/" + fileName,
                    fileName,
                    mimeType,
                    extension,
                    file.getSize(),
                    "sha",
                    fileType
            );
        });

        ProjectPackageDto.ImportResult result = service.importProject(
                "ws-1",
                "user-1",
                List.of(originalImage, processingImage, pageXml),
                List.of("legacy/input/0001.png", "legacy/processing/bin/0001.png", "legacy/processing/0001.xml"),
                "legacy"
        );

        assertEquals("project-legacy", result.projectId());
        assertEquals("legacy", result.projectName());
        assertEquals(1, result.pageCount());
        assertEquals(2, result.imageCount());
        assertEquals(1, result.xmlCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Page>> pagesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(pageRepository).saveAll(pagesCaptor.capture());
        Page importedPage = pagesCaptor.getValue().iterator().next();
        Set<String> variants = importedPage.getImages().stream()
                .map(PageImage::getVariant)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("original", "bin"), variants);
        verify(pageFilterIndexService).rebuildProjectIndex("project-legacy");
    }
}
