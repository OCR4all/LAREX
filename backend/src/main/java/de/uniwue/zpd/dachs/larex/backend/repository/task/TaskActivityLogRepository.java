package de.uniwue.zpd.dachs.larex.backend.repository.task;

import de.uniwue.zpd.dachs.larex.backend.entity.TaskActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskActivityLogRepository extends JpaRepository<TaskActivityLog, String> {

    List<TaskActivityLog> findByTaskIdOrderByCreatedDesc(String taskId);

    Page<TaskActivityLog> findByTaskIdOrderByCreatedDesc(String taskId, Pageable pageable);

    void deleteByTaskId(String taskId);
}
