package de.uniwue.zpd.dachs.larex.backend.repository.task;

import de.uniwue.zpd.dachs.larex.backend.entity.Subtask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubtaskRepository extends JpaRepository<Subtask, String> {

    List<Subtask> findByTaskIdOrderBySortOrderAsc(String taskId);

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
}
