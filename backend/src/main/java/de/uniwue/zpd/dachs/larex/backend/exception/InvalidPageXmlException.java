package de.uniwue.zpd.dachs.larex.backend.exception;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;

import java.util.stream.Collectors;

public class InvalidPageXmlException extends RuntimeException {
    private final PageXmlTextDto.XmlValidationResult validation;

    public InvalidPageXmlException(PageXmlTextDto.XmlValidationResult validation) {
        super(message(validation));
        this.validation = validation;
    }

    public boolean isImportFormat() {
        return importFormat(validation) != null;
    }

    public String title() {
        XmlSchema format = importFormat(validation);
        return "Invalid " + (format == null ? "PAGE XML" : label(format));
    }

    private static XmlSchema importFormat(PageXmlTextDto.XmlValidationResult validation) {
        XmlSchema schema = XmlSchema.detectFromNamespace(validation.namespace());
        return schema == XmlSchema.PAGE_XML || schema == XmlSchema.UNKNOWN ? null : schema;
    }

    private static String label(XmlSchema format) {
        return format == XmlSchema.ALTO_XML ? "ALTO XML" : format.getDisplayName();
    }

    private static String message(PageXmlTextDto.XmlValidationResult validation) {
        XmlSchema format = importFormat(validation);
        if (format != null) {
            return "Invalid " + label(format) + ": " + validation.errors().stream()
                    .limit(5)
                    .map(PageXmlTextDto.XmlValidationError::message)
                    .collect(Collectors.joining("; "));
        }
        return "Invalid PAGE XML: " + validation.errors().stream()
                .limit(5)
                .map(error -> "line " + error.line() + ", column " + error.column() + ": " + error.message())
                .collect(Collectors.joining("; "));
    }

    public PageXmlTextDto.XmlValidationResult getValidation() {
        return validation;
    }
}
