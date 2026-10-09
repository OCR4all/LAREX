package de.uniwue.zpd.dachs.larex.backend.exception;

public class PageMoveConflictException extends RuntimeException {
    public PageMoveConflictException(String message) {
        super(message);
    }
}
