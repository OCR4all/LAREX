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
    public record Endpoint(String url,
                           Integer timeoutSeconds,
                           String healthUrl,
                           String preflightUrl,
                           String parameterValuesUrl,
                           EndpointAuth auth) {
        public Endpoint(String url, Integer timeoutSeconds, String healthUrl, String preflightUrl, EndpointAuth auth) {
            this(url, timeoutSeconds, healthUrl, preflightUrl, null, auth);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record EndpointAuth(String type, String secretRef) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Access(String execute) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Locking(String mode) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Inputs(Object images, Object xml) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Outputs(OutputTarget xml, ImageOutputTarget images, FileOutputTarget files) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record OutputTarget(Boolean enabled, String mode) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ImageOutputTarget(Boolean enabled, String variant, String mode) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record FileOutputTarget(Boolean enabled) {}

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
                            Boolean required,
                            AllowedValues allowedValues) {
        public Parameter(String type,
                         Object defaultValue,
                         Double min,
                         Double max,
                         String description,
                         Boolean required) {
            this(type, defaultValue, min, max, description, required, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AllowedValues(List<ParameterChoice> values, String provider) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ParameterChoice(Object value, String label) {}
}
