package de.uniwue.zpd.dachs.larex.backend.repository.notification;

import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    
    List<Notification> findByUserIdOrderByCreatedDesc(String userId);
    
    List<Notification> findByUserIdAndReadOrderByCreatedDesc(String userId, boolean read);
    
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.read = false")
    long countUnreadByUserId(@Param("userId") String userId);
    
    List<Notification> findByUserIdAndTypeOrderByCreatedDesc(String userId, Notification.NotificationType type);
    
    List<Notification> findByRelatedEntityIdAndRelatedEntityType(String relatedEntityId, String relatedEntityType);

    void deleteByUserIdAndRead(String userId, boolean read);

    @Modifying
    @Query("""
        UPDATE Notification n
        SET n.read = true,
            n.readAt = :readAt
        WHERE n.userId = :userId
          AND n.read = false
        """)
    int markAllAsReadByUserId(
            @Param("userId") String userId,
            @Param("readAt") LocalDateTime readAt
    );

    @Modifying
    @Query("""
        UPDATE Notification n
        SET n.read = true,
            n.readAt = :readAt
        WHERE n.id = :notificationId
          AND n.userId = :userId
          AND n.read = false
        """)
    int markAsReadByIdAndUserId(
            @Param("notificationId") String notificationId,
            @Param("userId") String userId,
            @Param("readAt") LocalDateTime readAt
    );
}
