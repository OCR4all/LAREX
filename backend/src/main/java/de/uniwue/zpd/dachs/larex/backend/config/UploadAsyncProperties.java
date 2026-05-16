package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.upload")
public class UploadAsyncProperties {

    @Valid
    private ExecutorPoolProperties async = new ExecutorPoolProperties(2, 5, 100);

    @Valid
    private ExecutorPoolProperties indexAsync = new ExecutorPoolProperties(1, 2, 200);

    public ExecutorPoolProperties getAsync() {
        return async;
    }

    public void setAsync(ExecutorPoolProperties async) {
        this.async = async;
    }

    public ExecutorPoolProperties getIndexAsync() {
        return indexAsync;
    }

    public void setIndexAsync(ExecutorPoolProperties indexAsync) {
        this.indexAsync = indexAsync;
    }
}
