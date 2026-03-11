package de.uniwue.zpd.dachs.larex.backend.repository.task;

import de.uniwue.zpd.dachs.larex.backend.entity.Subtask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface SubtaskRepository extends JpaRepository<Subtask, String> {

    List<Subtask> findByTaskIdOrderBySortOrderAsc(String taskId);

    List<Subtask> findByTaskIdAndIdIn(String taskId, Collection<String> subtaskIds);

    @Query("SELECT COALESCE(MAX(s.sortOrder), -1) + 1 FROM Subtask s WHERE s.taskId = :taskId")
    int getNextSortOrder(@Param("taskId") String taskId);

    @Query("SELECT COUNT(s) FROM Subtask s WHERE s.taskId = :taskId")
    long countByTaskId(@Param("taskId") String taskId);

    @Query("SELECT COUNT(s) FROM Subtask s WHERE s.taskId = :taskId AND s.completed = true")
    long countCompletedByTaskId(@Param("taskId") String taskId);

    void deleteByTaskId(String taskId);

    List<Subtask> findByPageId(String pageId);

    @Query("SELECT s FROM Subtask s WHERE s.pageId IN :pageIds AND s.assignedUserId = :userId AND s.completed = false")
    List<Subtask> findOpenByPageIdsAndAssignedUserId(@Param("pageIds") List<String> pageIds, @Param("userId") String userId);

    @Query("SELECT s.pageId, COUNT(s) FROM Subtask s WHERE s.pageId IN :pageIds AND s.assignedUserId = :userId AND s.completed = false GROUP BY s.pageId")
    List<Object[]> countOpenByPageIdsAndAssignedUserId(@Param("pageIds") List<String> pageIds, @Param("userId") String userId);

    @Query("SELECT s FROM Subtask s WHERE s.pageId = :pageId AND s.assignedUserId = :userId AND s.completed = false")
    List<Subtask> findOpenByPageIdAndAssignedUserId(@Param("pageId") String pageId, @Param("userId") String userId);

    @Modifying
    @Query("""
        UPDATE Subtask s
        SET s.completed = true,
            s.completedAt = :completedAt,
            s.completedByUserId = :completedByUserId
        WHERE s.taskId = :taskId
          AND s.id IN :subtaskIds
          AND s.completed = false
        """)
    int markCompletedByTaskIdAndIdIn(
            @Param("taskId") String taskId,
            @Param("subtaskIds") Collection<String> subtaskIds,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("completedByUserId") String completedByUserId
    );

    @Modifying
    @Query("DELETE FROM Subtask s WHERE s.taskId = :taskId AND s.id IN :subtaskIds")
    int deleteByTaskIdAndIdIn(
            @Param("taskId") String taskId,
            @Param("subtaskIds") Collection<String> subtaskIds
    );

    @Modifying
    @Query("""
        UPDATE Subtask s
        SET s.description = :description
        WHERE s.taskId = :taskId
          AND s.id IN :subtaskIds
        """)
    int updateDescriptionByTaskIdAndIdIn(
            @Param("taskId") String taskId,
            @Param("subtaskIds") Collection<String> subtaskIds,
            @Param("description") String description
    );

    @Modifying
    @Query("""
        UPDATE Subtask s
        SET s.assignedUserId = :assignedUserId
        WHERE s.taskId = :taskId
          AND s.id IN :subtaskIds
        """)
    int updateAssignedUserByTaskIdAndIdIn(
            @Param("taskId") String taskId,
            @Param("subtaskIds") Collection<String> subtaskIds,
            @Param("assignedUserId") String assignedUserId
    );
}
