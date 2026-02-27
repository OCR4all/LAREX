package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    List<Task> findByWorkspaceId(String workspaceId);
    Page<Task> findByWorkspaceId(String workspaceId, Pageable pageable);

    List<Task> findByWorkspaceIdAndStatus(String workspaceId, Task.TaskStatus status);
    Page<Task> findByWorkspaceIdAndStatus(String workspaceId, Task.TaskStatus status, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.workspaceId = :workspaceId AND :userId MEMBER OF t.assignedUserIds")
    List<Task> findByWorkspaceIdAndAssignedUserId(@Param("workspaceId") String workspaceId, @Param("userId") String userId);

    @Query("SELECT t FROM Task t WHERE t.workspaceId = :workspaceId AND :userId MEMBER OF t.assignedUserIds")
    Page<Task> findByWorkspaceIdAndAssignedUserId(@Param("workspaceId") String workspaceId, @Param("userId") String userId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.workspaceId = :workspaceId AND :userId MEMBER OF t.assignedUserIds AND t.status = :status")
    List<Task> findByWorkspaceIdAndAssignedUserIdAndStatus(@Param("workspaceId") String workspaceId, @Param("userId") String userId, @Param("status") Task.TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.workspaceId = :workspaceId AND :userId MEMBER OF t.assignedUserIds AND t.status = :status")
    Page<Task> findByWorkspaceIdAndAssignedUserIdAndStatus(@Param("workspaceId") String workspaceId, @Param("userId") String userId, @Param("status") Task.TaskStatus status, Pageable pageable);
    
    List<Task> findByCreatedByUserId(String createdByUserId);
    
    @Query("SELECT t FROM Task t WHERE :userId MEMBER OF t.assignedUserIds")
    List<Task> findByAssignedUserId(@Param("userId") String userId);
    
    @Query("SELECT t FROM Task t WHERE :userId MEMBER OF t.assignedUserIds AND t.status = :status")
    List<Task> findByAssignedUserIdAndStatus(@Param("userId") String userId, @Param("status") Task.TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.dueDate IS NOT NULL AND t.dueDate BETWEEN :start AND :end " +
           "AND t.status NOT IN ('COMPLETED', 'CANCELLED')")
    List<Task> findTasksDueBetween(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("SELECT t FROM Task t WHERE t.dueDate IS NOT NULL AND t.dueDate < :now " +
           "AND t.status NOT IN ('COMPLETED', 'CANCELLED')")
    List<Task> findOverdueTasks(@Param("now") java.time.LocalDateTime now);
}
