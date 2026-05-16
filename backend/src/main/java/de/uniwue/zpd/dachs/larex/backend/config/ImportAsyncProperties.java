package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.import")
public class ImportAsyncProperties {

    @Valid
    private ExecutorPoolProperties async = new ExecutorPoolProperties(1, 2, 10);

    public ExecutorPoolProperties getAsync() {
        return async;
    }

    public void setAsync(ExecutorPoolProperties async) {
        this.async = async;
    }
}
