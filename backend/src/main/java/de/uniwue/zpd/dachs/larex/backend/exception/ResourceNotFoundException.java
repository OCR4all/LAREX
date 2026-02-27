package de.uniwue.zpd.dachs.larex.backend.exception;

/**
 * Custom exception for resource not found scenarios
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String resourceType, String identifier) {
        super(String.format("%s with identifier '%s' not found", resourceType, identifier));
    }
    
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}