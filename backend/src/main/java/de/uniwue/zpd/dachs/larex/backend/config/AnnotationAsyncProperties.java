package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.annotation")
public class AnnotationAsyncProperties {

    @Valid
    private ExecutorPoolProperties postSave = new ExecutorPoolProperties(1, 2, 200);

    public ExecutorPoolProperties getPostSave() {
        return postSave;
    }

    public void setPostSave(ExecutorPoolProperties postSave) {
        this.postSave = postSave;
    }
}
