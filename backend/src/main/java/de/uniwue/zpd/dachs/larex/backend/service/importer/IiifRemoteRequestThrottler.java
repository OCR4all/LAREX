package de.uniwue.zpd.dachs.larex.backend.service.importer;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IiifRemoteRequestThrottler {

    private static final long DEFAULT_MIN_INTERVAL_MILLIS = 500L;
    private static final long DEFAULT_RETRY_AFTER_MILLIS = 5_000L;
    private static final long MAX_RETRY_AFTER_MILLIS = 30_000L;

    private final Map<String, Object> hostLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> nextAllowedAtByHost = new ConcurrentHashMap<>();

    public void awaitRequestSlot(String url) throws IOException {
        String hostKey = toHostKey(url);
        Object lock = hostLocks.computeIfAbsent(hostKey, ignored -> new Object());
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long nextAllowedAt = nextAllowedAtByHost.getOrDefault(hostKey, 0L);
            long sleepMillis = nextAllowedAt - now;
            if (sleepMillis > 0) {
                sleepInterruptibly(sleepMillis);
            }
            nextAllowedAtByHost.put(hostKey, System.currentTimeMillis() + DEFAULT_MIN_INTERVAL_MILLIS);
        }
    }

    public void deferAfterRateLimit(String url, HttpHeaders headers, int attempt) {
        String hostKey = toHostKey(url);
        long backoffMillis = parseRetryAfterMillis(headers)
                .orElseGet(() -> Math.min(MAX_RETRY_AFTER_MILLIS, DEFAULT_RETRY_AFTER_MILLIS * (1L << Math.max(0, attempt))));
        nextAllowedAtByHost.compute(hostKey, (_ignored, current) -> {
            long existing = current == null ? 0L : current;
            long candidate = System.currentTimeMillis() + backoffMillis;
            return Math.max(existing, candidate);
        });
    }

    private OptionalLong parseRetryAfterMillis(HttpHeaders headers) {
        Optional<String> retryAfter = headers.firstValue("retry-after");
        if (retryAfter.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            long seconds = Long.parseLong(retryAfter.get().trim());
            if (seconds <= 0) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Math.min(MAX_RETRY_AFTER_MILLIS, Duration.ofSeconds(seconds).toMillis()));
        } catch (NumberFormatException ignored) {
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
            return port > 0 ? host + ":" + port : host;
        } catch (URISyntaxException ignored) {
            return "invalid-host";
        }
    }

    private void sleepInterruptibly(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for IIIF request throttle", e);
        }
    }
}
