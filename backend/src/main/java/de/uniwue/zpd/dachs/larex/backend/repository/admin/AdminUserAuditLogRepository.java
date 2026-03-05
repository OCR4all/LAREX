package de.uniwue.zpd.dachs.larex.backend.repository.admin;

import de.uniwue.zpd.dachs.larex.backend.entity.AdminUserAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminUserAuditLogRepository extends JpaRepository<AdminUserAuditLog, String> {
    List<AdminUserAuditLog> findByTargetUserIdOrderByCreatedDesc(String targetUserId, Pageable pageable);
}
