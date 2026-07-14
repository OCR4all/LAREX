package de.uniwue.zpd.dachs.larex.backend.exception;

public class AnnotationAlreadyExistsException extends RuntimeException {

    private final String xmlId;

    public AnnotationAlreadyExistsException(String xmlId) {
        super("A PAGE XML annotation already exists for this page");
        this.xmlId = xmlId;
    }

    public String getXmlId() {
        return xmlId;
    }
}
