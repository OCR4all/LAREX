package de.uniwue.zpd.dachs.larex.backend.exception;

public class ProjectNameConflictException extends RuntimeException {

    private final String projectName;
    private final String targetWorkspaceId;

    public ProjectNameConflictException(String projectName, String targetWorkspaceId) {
        super("A project with the name '" + projectName + "' already exists in the target workspace");
        this.projectName = projectName;
        this.targetWorkspaceId = targetWorkspaceId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getTargetWorkspaceId() {
        return targetWorkspaceId;
    }
}
