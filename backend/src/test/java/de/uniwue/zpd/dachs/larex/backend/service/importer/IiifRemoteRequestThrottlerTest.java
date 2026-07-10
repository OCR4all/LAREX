package de.uniwue.zpd.dachs.larex.backend.service.importer;

import de.uniwue.zpd.dachs.larex.backend.service.admin.IiifSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IiifRemoteRequestThrottlerTest {

    private final IiifSettingsService settingsService = mock(IiifSettingsService.class);
    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    private final List<Long> waits = new ArrayList<>();
    private IiifRemoteRequestThrottler throttler;

    @BeforeEach
    void setUp() {
        when(settingsService.getEffectiveDownloadMinIntervalMs()).thenReturn(100);
        throttler = new IiifRemoteRequestThrottler(
                settingsService,
                now::get,
                millis -> {
                    waits.add(millis);
                    now.addAndGet(millis);
                }
        );
    }

    @Test
    void spacesDownloadsToTheSameHost() throws Exception {
        throttler.awaitDownloadRequestSlot("https://images.example.org/a.jpg");
        throttler.awaitDownloadRequestSlot("https://images.example.org/b.jpg");

        assertThat(waits).containsExactly(100L);
    }

    @Test
    void previewBypassesNormalDownloadPacing() throws Exception {
        throttler.awaitDownloadRequestSlot("https://images.example.org/a.jpg");
        throttler.awaitPreviewRequestSlot("https://images.example.org/manifest.json");

        assertThat(waits).isEmpty();
    }

    @Test
    void cooldownIsSharedByPreviewAndDownloadTraffic() throws Exception {
        throttler.deferAfterRateLimit(
                "https://images.example.org/a.jpg",
                headers(Map.of("Retry-After", List.of("2"))),
                0
        );
        throttler.awaitPreviewRequestSlot("https://images.example.org/manifest.json");

        throttler.deferAfterRateLimit(
                "https://images.example.org/manifest.json",
                headers(Map.of("Retry-After", List.of("3"))),
                0
        );
        throttler.awaitDownloadRequestSlot("https://images.example.org/b.jpg");

        assertThat(waits).containsExactly(2_000L, 3_000L);
    }

    @Test
    void differentHostsHaveIndependentState() throws Exception {
        throttler.awaitDownloadRequestSlot("https://one.example.org/a.jpg");
        throttler.awaitDownloadRequestSlot("https://two.example.org/a.jpg");

        assertThat(waits).isEmpty();
    }

    @Test
    void supportsHttpDateRetryAfter() throws Exception {
        String retryAt = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(now.get() + 3_000L),
                ZoneOffset.UTC
        ).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        throttler.deferAfterRateLimit(
                "https://images.example.org/a.jpg",
                headers(Map.of("Retry-After", List.of(retryAt))),
                0
        );

        throttler.awaitPreviewRequestSlot("https://images.example.org/manifest.json");

        assertThat(waits).containsExactly(3_000L);
    }

    @Test
    void usesBoundedFallbackWhenRetryAfterIsMissing() throws Exception {
        throttler.deferAfterRateLimit(
                "https://images.example.org/a.jpg",
                headers(Map.of()),
                0
        );

        throttler.awaitPreviewRequestSlot("https://images.example.org/manifest.json");

        assertThat(waits).containsExactly(5_000L);
    }

    @Test
    void runtimeIntervalChangesApplyToSubsequentSlots() throws Exception {
        AtomicInteger interval = new AtomicInteger(100);
        when(settingsService.getEffectiveDownloadMinIntervalMs()).thenAnswer(ignored -> interval.get());

        throttler.awaitDownloadRequestSlot("https://images.example.org/a.jpg");
        throttler.awaitDownloadRequestSlot("https://images.example.org/b.jpg");
        interval.set(20);
        throttler.awaitDownloadRequestSlot("https://images.example.org/c.jpg");
        throttler.awaitDownloadRequestSlot("https://images.example.org/d.jpg");

        assertThat(waits).containsExactly(100L, 100L, 20L);
    }

    @Test
    void recognizes429AndRetryAfter503Only() {
        HttpHeaders noHeaders = headers(Map.of());
        HttpHeaders retryAfter = headers(Map.of("Retry-After", List.of("2")));

        assertThat(throttler.isRateLimitResponse(429, noHeaders)).isTrue();
        assertThat(throttler.isRateLimitResponse(503, retryAfter)).isTrue();
        assertThat(throttler.isRateLimitResponse(503, noHeaders)).isFalse();
        assertThat(throttler.isRateLimitResponse(500, retryAfter)).isFalse();
    }

    @Test
    void preservesInterruptStatusWhileWaiting() {
        IiifRemoteRequestThrottler interruptedThrottler = new IiifRemoteRequestThrottler(
                settingsService,
                now::get,
                millis -> {
                    throw new InterruptedException("test interruption");
                }
        );
        interruptedThrottler.deferAfterRateLimit(
                "https://images.example.org/a.jpg",
                headers(Map.of("Retry-After", List.of("2"))),
                0
        );

        try {
            assertThatThrownBy(() ->
                    interruptedThrottler.awaitPreviewRequestSlot("https://images.example.org/manifest.json"))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("Interrupted while waiting");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private HttpHeaders headers(Map<String, List<String>> values) {
        return HttpHeaders.of(values, (_name, _value) -> true);
    }
}
