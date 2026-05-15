package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.upload.AsyncUploadProcessor;
import de.uniwue.zpd.dachs.larex.backend.service.upload.ChunkedUploadService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.StorageTrackingService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsyncUploadProcessorPdfTest {

  private static Path uploadDir;
  private static Path tempDir;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    if (uploadDir == null || tempDir == null) {
      try {
        uploadDir = Files.createTempDirectory("larex-upload-dir");
        tempDir = Files.createTempDirectory("larex-temp-dir");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    registry.add("file.upload-dir", () -> uploadDir.toString());
    registry.add("larex.upload.temp-directory", () -> tempDir.toString());
  }

  @MockBean
  private NotificationService notificationService;

  @MockBean
  private StorageTrackingService storageTrackingService;

  @Autowired
  private AsyncUploadProcessor asyncUploadProcessor;

  @Autowired
  private ChunkedUploadService chunkedUploadService;

  @SpyBean
  private HierarchicalFileStorageService hierarchicalFileStorageService;

  @Autowired
  private LibraryRepository libraryRepository;

  @Autowired
  private ProjectRepository projectRepository;

  @Autowired
  private UploadSessionRepository uploadSessionRepository;

  @Autowired
  private UploadSessionFileRepository uploadSessionFileRepository;

  @Autowired
  private PageRepository pageRepository;

  @Autowired
  private PageImageRepository pageImageRepository;

  @Test
  void convertsPdfIntoPagesAndImages() throws Exception {
    Library library = libraryRepository.save(new Library("ws-test", "Test Library"));
    Project project = projectRepository.save(new Project("Test Project", "desc", library));

    UploadSession session = new UploadSession(project.getId(), library.getWorkspaceId(), "user-test", 1, 1);
    session.setStatus(UploadSession.UploadSessionStatus.PROCESSING);
    session = uploadSessionRepository.save(session);

    Path sessionDir = tempDir.resolve(session.getId());
    Files.createDirectories(sessionDir);
    Path pdfPath = sessionDir.resolve("sample.pdf");

    try (PDDocument doc = new PDDocument()) {
      doc.addPage(new PDPage());
      doc.addPage(new PDPage());
      doc.save(pdfPath.toFile());
    }

    long pdfSize = Files.size(pdfPath);

    UploadSessionFile file = new UploadSessionFile("sample.pdf", pdfSize, "application/pdf", "myprefix", "pdf", 1);
    file.setSession(session);
    file.setTempFilePath(pdfPath.toString());
    file.setStatus(UploadSessionFile.UploadFileStatus.UPLOADED);
    file = uploadSessionFileRepository.save(file);

    asyncUploadProcessor.doProcessUploadSession(session.getId());

    List<de.uniwue.zpd.dachs.larex.backend.entity.Page> pages = pageRepository.findByProjectId(project.getId());
    assertThat(pages).hasSize(2);
    assertThat(pages)
      .extracting(de.uniwue.zpd.dachs.larex.backend.entity.Page::getName)
      .containsExactlyInAnyOrder("myprefix_001", "myprefix_002");

    for (var page : pages) {
      var images = pageImageRepository.findByPageId(page.getId());
      assertThat(images).hasSize(1);
      var image = images.get(0);
      assertThat(image.getVariant()).isEqualTo("png");
      assertThat(Files.exists(uploadDir.resolve(image.getFilePath()))).isTrue();
    }

    UploadSessionFile updated = uploadSessionFileRepository.findById(file.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(UploadSessionFile.UploadFileStatus.COMPLETED);

    UploadSession updatedSession = uploadSessionRepository.findById(session.getId()).orElseThrow();
    assertThat(updatedSession.getStatus()).isEqualTo(UploadSession.UploadSessionStatus.COMPLETED);
  }

  @Test
  void stopsPdfProcessingWhenSessionIsCancelled() throws Exception {
    Library library = libraryRepository.save(new Library("ws-cancel", "Cancel Library"));
    Project project = projectRepository.save(new Project("Cancel Project", "desc", library));

    UploadSession session = new UploadSession(project.getId(), library.getWorkspaceId(), "user-cancel", 1, 1);
    session.setStatus(UploadSession.UploadSessionStatus.PROCESSING);
    session = uploadSessionRepository.save(session);
    final String sessionId = session.getId();

    Path sessionDir = tempDir.resolve(sessionId);
    Files.createDirectories(sessionDir);
    Path pdfPath = sessionDir.resolve("cancel-sample.pdf");
    final int totalPages = 8;

    try (PDDocument doc = new PDDocument()) {
      for (int i = 0; i < totalPages; i++) {
        doc.addPage(new PDPage());
      }
      doc.save(pdfPath.toFile());
    }

    long pdfSize = Files.size(pdfPath);

    UploadSessionFile file = new UploadSessionFile("cancel-sample.pdf", pdfSize, "application/pdf", "cancelprefix", "pdf", 1);
    file.setSession(session);
    file.setTempFilePath(pdfPath.toString());
    file.setStatus(UploadSessionFile.UploadFileStatus.UPLOADED);
    uploadSessionFileRepository.save(file);

    AtomicBoolean cancelTriggered = new AtomicBoolean(false);
    doAnswer(invocation -> {
      Object result = invocation.callRealMethod();
      if (cancelTriggered.compareAndSet(false, true)) {
        chunkedUploadService.cancelSession("user-cancel", sessionId);
      }
      return result;
    }).when(hierarchicalFileStorageService).storeBufferedImage(
      any(BufferedImage.class),
      anyString(),
      anyString(),
      anyString(),
      anyString(),
      anyString()
    );

    try {
      asyncUploadProcessor.doProcessUploadSession(session.getId());
    } finally {
      reset(hierarchicalFileStorageService);
    }

    UploadSession updatedSession = uploadSessionRepository.findById(session.getId()).orElseThrow();
    assertThat(updatedSession.getStatus()).isEqualTo(UploadSession.UploadSessionStatus.CANCELLED);

    List<de.uniwue.zpd.dachs.larex.backend.entity.Page> pages = pageRepository.findByProjectId(project.getId());
    assertThat(pages.size()).isGreaterThan(0);
    assertThat(pages.size()).isLessThan(totalPages);
  }
}
