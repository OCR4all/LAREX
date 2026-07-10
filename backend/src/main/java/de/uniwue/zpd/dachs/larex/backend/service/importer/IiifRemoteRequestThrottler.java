package de.uniwue.zpd.dachs.larex.backend.service.importer;

import de.uniwue.zpd.dachs.larex.backend.service.admin.IiifSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IiifRemoteRequestThrottler {

    private static final long DEFAULT_RETRY_AFTER_MILLIS = 5_000L;
    private static final long MAX_FALLBACK_RETRY_AFTER_MILLIS = 30_000L;
    private static final long MAX_SERVER_RETRY_AFTER_MILLIS = Duration.ofMinutes(5).toMillis();

    private final Map<String, HostState> hostStates = new ConcurrentHashMap<>();
    private final IiifSettingsService settingsService;
    private final TimeSource timeSource;
    private final WaitStrategy waitStrategy;

    @Autowired
    public IiifRemoteRequestThrottler(IiifSettingsService settingsService) {
        this(settingsService, System::currentTimeMillis, Thread::sleep);
    }

    IiifRemoteRequestThrottler(IiifSettingsService settingsService,
                               TimeSource timeSource,
                               WaitStrategy waitStrategy) {
        this.settingsService = settingsService;
        this.timeSource = timeSource;
        this.waitStrategy = waitStrategy;
    }

    public void awaitPreviewRequestSlot(String url) throws IOException {
        awaitRequestSlot(url, RequestMode.PREVIEW);
    }

    public void awaitDownloadRequestSlot(String url) throws IOException {
        awaitRequestSlot(url, RequestMode.DOWNLOAD);
    }

    public boolean isRateLimitResponse(int statusCode, HttpHeaders headers) {
        return statusCode == 429
                || (statusCode == 503 && headers.firstValue("retry-after").isPresent());
    }

    public void deferAfterRateLimit(String url, HttpHeaders headers, int attempt) {
        String hostKey = toHostKey(url);
        HostState state = hostStates.computeIfAbsent(hostKey, ignored -> new HostState());
        long backoffMillis = parseRetryAfterMillis(headers)
                .orElseGet(() -> Math.min(
                        MAX_FALLBACK_RETRY_AFTER_MILLIS,
                        DEFAULT_RETRY_AFTER_MILLIS * (1L << Math.max(0, attempt))
                ));
        synchronized (state) {
            long candidate = timeSource.currentTimeMillis() + backoffMillis;
            state.cooldownUntil = Math.max(state.cooldownUntil, candidate);
        }
    }

    private void awaitRequestSlot(String url, RequestMode requestMode) throws IOException {
        String hostKey = toHostKey(url);
        HostState state = hostStates.computeIfAbsent(hostKey, ignored -> new HostState());
        synchronized (state) {
            long now = timeSource.currentTimeMillis();
            long nextAllowedAt = state.cooldownUntil;
            if (requestMode == RequestMode.DOWNLOAD) {
                nextAllowedAt = Math.max(nextAllowedAt, state.nextDownloadAt);
            }

            long sleepMillis = nextAllowedAt - now;
            if (sleepMillis > 0) {
                sleepInterruptibly(sleepMillis);
            }

            if (requestMode == RequestMode.DOWNLOAD) {
                state.nextDownloadAt = timeSource.currentTimeMillis()
                        + settingsService.getEffectiveDownloadMinIntervalMs();
            }
        }
    }

    private OptionalLong parseRetryAfterMillis(HttpHeaders headers) {
        Optional<String> retryAfter = headers.firstValue("retry-after");
        if (retryAfter.isEmpty()) {
            return OptionalLong.empty();
        }

        String value = retryAfter.get().trim();
        try {
            long seconds = Long.parseLong(value);
            if (seconds <= 0) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Math.min(
                    MAX_SERVER_RETRY_AFTER_MILLIS,
                    Duration.ofSeconds(seconds).toMillis()
            ));
        } catch (NumberFormatException | ArithmeticException ignored) {
            // Retry-After also permits an RFC 1123 HTTP-date.
        }

        try {
            Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            long delayMillis = retryAt.toEpochMilli() - timeSource.currentTimeMillis();
            if (delayMillis <= 0) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Math.min(MAX_SERVER_RETRY_AFTER_MILLIS, delayMillis));
        } catch (DateTimeParseException ignored) {
            return OptionalLong.empty();
        }
    }

    private String toHostKey(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "unknown-host";
            }
            int port = uri.getPort();
            return port > 0 ? host.toLowerCase() + ":" + port : host.toLowerCase();
        } catch (URISyntaxException ignored) {
            return "invalid-host";
        }
    }

    private void sleepInterruptibly(long millis) throws IOException {
        try {
            waitStrategy.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for IIIF request throttle", e);
        }
    }

    private enum RequestMode {
        PREVIEW,
        DOWNLOAD
    }

    private static final class HostState {
        private long nextDownloadAt;
        private long cooldownUntil;
    }

    @FunctionalInterface
    interface TimeSource {
        long currentTimeMillis();
    }

    @FunctionalInterface
    interface WaitStrategy {
        void sleep(long millis) throws InterruptedException;
    }
}
