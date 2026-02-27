package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import de.uniwue.zpd.dachs.larex.backend.entity.NotificationPreference;
import de.uniwue.zpd.dachs.larex.backend.service.NotificationPreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notifications/preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    /**
     * DTO for notification preference responses.
     */
    public record PreferenceDto(
        String notificationType,
        boolean emailEnabled,
        boolean desktopEnabled,
        boolean inAppEnabled
    ) {
        public static PreferenceDto from(NotificationPreference pref) {
            return new PreferenceDto(
                pref.getNotificationType().name(),
                pref.isEmailEnabled(),
                pref.isDesktopEnabled(),
                pref.isInAppEnabled()
            );
        }
    }

    /**
     * DTO for updating a single preference.
     */
    public record UpdatePreferenceRequest(
        String notificationType,
        Boolean emailEnabled,
        Boolean desktopEnabled,
        Boolean inAppEnabled
    ) {}

    /**
     * DTO for bulk updating preferences.
     */
    public record BulkUpdateRequest(
        List<UpdatePreferenceRequest> preferences
    ) {}

    /**
     * Get all notification preferences for the current user.
     */
    @GetMapping
    public ResponseEntity<List<PreferenceDto>> getPreferences(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        Map<Notification.NotificationType, NotificationPreference> preferences = preferenceService.getUserPreferences(userId);
        
        List<PreferenceDto> dtos = preferences.values().stream()
            .map(PreferenceDto::from)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get preference for a specific notification type.
     */
    @GetMapping("/{type}")
    public ResponseEntity<PreferenceDto> getPreference(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String type) {
        String userId = jwt.getSubject();
        
        try {
            Notification.NotificationType notificationType = Notification.NotificationType.valueOf(type);
            NotificationPreference pref = preferenceService.getOrCreatePreference(userId, notificationType);
            return ResponseEntity.ok(PreferenceDto.from(pref));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update preference for a specific notification type.
     */
    @PutMapping("/{type}")
    public ResponseEntity<PreferenceDto> updatePreference(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String type,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        String userId = jwt.getSubject();

        try {
            Notification.NotificationType notificationType = Notification.NotificationType.valueOf(type);
            NotificationPreference updated = preferenceService.updatePreference(
                userId, 
                notificationType,
                request.emailEnabled(),
                request.desktopEnabled(),
                request.inAppEnabled()
            );
            return ResponseEntity.ok(PreferenceDto.from(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Bulk update multiple preferences at once.
     */
    @PutMapping
    public ResponseEntity<List<PreferenceDto>> updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BulkUpdateRequest request) {
        String userId = jwt.getSubject();
        
        for (UpdatePreferenceRequest prefRequest : request.preferences()) {
            try {
                Notification.NotificationType notificationType = Notification.NotificationType.valueOf(prefRequest.notificationType());
                preferenceService.updatePreference(
                    userId,
                    notificationType,
                    prefRequest.emailEnabled(),
                    prefRequest.desktopEnabled(),
                    prefRequest.inAppEnabled()
                );
            } catch (IllegalArgumentException e) {
                // Skip invalid notification types
            }
        }
        
        // Return all preferences after update
        Map<Notification.NotificationType, NotificationPreference> preferences = preferenceService.getUserPreferences(userId);
        List<PreferenceDto> dtos = preferences.values().stream()
            .map(PreferenceDto::from)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get list of all available notification types.
     */
    @GetMapping("/types")
    public ResponseEntity<List<NotificationTypeInfo>> getNotificationTypes() {
        List<NotificationTypeInfo> types = List.of(
            new NotificationTypeInfo("WORKSPACE_INVITATION", "Workspace Invitations", "Notifications when you are invited to join a workspace"),
            new NotificationTypeInfo("TASK_ASSIGNED", "Task Assignments", "Notifications when a task is assigned to you"),
            new NotificationTypeInfo("TASK_COMPLETED", "Task Completions", "Notifications when tasks you created are completed"),
            new NotificationTypeInfo("PROJECT_CREATED", "Project Created", "Notifications when new projects are created in your workspaces"),
            new NotificationTypeInfo("PROJECT_DELETED", "Project Deleted", "Notifications when projects are deleted from your workspaces"),
            new NotificationTypeInfo("PAGE_CREATED", "Page Created", "Notifications when new pages are added to projects"),
            new NotificationTypeInfo("PAGE_DELETED", "Page Deleted", "Notifications when pages are removed from projects"),
            new NotificationTypeInfo("WORKSPACE_WATCH", "Workspace Updates", "Notifications for workspaces you are watching"),
            new NotificationTypeInfo("PROJECT_WATCH", "Project Updates", "Notifications for projects you are watching")
        );
        return ResponseEntity.ok(types);
    }

    public record NotificationTypeInfo(String type, String label, String description) {}
}
