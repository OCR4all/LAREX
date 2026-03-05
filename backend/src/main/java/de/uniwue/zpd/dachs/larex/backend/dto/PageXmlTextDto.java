package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTOs for raw PAGE XML read/validate/save endpoints.
 */
public class PageXmlTextDto {

    public record ValidateRequest(
            @NotBlank(message = "xml is required")
            String xml
    ) {}

    public record SaveRequest(
            @NotBlank(message = "xml is required")
            String xml,
            String comment
    ) {}

    public record XmlValidationError(
            @NotNull
            Integer line,
            @NotNull
            Integer column,
            @NotBlank
            String severity,
            @NotBlank
            String code,
            @NotBlank
            String message
    ) {}

    public record XmlValidationResult(
            boolean valid,
            List<XmlValidationError> errors,
            String pageVersion,
            String namespace
    ) {}

    public record XmlTextResponse(
            String xmlId,
            String schema,
            String xml,
            XmlValidationResult validation
    ) {}
}
