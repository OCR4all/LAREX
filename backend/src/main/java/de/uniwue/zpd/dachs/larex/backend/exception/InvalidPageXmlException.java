package de.uniwue.zpd.dachs.larex.backend.exception;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;

public class InvalidPageXmlException extends RuntimeException {
    private final PageXmlTextDto.XmlValidationResult validation;

    public InvalidPageXmlException(PageXmlTextDto.XmlValidationResult validation) {
        super("Invalid PAGE XML: " + validation.errors().stream()
                .limit(5)
                .map(error -> "line " + error.line() + ", column " + error.column() + ": " + error.message())
                .collect(java.util.stream.Collectors.joining("; ")));
        this.validation = validation;
    }

    public PageXmlTextDto.XmlValidationResult getValidation() {
        return validation;
    }
}
