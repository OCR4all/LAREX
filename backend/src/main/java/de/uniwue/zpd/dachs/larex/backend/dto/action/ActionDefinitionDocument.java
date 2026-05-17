package de.uniwue.zpd.dachs.larex.backend.dto.action;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ActionDefinitionDocument(
        Integer version,
        String id,
        String name,
        String description,
        String category,
        List<String> targets,
        Endpoint endpoint,
        Access access,
        Locking locking,
        Inputs inputs,
        Outputs outputs,
        Concurrency concurrency,
        Runtime runtime,
        Map<String, Parameter> parameters
) {
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Endpoint(String url, Integer timeoutSeconds, String healthUrl, EndpointAuth auth) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record EndpointAuth(String type, String secretRef) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Access(String execute) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Locking(String mode) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Inputs(Boolean images, Boolean xml) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Outputs(OutputTarget xml, ImageOutputTarget images, StructuredOutputTarget text, StructuredOutputTarget layout) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record OutputTarget(Boolean enabled, String mode) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ImageOutputTarget(Boolean enabled, String variant, String mode) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record StructuredOutputTarget(Boolean enabled, String mode) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Concurrency(Integer maxActiveRuns, String scope) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Runtime(Model model) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Model(String name, Boolean optional) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Parameter(String type,
                            @JsonProperty("default") Object defaultValue,
                            Double min,
                            Double max,
                            String description,
                            Boolean required) {}
}
