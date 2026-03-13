package de.uniwue.zpd.dachs.larex.backend.service.page.indexing;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PageIndexStatusTracker {

    public enum AcquireResult {
        ACQUIRED,
        ACQUIRED_STALE_RECOVERY,
        ALREADY_ACTIVE,
        INVALID_PAGE_ID
    }

    private final Map<String, Long> indexingPageStartedAtMs = new ConcurrentHashMap<>();

    public AcquireResult acquireIndexingSlot(String pageId, long staleThresholdMs) {
        if (pageId == null || pageId.isBlank()) {
            return AcquireResult.INVALID_PAGE_ID;
        }

        long nowMs = System.currentTimeMillis();
        AtomicReference<AcquireResult> result = new AtomicReference<>(AcquireResult.INVALID_PAGE_ID);
        indexingPageStartedAtMs.compute(pageId, (ignored, existingStartedAtMs) -> {
            if (existingStartedAtMs == null) {
                result.set(AcquireResult.ACQUIRED);
                return nowMs;
            }

            boolean stale = staleThresholdMs >= 0 && (nowMs - existingStartedAtMs) >= staleThresholdMs;
            if (stale) {
                result.set(AcquireResult.ACQUIRED_STALE_RECOVERY);
                return nowMs;
            }

            result.set(AcquireResult.ALREADY_ACTIVE);
            return existingStartedAtMs;
        });

        return result.get();
    }

    public boolean markIndexingIfAbsent(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return false;
        }
        return indexingPageStartedAtMs.putIfAbsent(pageId, System.currentTimeMillis()) == null;
    }

    public void clearIndexing(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return;
        }
        indexingPageStartedAtMs.remove(pageId);
    }

    public boolean isIndexing(String pageId) {
        return pageId != null && indexingPageStartedAtMs.containsKey(pageId);
    }

    public Set<String> filterIndexing(Collection<String> pageIds) {
        if (pageIds == null || pageIds.isEmpty()) {
            return Set.of();
        }

        Set<String> result = new HashSet<>();
        for (String pageId : pageIds) {
            if (pageId != null && indexingPageStartedAtMs.containsKey(pageId)) {
                result.add(pageId);
            }
        }
        return result;
    }
}
