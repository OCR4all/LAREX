package de.uniwue.zpd.dachs.larex.backend.service.storage;

import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile;
import de.uniwue.zpd.dachs.larex.backend.repository.storage.StoredFileRepository;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadDirectoryPreflightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HierarchicalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private UploadDirectoryPreflightService uploadDirectoryPreflightService;

    private HierarchicalFileStorageService service;

    @BeforeEach
    void setUp() {
        service = new HierarchicalFileStorageService(
                storedFileRepository,
                uploadDirectoryPreflightService
        );
        ReflectionTestUtils.setField(service, "uploadRoot", tempDir.toAbsolutePath().normalize());
    }

    @Test
    void resolvesAbsolutePathInsideUploadRoot() {
        Path expected = tempDir.resolve("ws/workspace/pr/project/img/file.png").normalize();

        assertEquals(expected, service.resolveUploadPath(expected.toString()));
    }

    @Test
    void rejectsAbsolutePathOutsideUploadRoot() {
        Path outside = tempDir.getParent().resolve("outside.png").normalize();

        assertThrows(IllegalArgumentException.class, () -> service.resolveUploadPath(outside.toString()));
    }

    @Test
    void doesNotDeleteAFileOwnedByTheSourceProjectWhenDeletingALegacyCopy() {
        String sourcePath = "ws/source/pr/source-project/img/aa/bb/source.jpg";
        StoredFile sourceFile = new StoredFile();
        sourceFile.setStoragePath(sourcePath);
        sourceFile.setProjectId("source-project");
        when(storedFileRepository.findByStoragePathIn(Set.of(sourcePath)))
                .thenReturn(List.of(sourceFile));

        int deleted = service.deleteStoredFilesOwnedByProject(
                "copied-project",
                List.of(sourcePath)
        );

        assertEquals(0, deleted);
        verify(storedFileRepository, never()).markStatusByStoragePaths(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void doesNotDeleteAFileStillReferencedByBothTheSourceAndCopiedProjects() {
        String sharedPath = "ws/source/pr/source-project/img/aa/bb/source.jpg";
        when(storedFileRepository.findPageAssetPathsWithMultipleReferences(Set.of(sharedPath)))
                .thenReturn(List.of(sharedPath));

        int deleted = service.deleteStoredFiles(List.of(sharedPath));

        assertEquals(0, deleted);
        verify(storedFileRepository, never()).markStatusByStoragePaths(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void afterCommitCleanupDoesNotDeleteAFileWithOneRemainingReference() {
        String sharedPath = "ws/source/pr/source-project/xml/aa/bb/source.xml";
        when(storedFileRepository.findReferencedPageAssetPaths(Set.of(sharedPath)))
                .thenReturn(List.of(sharedPath));

        int deleted = service.deleteUnreferencedStoredFiles(List.of(sharedPath));

        assertEquals(0, deleted);
        verify(storedFileRepository, never()).markStatusByStoragePaths(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
