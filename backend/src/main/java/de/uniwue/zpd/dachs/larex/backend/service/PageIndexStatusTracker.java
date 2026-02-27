package de.uniwue.zpd.dachs.larex.backend.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PageIndexStatusTracker {

    private final Set<String> indexingPageIds = ConcurrentHashMap.newKeySet();

    public boolean markIndexingIfAbsent(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return false;
        }
        return indexingPageIds.add(pageId);
    }

    public void clearIndexing(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return;
        }
        indexingPageIds.remove(pageId);
    }

    public boolean isIndexing(String pageId) {
        return pageId != null && indexingPageIds.contains(pageId);
    }

    public Set<String> filterIndexing(Collection<String> pageIds) {
        if (pageIds == null || pageIds.isEmpty()) {
            return Set.of();
        }

        Set<String> result = new HashSet<>();
        for (String pageId : pageIds) {
            if (pageId != null && indexingPageIds.contains(pageId)) {
                result.add(pageId);
            }
        }
        return result;
    }
}
