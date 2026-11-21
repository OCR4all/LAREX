package de.uniwue.zpd.dachs.larex.backend.exception;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;

public class InvalidPageXmlException extends RuntimeException {
    private final PageXmlTextDto.XmlValidationResult validation;

    public InvalidPageXmlException(PageXmlTextDto.XmlValidationResult validation) {
        super(message(validation));
        this.validation = validation;
    }

    public boolean isAlto() {
        return isAlto(validation);
    }

    private static boolean isAlto(PageXmlTextDto.XmlValidationResult validation) {
        return XmlSchema.ALTO_XML.getNamespace().equals(validation.namespace());
    }

    private static String message(PageXmlTextDto.XmlValidationResult validation) {
        if (isAlto(validation)) {
            return "Invalid ALTO XML: " + validation.errors().stream()
                    .limit(5)
                    .map(PageXmlTextDto.XmlValidationError::message)
                    .collect(java.util.stream.Collectors.joining("; "));
        }
        return "Invalid PAGE XML: " + validation.errors().stream()
                .limit(5)
                .map(error -> "line " + error.line() + ", column " + error.column() + ": " + error.message())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    public PageXmlTextDto.XmlValidationResult getValidation() {
        return validation;
    }
}
