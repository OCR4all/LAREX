package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.annotation")
public class AnnotationProperties {

    @Valid
    private ReadCacheProperties readCache = new ReadCacheProperties();

    @Valid
    private ExecutorPoolProperties postSave = new ExecutorPoolProperties(1, 2, 200);

    public ReadCacheProperties getReadCache() {
        return readCache;
    }

    public void setReadCache(ReadCacheProperties readCache) {
        this.readCache = readCache;
    }

    public ExecutorPoolProperties getPostSave() {
        return postSave;
    }

    public void setPostSave(ExecutorPoolProperties postSave) {
        this.postSave = postSave;
    }

    public static class ReadCacheProperties {

        @Min(1)
        private long maximumSize = 250;

        @Min(1)
        private long expireAfterAccessMinutes = 10;

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public long getExpireAfterAccessMinutes() {
            return expireAfterAccessMinutes;
        }

        public void setExpireAfterAccessMinutes(long expireAfterAccessMinutes) {
            this.expireAfterAccessMinutes = expireAfterAccessMinutes;
        }
    }
}
