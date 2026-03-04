package de.uniwue.zpd.dachs.larex.backend.service.annotation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.uniwue.zpd.dachs.larex.backend.dto.page.PageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;

@Service
public class AnnotationReadCache {

    private final Cache<String, CacheEntry> cache;

    public AnnotationReadCache(
            @Value("${larex.annotation.read-cache.maximum-size:250}") long maximumSize,
            @Value("${larex.annotation.read-cache.expire-after-access-minutes:10}") long expireAfterAccessMinutes) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterAccess(Duration.ofMinutes(expireAfterAccessMinutes))
                .build();
    }

    public PageDto getIfFresh(String xmlId, Path path) throws IOException {
        if (xmlId == null || path == null) {
            return null;
        }

        CacheEntry entry = cache.getIfPresent(xmlId);
        if (entry == null) {
            return null;
        }

        FileFingerprint current = fingerprint(path);
        return entry.matches(current) ? entry.pageDto() : null;
    }

    public void put(String xmlId, Path path, PageDto pageDto) throws IOException {
        if (xmlId == null || path == null || pageDto == null) {
            return;
        }
        cache.put(xmlId, new CacheEntry(fingerprint(path), pageDto));
    }

    public void evict(String xmlId) {
        if (xmlId != null) {
            cache.invalidate(xmlId);
        }
    }

    private FileFingerprint fingerprint(Path path) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        return new FileFingerprint(path.toAbsolutePath().normalize().toString(), attrs.lastModifiedTime().toMillis(), attrs.size());
    }

    private record CacheEntry(FileFingerprint fingerprint, PageDto pageDto) {
        private boolean matches(FileFingerprint other) {
            return fingerprint.equals(other);
        }
    }

    private record FileFingerprint(String path, long lastModifiedMillis, long size) {}
}
