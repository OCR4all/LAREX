package de.uniwue.zpd.dachs.larex.backend.service.importer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;

@Service
public class IiifImageDownloader {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_HTTP_ATTEMPTS = 3;

    private final IiifRemoteRequestThrottler requestThrottler;
    private final HttpClient httpClient;

    @Autowired
    public IiifImageDownloader(IiifRemoteRequestThrottler requestThrottler) {
        this(requestThrottler, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(HTTP_TIMEOUT)
                .build());
    }

    IiifImageDownloader(IiifRemoteRequestThrottler requestThrottler, HttpClient httpClient) {
        this.requestThrottler = requestThrottler;
        this.httpClient = httpClient;
    }

    public DownloadedImage download(String imageUrl, String pageName, long maxBytes) throws IOException {
        if (maxBytes <= 0) {
            throw new DownloadSizeLimitExceededException(maxBytes, 0);
        }
        try {
            for (int attempt = 0; attempt < MAX_HTTP_ATTEMPTS; attempt++) {
                requestThrottler.awaitDownloadRequestSlot(imageUrl);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(imageUrl))
                        .timeout(HTTP_TIMEOUT)
                        .GET()
                        .header("Accept", "image/*, */*;q=0.8")
                        .header("User-Agent", "LAREX IIIF Import")
                        .build();
                HttpResponse<InputStream> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
                    if (contentLength > maxBytes) {
                        try (InputStream ignored = response.body()) {
                            throw new DownloadSizeLimitExceededException(maxBytes, contentLength);
                        }
                    }

                    Path tempFile = Files.createTempFile("larex-iiif-", ".download");
                    try (InputStream body = response.body()) {
                        long sizeBytes = copyBounded(body, tempFile, maxBytes);
                        DetectedImage detectedImage = validateImage(tempFile);
                        return new DownloadedImage(
                                tempFile,
                                detectedImage.mimeType(),
                                detectedImage.extension(),
                                sizeBytes
                        );
                    } catch (Exception e) {
                        Files.deleteIfExists(tempFile);
                        throw e;
                    }
                }

                try (InputStream ignored = response.body()) {
                    if (requestThrottler.isRateLimitResponse(response.statusCode(), response.headers())
                            && attempt < MAX_HTTP_ATTEMPTS - 1) {
                        requestThrottler.deferAfterRateLimit(imageUrl, response.headers(), attempt);
                        continue;
                    }
                    throw new IOException(buildStatusMessage(response.statusCode()));
                }
            }
            throw new IOException(buildStatusMessage(429));
        } catch (HttpTimeoutException e) {
            throw new IOException("Timed out while downloading the IIIF image for " + pageName + ".", e);
        } catch (ConnectException e) {
            throw new IOException("Could not reach the IIIF image server for " + pageName + ".", e);
        } catch (UnknownHostException e) {
            throw new IOException("Could not resolve the IIIF image host for " + pageName + ".", e);
        } catch (IOException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                throw e;
            }
            throw new IOException(
                    "Could not download the IIIF image for " + pageName + " because of a network error.",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading image for " + pageName, e);
        } catch (URISyntaxException e) {
            throw new IOException("IIIF image URL is invalid: " + imageUrl, e);
        }
    }

    private String buildStatusMessage(int statusCode) {
        return switch (statusCode) {
            case 401 -> "Image download failed with HTTP 401. The IIIF image requires authentication.";
            case 403 -> "Image download failed with HTTP 403. The IIIF image is not publicly accessible.";
            case 404 -> "Image download failed with HTTP 404. The IIIF image URL could not be found.";
            case 429 -> "Image download failed with HTTP 429. The IIIF server is rate limiting requests; retry later.";
            default -> "Image download failed with HTTP " + statusCode;
        };
    }

    private long copyBounded(InputStream source, Path target, long maxBytes) throws IOException {
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (var output = Files.newOutputStream(target)) {
            int read;
            while ((read = source.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (total > maxBytes - read) {
                    throw new DownloadSizeLimitExceededException(maxBytes, total + read);
                }
                output.write(buffer, 0, read);
                total += read;
            }
        }
        return total;
    }

    private DetectedImage validateImage(Path path) throws IOException {
        long fileSize = Files.size(path);
        if (fileSize < 10) {
            throw new IOException("Downloaded IIIF resource is not a valid supported image.");
        }

        byte[] header = new byte[20];
        int headerLength;
        try (InputStream input = Files.newInputStream(path)) {
            headerLength = input.read(header);
        }
        DetectedImage detected = detectImageType(header, headerLength);
        if (detected == null) {
            throw new IOException("Downloaded IIIF resource is not a supported JPEG, PNG, GIF, TIFF, or WebP image.");
        }

        if ("webp".equals(detected.extension())) {
            validateWebp(header, headerLength, fileSize);
            return detected;
        }

        try (ImageInputStream imageInput = ImageIO.createImageInputStream(path.toFile())) {
            if (imageInput == null) {
                throw new IOException("Downloaded IIIF image could not be decoded.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IOException("Downloaded IIIF image content is invalid.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) {
                    throw new IOException("Downloaded IIIF image has invalid dimensions.");
                }
            } finally {
                reader.dispose();
            }
        } catch (RuntimeException e) {
            throw new IOException("Downloaded IIIF image content is invalid.", e);
        }
        return detected;
    }

    private DetectedImage detectImageType(byte[] header, int length) {
        if (length >= 3
                && unsigned(header[0]) == 0xFF
                && unsigned(header[1]) == 0xD8
                && unsigned(header[2]) == 0xFF) {
            return new DetectedImage("image/jpeg", "jpg");
        }
        if (length >= 8 && Arrays.equals(
                Arrays.copyOf(header, 8),
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        )) {
            return new DetectedImage("image/png", "png");
        }
        if (length >= 6) {
            String signature = new String(header, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
            if ("GIF87a".equals(signature) || "GIF89a".equals(signature)) {
                return new DetectedImage("image/gif", "gif");
            }
        }
        if (length >= 4
                && ((header[0] == 'I' && header[1] == 'I' && unsigned(header[2]) == 0x2A && header[3] == 0)
                || (header[0] == 'M' && header[1] == 'M' && header[2] == 0 && unsigned(header[3]) == 0x2A))) {
            return new DetectedImage("image/tiff", "tiff");
        }
        if (length >= 12
                && asciiEquals(header, 0, "RIFF")
                && asciiEquals(header, 8, "WEBP")) {
            return new DetectedImage("image/webp", "webp");
        }
        return null;
    }

    private void validateWebp(byte[] header, int length, long fileSize) throws IOException {
        if (length < 16
                || !(asciiEquals(header, 12, "VP8 ")
                || asciiEquals(header, 12, "VP8L")
                || asciiEquals(header, 12, "VP8X"))) {
            throw new IOException("Downloaded WebP image content is invalid.");
        }
        long declaredRiffSize = Integer.toUnsignedLong(
                (unsigned(header[4]))
                        | (unsigned(header[5]) << 8)
                        | (unsigned(header[6]) << 16)
                        | (unsigned(header[7]) << 24)
        );
        if (declaredRiffSize + 8 > fileSize) {
            throw new IOException("Downloaded WebP image is truncated.");
        }
    }

    private boolean asciiEquals(byte[] bytes, int offset, String value) {
        if (offset + value.length() > bytes.length) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (bytes[offset + i] != (byte) value.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private record DetectedImage(String mimeType, String extension) {}

    public static class DownloadSizeLimitExceededException extends IOException {
        private final long maxBytes;
        private final long observedBytes;

        public DownloadSizeLimitExceededException(long maxBytes, long observedBytes) {
            super("IIIF image exceeds the allowed download size of " + maxBytes + " bytes.");
            this.maxBytes = maxBytes;
            this.observedBytes = observedBytes;
        }

        public long getMaxBytes() {
            return maxBytes;
        }

        public long getObservedBytes() {
            return observedBytes;
        }
    }

    public record DownloadedImage(
            Path path,
            String mimeType,
            String extension,
            long sizeBytes
    ) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
