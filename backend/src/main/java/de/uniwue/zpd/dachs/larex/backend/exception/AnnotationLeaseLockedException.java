package de.uniwue.zpd.dachs.larex.backend.exception;

import de.uniwue.zpd.dachs.larex.backend.dto.AnnotationCollaborationDto;

public class AnnotationLeaseLockedException extends RuntimeException {

    private final AnnotationCollaborationDto.UserSummary owner;
    private final String reason;

    public AnnotationLeaseLockedException(String message,
                                          AnnotationCollaborationDto.UserSummary owner,
                                          String reason) {
        super(message);
        this.owner = owner;
        this.reason = reason;
    }

    public AnnotationCollaborationDto.UserSummary getOwner() {
        return owner;
    }

    public String getReason() {
        return reason;
    }
}
