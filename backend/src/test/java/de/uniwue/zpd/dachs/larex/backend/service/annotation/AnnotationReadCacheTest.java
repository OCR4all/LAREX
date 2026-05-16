package de.uniwue.zpd.dachs.larex.backend.service.annotation;

import de.uniwue.zpd.dachs.larex.backend.config.AnnotationProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnnotationReadCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void getIfFresh_returnsCachedDtoWhenFingerprintMatches() throws Exception {
        AnnotationReadCache cache = new AnnotationReadCache(properties(10, 10));
        Path xmlPath = tempDir.resolve("page.xml");
        Files.writeString(xmlPath, "<PcGts/>");

        PageDto pageDto = pageDto();
        cache.put("xml-1", xmlPath, pageDto);

        assertNotNull(cache.getIfFresh("xml-1", xmlPath));
    }

    @Test
    void getIfFresh_returnsNullWhenFileFingerprintChanges() throws Exception {
        AnnotationReadCache cache = new AnnotationReadCache(properties(10, 10));
        Path xmlPath = tempDir.resolve("page.xml");
        Files.writeString(xmlPath, "<PcGts/>");

        cache.put("xml-1", xmlPath, pageDto());
        Files.writeString(xmlPath, "<PcGts><Page imageFilename=\"updated.png\"/></PcGts>");

        assertNull(cache.getIfFresh("xml-1", xmlPath));
    }

    private AnnotationProperties properties(long maximumSize, long expireAfterAccessMinutes) {
        AnnotationProperties properties = new AnnotationProperties();
        AnnotationProperties.ReadCacheProperties readCache = new AnnotationProperties.ReadCacheProperties();
        readCache.setMaximumSize(maximumSize);
        readCache.setExpireAfterAccessMinutes(expireAfterAccessMinutes);
        properties.setReadCache(readCache);
        return properties;
    }

    private PageDto pageDto() {
        return new PageDto(
                "img.png",
                1000,
                1500,
                null,
                null,
                null,
                null,
                "pcgts-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
