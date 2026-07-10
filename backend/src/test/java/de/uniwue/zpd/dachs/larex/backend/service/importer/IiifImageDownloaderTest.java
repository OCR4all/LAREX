package de.uniwue.zpd.dachs.larex.backend.service.importer;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class IiifImageDownloaderTest {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void detectsValidatedImageTypeInsteadOfTrustingResponseMimeType() throws Exception {
        String url = serve(ONE_PIXEL_PNG, "text/html", false);
        IiifImageDownloader downloader = downloader();

        try (IiifImageDownloader.DownloadedImage image = downloader.download(url, "Page 1", 1024)) {
            assertThat(image.mimeType()).isEqualTo("image/png");
            assertThat(image.extension()).isEqualTo("png");
            assertThat(image.sizeBytes()).isEqualTo(ONE_PIXEL_PNG.length);
            assertThat(Files.readAllBytes(image.path())).isEqualTo(ONE_PIXEL_PNG);
        }
    }

    @Test
    void rejectsInvalidImageContent() throws Exception {
        String url = serve("<html>not an image</html>".getBytes(), "image/jpeg", false);

        assertThatThrownBy(() -> downloader().download(url, "Page 1", 1024))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a supported");
    }

    @Test
    void rejectsDeclaredResponseLargerThanLimit() throws Exception {
        String url = serve(new byte[128], "image/jpeg", false);

        assertThatThrownBy(() -> downloader().download(url, "Page 1", 64))
                .isInstanceOf(IiifImageDownloader.DownloadSizeLimitExceededException.class)
                .extracting("observedBytes")
                .isEqualTo(128L);
    }

    @Test
    void stopsChunkedResponseWhenStreamCrossesLimit() throws Exception {
        String url = serve(new byte[128], "image/jpeg", true);

        assertThatThrownBy(() -> downloader().download(url, "Page 1", 64))
                .isInstanceOf(IiifImageDownloader.DownloadSizeLimitExceededException.class)
                .extracting("maxBytes")
                .isEqualTo(64L);
    }

    private IiifImageDownloader downloader() {
        return new IiifImageDownloader(
                mock(IiifRemoteRequestThrottler.class),
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        );
    }

    private String serve(byte[] body, String contentType, boolean chunked) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, chunked ? 0 : body.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/image";
    }
}
