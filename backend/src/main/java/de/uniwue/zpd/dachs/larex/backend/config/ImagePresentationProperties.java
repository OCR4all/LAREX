package de.uniwue.zpd.dachs.larex.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties("larex.image-presentation")
public record ImagePresentationProperties(
        @DefaultValue("8") int queueCapacity,
        @DefaultValue("PT45S") Duration requestTimeout,
        @DefaultValue("PT30S") Duration decodeTimeout,
        @DefaultValue("384") int workerHeapMib,
        @DefaultValue("500000000") long maxSourcePixels,
        @DefaultValue("100000") int maxSourceDimension,
        @DefaultValue("536870912") long maxSourceBytes,
        @DefaultValue("268435456") long maxDiskCacheBytes,
        @DefaultValue("P7D") Duration diskRetention,
        Path cacheDirectory) {
    public ImagePresentationProperties {
        if (queueCapacity < 1 || requestTimeout.isNegative() || requestTimeout.isZero()
                || decodeTimeout.isNegative() || decodeTimeout.isZero() || workerHeapMib < 64
                || maxSourcePixels < 1 || maxSourceDimension < 1 || maxSourceBytes < 1
                || maxDiskCacheBytes < 1 || diskRetention.isNegative()) {
            throw new IllegalArgumentException("Invalid image presentation resource limits");
        }
    }
    public static ImagePresentationProperties defaults() {
        return new ImagePresentationProperties(8, Duration.ofSeconds(45), Duration.ofSeconds(30), 384,
                500_000_000, 100_000, 536_870_912, 268_435_456, Duration.ofDays(7), null);
    }
}
