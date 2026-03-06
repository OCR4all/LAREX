package de.uniwue.zpd.dachs.larex.backend.dto;

public class AuthorizationCapabilitiesDto {

    public record WorkspaceCapabilities(
            boolean canAdminWorkspace,
            boolean canManageMembers,
            boolean canEditWorkspace,
            boolean canEditWorkspaceTextIndexDefaults,
            boolean canManageProjects,
            boolean canManageTasks,
            boolean canManageUtilities,
            boolean canSetPresets
    ) {}

    public record ProjectCapabilities(
            boolean canEdit,
            boolean canShare,
            boolean canDelete,
            boolean canDeletePages,
            boolean canUpload,
            boolean canExportPackage
    ) {}

    public record TaskCapabilities(
            boolean canEdit,
            boolean canDelete,
            boolean canAssignOthers,
            boolean canUpdateStatus
    ) {}

    public record ResourceCapabilities(
            boolean canEdit,
            boolean canDelete
    ) {}
}
