package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.config.UploadProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.UploadSessionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile.UploadFileStatus;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkedUploadValidationTest {

    private static final int CHUNK_SIZE = 5;

    @TempDir
    Path temporary;

    private final UploadSessionRepository sessions = mock(UploadSessionRepository.class);
    private final UploadSessionFileRepository files = mock(UploadSessionFileRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final WorkspaceQuotaGuardService quotaGuard = mock(WorkspaceQuotaGuardService.class);

    private ChunkedUploadService uploads;
    private UploadSessionFile file;

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties();
        properties.setTempDirectory(temporary);
        properties.setChunkSizeBytes(CHUNK_SIZE);
        uploads = new ChunkedUploadService(sessions, files, projects, mock(WorkspaceAccessService.class),
                mock(UploadDirectoryPreflightService.class), mock(ApplicationEventPublisher.class),
                mock(UploadRealtimePublisher.class), quotaGuard, properties, mock(PdfPreflightService.class));

        UploadSession session = new UploadSession("project", "workspace", "user", 1, 12);
        session.setId("session");
        file = new UploadSessionFile("page.png", 12, "image/png", "page", "default", 3);
        file.setId("file");
        session.addFile(file);
        when(sessions.findByIdAndUserId("session", "user")).thenReturn(Optional.of(session));
        when(files.lockByIdAndSessionId("file", "session")).thenReturn(Optional.of(file));
    }

    @Test
    void acceptsExactChunksAndReassemblesTheDeclaredFile() throws Exception {
        upload(0, 3, "aaaaa");
        upload(1, 3, "bbbbb");
        var response = upload(2, 3, "cc");

        assertThat(response.complete()).isTrue();
        assertThat(file.getStatus()).isEqualTo(UploadFileStatus.UPLOADED);
        assertThat(Files.readString(Path.of(file.getTempFilePath()))).isEqualTo("aaaaabbbbbcc");
    }

    @Test
    void rejectsChunksLargerOrSmallerThanExpected() {
        assertThatThrownBy(() -> upload(0, 3, "a".repeat(1024))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> upload(0, 3, "aaaa")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> upload(2, 3, "ccc")).isInstanceOf(IllegalArgumentException.class);

        assertThat(file.getChunksReceived()).isZero();
        assertThat(temporary.resolve("session/file")).doesNotExist();
    }

    @Test
    void rejectsIndicesOutsideTheDeclaredRange() {
        for (int index : new int[]{-1, 3, 100}) {
            assertThatThrownBy(() -> upload(index, 3, "aaaaa")).isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(temporary.resolve("session/file")).doesNotExist();
    }

    @Test
    void rejectsADifferentChunkCountThanTheServerComputed() {
        assertThatThrownBy(() -> upload(0, 999, "aaaaa")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countsARetriedChunkOnlyOnce() throws Exception {
        upload(0, 3, "aaaaa");
        upload(0, 3, "AAAAA");
        upload(1, 3, "bbbbb");

        assertThat(file.getChunksReceived()).isEqualTo(2);
        assertThat(file.getStatus()).isEqualTo(UploadFileStatus.UPLOADING);

        upload(2, 3, "cc");
        assertThat(Files.readString(Path.of(file.getTempFilePath()))).isEqualTo("AAAAAbbbbbcc");
    }

    @Test
    void rejectsChunksAfterTheFileIsComplete() throws Exception {
        upload(0, 3, "aaaaa");
        upload(1, 3, "bbbbb");
        upload(2, 3, "cc");

        assertThatThrownBy(() -> upload(2, 3, "cc")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(Path.of(file.getTempFilePath()))).isEqualTo("aaaaabbbbbcc");
    }

    @Test
    void rejectsDeclaredSizesThatOverflowTheSessionTotal() {
        Project project = mock(Project.class);
        when(project.getLibrary()).thenReturn(new Library("workspace", "library"));
        when(projects.findById("project")).thenReturn(Optional.of(project));
        var request = new UploadSessionDto.CreateSessionRequest(List.of(
                new UploadSessionDto.FileMetadata("a.png", Long.MAX_VALUE, "image/png", null, null),
                new UploadSessionDto.FileMetadata("b.png", Long.MAX_VALUE, "image/png", null, null)));

        assertThatThrownBy(() -> uploads.createSession("user", "workspace", "project", request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(quotaGuard, never()).reserveBytesOrThrow(anyString(), anyLong(), anyString());
    }

    private UploadSessionDto.UploadChunkResponse upload(int index, int totalChunks, String content) throws Exception {
        return uploads.uploadChunk("user", "session", "file", index, totalChunks,
                new MockMultipartFile("file", "chunk", "application/octet-stream", content.getBytes()));
    }
}
