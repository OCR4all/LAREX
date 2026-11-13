package de.uniwue.zpd.dachs.larex.backend.service.task;

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
    public void setArchived(String taskId, String userId, boolean archived) {
        taskService.setArchived(taskId, userId, archived);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteTask(String taskId, String userId) {
        taskService.deleteTask(taskId, userId);
    }
}
