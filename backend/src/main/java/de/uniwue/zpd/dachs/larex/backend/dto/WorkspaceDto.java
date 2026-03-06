package de.uniwue.zpd.dachs.larex.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class WorkspaceDto {

    /**
     * Base response DTO using polymorphic serialization
     */
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = PersonalWorkspaceResponse.class, name = "personal"),
            @JsonSubTypes.Type(value = TeamWorkspaceResponse.class, name = "team")
    })
    public abstract static class Response {
        private final String id;
        private final String name;
        private final String description;
        private final String avatar;
        private final LocalDateTime created;
        private final LocalDateTime updated;
        private final boolean isPersonal;
        private final String ownerUserId;
        private final String codecId;
        private final String labelSetId;
        private final String tagSetId;
        private final Integer defaultGtIndex;
        private final List<Integer> defaultRecognitionIndices;
        private final AuthorizationCapabilitiesDto.WorkspaceCapabilities capabilities;

        protected Response(String id, String name, String description, String avatar,
                          LocalDateTime created, LocalDateTime updated, boolean isPersonal,
                          String ownerUserId, String codecId, String labelSetId, String tagSetId,
                          Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                          AuthorizationCapabilitiesDto.WorkspaceCapabilities capabilities) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.avatar = avatar;
            this.created = created;
            this.updated = updated;
            this.isPersonal = isPersonal;
            this.ownerUserId = ownerUserId;
            this.codecId = codecId;
            this.labelSetId = labelSetId;
            this.tagSetId = tagSetId;
            this.defaultGtIndex = defaultGtIndex;
            this.defaultRecognitionIndices = defaultRecognitionIndices;
            this.capabilities = capabilities;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getAvatar() { return avatar; }
        public LocalDateTime getCreated() { return created; }
        public LocalDateTime getUpdated() { return updated; }
        @JsonProperty("isPersonal")
        public boolean isPersonal() { return isPersonal; }
        public String getOwnerUserId() { return ownerUserId; }
        public String getCodecId() { return codecId; }
        public String getLabelSetId() { return labelSetId; }
        public String getTagSetId() { return tagSetId; }
        public Integer getDefaultGtIndex() { return defaultGtIndex; }
        public List<Integer> getDefaultRecognitionIndices() { return defaultRecognitionIndices; }
        public AuthorizationCapabilitiesDto.WorkspaceCapabilities getCapabilities() { return capabilities; }
    }

    public static class PersonalWorkspaceResponse extends Response {
        public PersonalWorkspaceResponse(String id, String description, String avatar,
                                       LocalDateTime created, LocalDateTime updated,
                                       String ownerUserId, String codecId, String labelSetId, String tagSetId,
                                       Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                                       AuthorizationCapabilitiesDto.WorkspaceCapabilities capabilities) {
            super(id, "Personal Workspace", description, avatar, created, updated, true, ownerUserId, codecId, labelSetId, tagSetId,
                    defaultGtIndex, defaultRecognitionIndices, capabilities);
        }
    }

    public static class TeamWorkspaceResponse extends Response {
        public TeamWorkspaceResponse(String id, String name, String description, String avatar,
                                   LocalDateTime created, LocalDateTime updated,
                                   String ownerUserId, String codecId, String labelSetId, String tagSetId,
                                   Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                                   AuthorizationCapabilitiesDto.WorkspaceCapabilities capabilities) {
            super(id, name, description, avatar, created, updated, false, ownerUserId, codecId, labelSetId, tagSetId,
                    defaultGtIndex, defaultRecognitionIndices, capabilities);
        }
    }

    // Request DTOs - backward compatible with existing API
    public record CreateRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,
            @Size(max = 1000, message = "Description must not exceed 1000 characters")
            String description
    ) {}

    // New separate request DTOs for clarity
    public record CreateTeamWorkspaceRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,
            @Size(max = 1000, message = "Description must not exceed 1000 characters")
            String description,
            List<InviteRequest> initialInvites
    ) {}

    public record UpdateRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,
            @Size(max = 1000, message = "Description must not exceed 1000 characters")
            String description,
            String avatar,
            String codecId,
            String labelSetId,
            String tagSetId,
            Integer defaultGtIndex,
            List<Integer> defaultRecognitionIndices
    ) {}

    public record UpdateTeamWorkspaceRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,
            @Size(max = 1000, message = "Description must not exceed 1000 characters")
            String description,
            String avatar,
            String codecId,
            String labelSetId,
            String tagSetId,
            Integer defaultGtIndex,
            List<Integer> defaultRecognitionIndices
    ) {}

    public record UpdatePersonalWorkspaceRequest(
            @Size(max = 1000, message = "Description must not exceed 1000 characters")
            String description,
            String avatar,
            String codecId,
            String labelSetId,
            String tagSetId,
            Integer defaultGtIndex,
            List<Integer> defaultRecognitionIndices
    ) {}

    // Invitation DTOs (only for team workspaces)
    public record InviteRequest(
            @NotBlank(message = "User ID is required")
            String userId,
            @NotBlank(message = "Role is required")
            String role
    ) {}

    public record UpdateMemberRoleRequest(
            @NotBlank(message = "Role is required")
            String role
    ) {}

    public record InvitationResponse(
            String id,
            String workspaceId,
            String workspaceName,
            String role,
            java.time.LocalDateTime invitedAt
    ) {}
}
