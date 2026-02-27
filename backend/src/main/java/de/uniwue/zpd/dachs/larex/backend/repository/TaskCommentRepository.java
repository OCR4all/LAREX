package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, String> {

    List<TaskComment> findByTaskIdOrderByCreatedAsc(String taskId);

    List<TaskComment> findByTaskIdOrderByCreatedDesc(String taskId);

    long countByTaskId(String taskId);

    void deleteByTaskId(String taskId);
}
