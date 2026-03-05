package de.uniwue.zpd.dachs.larex.backend.repository.notification;

import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import de.uniwue.zpd.dachs.larex.backend.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {

    List<NotificationPreference> findByUserId(String userId);

    Optional<NotificationPreference> findByUserIdAndNotificationType(String userId, Notification.NotificationType notificationType);

    boolean existsByUserIdAndNotificationType(String userId, Notification.NotificationType notificationType);
}
