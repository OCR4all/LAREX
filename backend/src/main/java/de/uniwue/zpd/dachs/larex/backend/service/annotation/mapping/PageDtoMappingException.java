package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping;

public class PageDtoMappingException extends IllegalArgumentException {

    public PageDtoMappingException(String message) {
        super(message);
    }

    public PageDtoMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
