package de.uniwue.zpd.dachs.larex.backend.service.importer;

import com.sun.net.httpserver.HttpServer;
import de.uniwue.zpd.dachs.larex.backend.config.IiifProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.IiifImportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.JobRealtimePublisher;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IiifManifestSizeLimitTest {

    private static final int LIMIT = 1_048_576;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void rejectsManifestWhoseDeclaredLengthExceedsTheLimit() throws Exception {
        String url = serve(oversizedManifest(), false);

        assertThatThrownBy(() -> preview(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("larger than the allowed");
    }

    @Test
    void stopsChunkedManifestOnceItCrossesTheLimit() throws Exception {
        String url = serve(oversizedManifest(), true);

        assertThatThrownBy(() -> preview(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("larger than the allowed");
    }

    @Test
    void acceptsManifestWithinTheLimit() throws Exception {
        String url = serve("""
                {
                  "@context": "http://iiif.io/api/presentation/3/context.json",
                  "id": "https://example.org/iiif/manifest/1",
                  "type": "Manifest",
                  "label": { "en": ["Small"] },
                  "items": []
                }
                """.getBytes(StandardCharsets.UTF_8), true);

        assertThat(preview(url).manifest().label()).isEqualTo("Small");
    }

    private IiifImportDto.PreviewResponse preview(String url) throws IOException {
        IiifProperties properties = new IiifProperties();
        properties.setMaxManifestBytes(LIMIT);
        ProjectRepository projects = mock(ProjectRepository.class);
        Project project = new Project("Project", "desc", new Library("workspace", "Library"));
        project.setId("project");
        when(projects.findById("project")).thenReturn(Optional.of(project));
        IiifImportService service = new IiifImportService(projects, mock(PageRepository.class),
                mock(PageImageRepository.class), mock(IiifImportJobRepository.class),
                mock(IiifImportJobItemRepository.class), mock(WorkspaceAccessService.class),
                mock(WorkspaceQuotaGuardService.class), mock(PageOrderService.class),
                mock(IiifImportQueueService.class), mock(IiifRemoteRequestThrottler.class), new ObjectMapper(),
                mock(TaskExecutor.class), mock(JobRealtimePublisher.class), new IiifRemoteUrlGuard(true), properties);
        return service.previewFromManifestUrl("workspace", "project", "user", new IiifImportDto.PreviewRequest(url));
    }

    private static byte[] oversizedManifest() {
        byte[] body = new byte[LIMIT + 1024];
        Arrays.fill(body, (byte) ' ');
        body[0] = '{';
        body[body.length - 1] = '}';
        return body;
    }

    private String serve(byte[] body, boolean chunked) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/manifest", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, chunked ? 0 : body.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            } catch (IOException ignored) {
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/manifest";
    }
}
