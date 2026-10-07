package de.uniwue.zpd.dachs.larex.backend.exception;

public class LabelSetNameConflictException extends RuntimeException {
    public LabelSetNameConflictException(String name) {
        super("A label set named '" + name + "' already exists in the selected workspace. Choose another name.");
    }
}
