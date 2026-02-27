package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}