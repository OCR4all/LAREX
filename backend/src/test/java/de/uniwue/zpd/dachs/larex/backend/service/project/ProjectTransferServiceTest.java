package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectTransferRequest;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.exception.ProjectNameConflictException;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectTransferRequestRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class ProjectTransferServiceTest {

    @Mock
    private ProjectTransferRequestRepository transferRequestRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private PageRepository pageRepository;
    @Mock
    private PageImageRepository pageImageRepository;
    @Mock
    private PageXmlRepository pageXmlRepository;
    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private WorkspaceQueryService workspaceQueryService;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private ProjectStarService projectStarService;
    @Mock
    private PageOrderService pageOrderService;
    @Mock
    private AuthorizationPolicyService authorizationPolicyService;
    @Mock
    private HierarchicalFileStorageService hierarchicalFileStorageService;
    @TempDir
    private Path tempDir;

    private ProjectTransferService service;

    @BeforeEach
    void setUp() {
        service = new ProjectTransferService(
                transferRequestRepository,
                projectRepository,
                pageRepository,
                pageImageRepository,
                pageXmlRepository,
                libraryRepository,
                workspaceQueryService,
                workspaceService,
                projectStarService,
                pageOrderService,
                authorizationPolicyService,
                hierarchicalFileStorageService
        );
    }

    @Test
    void reportsDuplicateTargetNameBeforeCreatingTransfer() {
        Library sourceLibrary = library("source-workspace", "source-library");
        Library targetLibrary = library("target-workspace", "target-library");
        Project sourceProject = project("source-project", "123", sourceLibrary);
        Project existingProject = project("existing-project", "123", targetLibrary);

        givenCommonTransferState(sourceProject, targetLibrary);
        when(projectRepository.findByNameAndLibraryId("123", "target-library"))
                .thenReturn(Optional.of(existingProject));

        ProjectNameConflictException exception = assertThrows(
                ProjectNameConflictException.class,
                () -> service.requestProjectTransfer(
                        "source-project",
                        "target-workspace",
                        "user-1",
                        null,
                        ProjectTransferRequest.TransferType.MOVE
                )
        );

        assertEquals("123", exception.getProjectName());
        verify(transferRequestRepository, never()).save(any());
    }

    @Test
    void appliesReplacementNameWhenMovingProject() {
        Library sourceLibrary = library("source-workspace", "source-library");
        Library targetLibrary = library("target-workspace", "target-library");
        Project sourceProject = project("source-project", "123", sourceLibrary);

        givenCommonTransferState(sourceProject, targetLibrary);
        when(projectRepository.findByNameAndLibraryId("renamed", "target-library"))
                .thenReturn(Optional.empty());
        when(transferRequestRepository.save(any(ProjectTransferRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.requestProjectTransfer(
                "source-project",
                "target-workspace",
                "user-1",
                null,
                ProjectTransferRequest.TransferType.MOVE,
                "renamed"
        );

        assertEquals("renamed", sourceProject.getName());
        assertEquals(targetLibrary, sourceProject.getLibrary());
    }

    @Test
    void movesImagesIntoTheTargetWorkspaceBeforeChangingProjectOwnership() throws Exception {
        Library sourceLibrary = library("source-workspace", "source-library");
        Library targetLibrary = library("target-workspace", "target-library");
        Project sourceProject = project("source-project", "123", sourceLibrary);
        Page sourcePage = new Page("page-1", null, sourceProject);
        sourcePage.setId("source-page");
        String oldPath = "ws/source-workspace/pr/source-project/img/aa/bb/source.jpg";
        PageImage sourceImage = new PageImage(
                "page-1.jpg", oldPath, "image/jpeg", 42L, "original", "page-1", sourcePage
        );
        Path sourcePath = Files.writeString(tempDir.resolve("move-source.jpg"), "image");

        givenCommonTransferState(sourceProject, targetLibrary);
        when(projectRepository.findByNameAndLibraryId("123", "target-library"))
                .thenReturn(Optional.empty());
        when(transferRequestRepository.save(any(ProjectTransferRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(pageRepository.findByProjectId("source-project")).thenReturn(List.of(sourcePage));
        when(pageImageRepository.findByPageIdIn(List.of("source-page"))).thenReturn(List.of(sourceImage));
        when(pageXmlRepository.findByPage_IdIn(List.of("source-page"))).thenReturn(List.of());
        when(hierarchicalFileStorageService.resolveUploadPath(oldPath)).thenReturn(sourcePath);
        when(hierarchicalFileStorageService.storeFromPath(
                eq(sourcePath),
                eq("page-1.jpg"),
                eq("image/jpeg"),
                eq("target-workspace"),
                eq("source-project"),
                eq(StoredFile.StoredFileType.IMG),
                eq("user-1"),
                eq(false)
        )).thenReturn(new HierarchicalFileStorageService.StoredFileDescriptor(
                "target-file",
                "ws/target-workspace/pr/source-project/img/cc/dd/target.jpg",
                "page-1.jpg",
                "image/jpeg",
                "jpg",
                42L,
                "checksum",
                StoredFile.StoredFileType.IMG
        ));

        service.requestProjectTransfer(
                "source-project",
                "target-workspace",
                "user-1",
                null,
                ProjectTransferRequest.TransferType.MOVE
        );

        assertEquals("ws/target-workspace/pr/source-project/img/cc/dd/target.jpg", sourceImage.getFilePath());
        assertEquals(targetLibrary, sourceProject.getLibrary());
        verify(pageImageRepository).save(sourceImage);
        verify(hierarchicalFileStorageService).deleteUnreferencedStoredFiles(List.of(oldPath));
    }

    @Test
    void checksTargetProjectNameAvailabilityForTransferUsers() {
        Library sourceLibrary = library("source-workspace", "source-library");
        Library targetLibrary = library("target-workspace", "target-library");
        Project sourceProject = project("source-project", "123", sourceLibrary);

        when(projectRepository.findById("source-project")).thenReturn(Optional.of(sourceProject));
        when(workspaceQueryService.findWorkspaceById("target-workspace"))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(AbstractWorkspace.class)));
        when(authorizationPolicyService.canManageProjects(anyString(), anyString())).thenReturn(true);
        when(libraryRepository.findByWorkspaceId("target-workspace"))
                .thenReturn(Optional.of(targetLibrary));
        when(projectRepository.findByNameAndLibraryId("renamed", "target-library"))
                .thenReturn(Optional.empty());

        assertTrue(service.isProjectNameAvailable(
                "source-project", "target-workspace", " renamed ", "user-1"
        ));

        when(projectRepository.findByNameAndLibraryId("taken", "target-library"))
                .thenReturn(Optional.of(project("existing-project", "taken", targetLibrary)));

        assertFalse(service.isProjectNameAvailable(
                "source-project", "target-workspace", "taken", "user-1"
        ));
    }

    @Test
    void copiesImagesIntoIndependentTargetStorage() throws Exception {
        Library sourceLibrary = library("source-workspace", "source-library");
        Library targetLibrary = library("target-workspace", "target-library");
        Project sourceProject = project("source-project", "123", sourceLibrary);
        Page sourcePage = new Page("page-1", null, sourceProject);
        sourcePage.setId("source-page");
        PageImage sourceImage = new PageImage(
                "page-1.jpg",
                "ws/source-workspace/pr/source-project/img/aa/bb/source.jpg",
                "image/jpeg",
                42L,
                "original",
                "page-1",
                sourcePage
        );
        Path sourcePath = Files.writeString(tempDir.resolve("source.jpg"), "image");

        givenCommonTransferState(sourceProject, targetLibrary);
        when(projectRepository.findByNameAndLibraryId("123 (Copy)", "target-library"))
                .thenReturn(Optional.empty());
        when(transferRequestRepository.save(any(ProjectTransferRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            if (project.getId() == null) {
                project.setId("target-project");
            }
            return project;
        });
        when(pageRepository.findByProjectId("source-project")).thenReturn(List.of(sourcePage));
        when(pageOrderService.sortPages(any())).thenReturn(List.of(sourcePage));
        when(pageRepository.save(any(Page.class))).thenAnswer(invocation -> {
            Page page = invocation.getArgument(0);
            page.setId("target-page");
            return page;
        });
        when(pageImageRepository.findByPageIdIn(List.of("source-page"))).thenReturn(List.of(sourceImage));
        when(pageXmlRepository.findByPage_IdIn(List.of("source-page"))).thenReturn(List.of());
        when(hierarchicalFileStorageService.resolveUploadPath(sourceImage.getFilePath()))
                .thenReturn(sourcePath);
        when(hierarchicalFileStorageService.storeFromPath(
                eq(sourcePath),
                eq("page-1.jpg"),
                eq("image/jpeg"),
                eq("target-workspace"),
                eq("target-project"),
                eq(StoredFile.StoredFileType.IMG),
                eq("user-1"),
                eq(false)
        )).thenReturn(new HierarchicalFileStorageService.StoredFileDescriptor(
                "target-file",
                "ws/target-workspace/pr/target-project/img/cc/dd/target.jpg",
                "page-1.jpg",
                "image/jpeg",
                "jpg",
                42L,
                "checksum",
                StoredFile.StoredFileType.IMG
        ));

        service.requestProjectTransfer(
                "source-project",
                "target-workspace",
                "user-1",
                null,
                ProjectTransferRequest.TransferType.COPY
        );

        verify(pageImageRepository).save(org.mockito.ArgumentMatchers.argThat(copiedImage ->
                "ws/target-workspace/pr/target-project/img/cc/dd/target.jpg".equals(copiedImage.getFilePath())
                        && !sourceImage.getFilePath().equals(copiedImage.getFilePath())
        ));
    }

    @Test
    void abortsCopyInsteadOfSharingAMissingSourceFile() throws Exception {
        Library sourceLibrary = library("source-workspace", "source-library");
        Library targetLibrary = library("target-workspace", "target-library");
        Project sourceProject = project("source-project", "123", sourceLibrary);
        Page sourcePage = new Page("page-1", null, sourceProject);
        sourcePage.setId("source-page");
        PageImage sourceImage = new PageImage(
                "page-1.jpg",
                "ws/source-workspace/pr/source-project/img/aa/bb/missing.jpg",
                "image/jpeg",
                42L,
                "original",
                "page-1",
                sourcePage
        );

        givenCommonTransferState(sourceProject, targetLibrary);
        when(projectRepository.findByNameAndLibraryId("123 (Copy)", "target-library"))
                .thenReturn(Optional.empty());
        when(transferRequestRepository.save(any(ProjectTransferRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId("target-project");
            return project;
        });
        when(pageRepository.findByProjectId("source-project")).thenReturn(List.of(sourcePage));
        when(pageOrderService.sortPages(any())).thenReturn(List.of(sourcePage));
        when(pageRepository.save(any(Page.class))).thenAnswer(invocation -> {
            Page page = invocation.getArgument(0);
            page.setId("target-page");
            return page;
        });
        when(pageImageRepository.findByPageIdIn(List.of("source-page"))).thenReturn(List.of(sourceImage));
        when(pageXmlRepository.findByPage_IdIn(List.of("source-page"))).thenReturn(List.of());
        when(hierarchicalFileStorageService.resolveUploadPath(sourceImage.getFilePath()))
                .thenReturn(Path.of("/definitely-missing/project-transfer-source.jpg"));

        assertThrows(IllegalStateException.class, () -> service.requestProjectTransfer(
                "source-project",
                "target-workspace",
                "user-1",
                null,
                ProjectTransferRequest.TransferType.COPY
        ));

        verify(pageImageRepository, never()).save(any(PageImage.class));
        verify(hierarchicalFileStorageService, never()).storeFromPath(
                any(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), eq(false)
        );
    }

    private void givenCommonTransferState(Project sourceProject, Library targetLibrary) {
        when(projectRepository.findById("source-project")).thenReturn(Optional.of(sourceProject));
        when(workspaceQueryService.findWorkspaceById("target-workspace"))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(AbstractWorkspace.class)));
        when(transferRequestRepository.existsByProjectIdAndStatus(
                "source-project", ProjectTransferRequest.Status.PENDING
        )).thenReturn(false);
        when(authorizationPolicyService.canManageProjects(anyString(), anyString())).thenReturn(true);
        when(libraryRepository.findByWorkspaceId("target-workspace"))
                .thenReturn(Optional.of(targetLibrary));
    }

    private Library library(String workspaceId, String id) {
        Library library = new Library(workspaceId, workspaceId);
        library.setId(id);
        return library;
    }

    private Project project(String id, String name, Library library) {
        Project project = new Project(name, null, library);
        project.setId(id);
        return project;
    }
}
