package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.TaskReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskReminderRepository extends JpaRepository<TaskReminder, String> {

    List<TaskReminder> findByTaskId(String taskId);

    List<TaskReminder> findByTaskIdAndUserId(String taskId, String userId);

    List<TaskReminder> findBySentFalseAndReminderTimeBefore(LocalDateTime time);

    void deleteByTaskId(String taskId);
}
