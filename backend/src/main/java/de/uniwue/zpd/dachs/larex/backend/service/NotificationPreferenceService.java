package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import de.uniwue.zpd.dachs.larex.backend.entity.NotificationPreference;
import de.uniwue.zpd.dachs.larex.backend.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Get all notification preferences for a user.
     * Returns preferences for all notification types, creating defaults where needed.
     */
    public Map<Notification.NotificationType, NotificationPreference> getUserPreferences(String userId) {
        List<NotificationPreference> existingPreferences = preferenceRepository.findByUserId(userId);
        
        Map<Notification.NotificationType, NotificationPreference> preferencesMap = new HashMap<>();
        
        // Add existing preferences
        for (NotificationPreference pref : existingPreferences) {
            preferencesMap.put(pref.getNotificationType(), pref);
        }
        
        // Create default preferences for missing types (not persisted until explicitly saved)
        for (Notification.NotificationType type : Notification.NotificationType.values()) {
            if (!preferencesMap.containsKey(type)) {
                NotificationPreference defaultPref = new NotificationPreference(userId, type);
                // Defaults: email=false, desktop=false, inApp=true
                preferencesMap.put(type, defaultPref);
            }
        }
        
        return preferencesMap;
    }

    /**
     * Get or create a preference for a specific notification type.
     */
    public NotificationPreference getOrCreatePreference(String userId, Notification.NotificationType type) {
        return preferenceRepository.findByUserIdAndNotificationType(userId, type)
                .orElseGet(() -> {
                    NotificationPreference pref = new NotificationPreference(userId, type);
                    return preferenceRepository.save(pref);
                });
    }

    /**
     * Update notification preference for a specific type.
     */
    public NotificationPreference updatePreference(String userId, Notification.NotificationType type,
                                                    Boolean emailEnabled, Boolean desktopEnabled, Boolean inAppEnabled) {
        NotificationPreference pref = getOrCreatePreference(userId, type);
        
        if (emailEnabled != null) {
            pref.setEmailEnabled(emailEnabled);
        }
        if (desktopEnabled != null) {
            pref.setDesktopEnabled(desktopEnabled);
        }
        if (inAppEnabled != null) {
            pref.setInAppEnabled(inAppEnabled);
        }
        
        return preferenceRepository.save(pref);
    }

    /**
     * Bulk update multiple preferences at once.
     */
    public List<NotificationPreference> updatePreferences(String userId, List<NotificationPreference> preferences) {
        for (NotificationPreference incoming : preferences) {
            updatePreference(
                userId, 
                incoming.getNotificationType(),
                incoming.isEmailEnabled(),
                incoming.isDesktopEnabled(),
                incoming.isInAppEnabled()
            );
        }
        return preferenceRepository.findByUserId(userId);
    }

    /**
     * Check if email notifications are enabled for a specific type.
     */
    public boolean isEmailEnabledForType(String userId, Notification.NotificationType type) {
        Optional<NotificationPreference> pref = preferenceRepository.findByUserIdAndNotificationType(userId, type);
        return pref.map(NotificationPreference::isEmailEnabled).orElse(false);
    }

    /**
     * Check if desktop notifications are enabled for a specific type.
     */
    public boolean isDesktopEnabledForType(String userId, Notification.NotificationType type) {
        Optional<NotificationPreference> pref = preferenceRepository.findByUserIdAndNotificationType(userId, type);
        return pref.map(NotificationPreference::isDesktopEnabled).orElse(false);
    }

    /**
     * Check if in-app notifications are enabled for a specific type.
     */
    public boolean isInAppEnabledForType(String userId, Notification.NotificationType type) {
        Optional<NotificationPreference> pref = preferenceRepository.findByUserIdAndNotificationType(userId, type);
        // Default to true if no preference exists
        return pref.map(NotificationPreference::isInAppEnabled).orElse(true);
    }
}
