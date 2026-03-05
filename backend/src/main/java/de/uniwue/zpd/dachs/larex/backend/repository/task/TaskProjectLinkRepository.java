package de.uniwue.zpd.dachs.larex.backend.repository.task;

import de.uniwue.zpd.dachs.larex.backend.entity.TaskProjectLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskProjectLinkRepository extends JpaRepository<TaskProjectLink, String> {

    List<TaskProjectLink> findByTaskId(String taskId);

    List<TaskProjectLink> findByProjectId(String projectId);

    Optional<TaskProjectLink> findByTaskIdAndProjectId(String taskId, String projectId);

    boolean existsByTaskIdAndProjectId(String taskId, String projectId);

    void deleteByTaskId(String taskId);

    void deleteByProjectId(String projectId);
}
