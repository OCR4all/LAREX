package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.dto.TaskDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskStatusTransactionService {

    private final TaskService taskService;

    public TaskStatusTransactionService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(String taskId, String userId, Task.TaskStatus status) {
        taskService.updateTaskStatus(taskId, userId, new TaskDto.UpdateStatusRequest(status));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteTask(String taskId, String userId) {
        taskService.deleteTask(taskId, userId);
    }
}
