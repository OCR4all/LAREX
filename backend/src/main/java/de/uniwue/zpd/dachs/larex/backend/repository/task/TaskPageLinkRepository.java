package de.uniwue.zpd.dachs.larex.backend.repository.task;

import de.uniwue.zpd.dachs.larex.backend.entity.TaskPageLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskPageLinkRepository extends JpaRepository<TaskPageLink, String> {

    List<TaskPageLink> findByTaskId(String taskId);

    List<TaskPageLink> findByPageId(String pageId);

    Optional<TaskPageLink> findByTaskIdAndPageId(String taskId, String pageId);

    List<TaskPageLink> findByTaskIdAndPageIdIn(String taskId, List<String> pageIds);

    boolean existsByTaskIdAndPageId(String taskId, String pageId);

    void deleteByTaskId(String taskId);

    void deleteByPageId(String pageId);

    @Query("SELECT tpl FROM TaskPageLink tpl JOIN Task t ON tpl.taskId = t.id " +
           "WHERE tpl.pageId = :pageId AND :userId MEMBER OF t.assignedUserIds")
    List<TaskPageLink> findByPageIdAndAssignedUserId(@Param("pageId") String pageId, @Param("userId") String userId);

    /**
     * Delete all task-page links for pages in a given list (used when deleting a project).
     */
    void deleteByPageIdIn(List<String> pageIds);
}
