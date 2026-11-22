package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskProjectLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@Transactional
public class TaskLinkCleanupService {

    private final PageRepository pageRepository;
    private final TaskPageLinkRepository taskPageLinkRepository;
    private final TaskProjectLinkRepository taskProjectLinkRepository;
    private final SubtaskRepository subtaskRepository;
    private final TaskProgressService taskProgressService;

    public TaskLinkCleanupService(PageRepository pageRepository,
                                  TaskPageLinkRepository taskPageLinkRepository,
                                  TaskProjectLinkRepository taskProjectLinkRepository,
                                  SubtaskRepository subtaskRepository,
                                  TaskProgressService taskProgressService) {
        this.pageRepository = pageRepository;
        this.taskPageLinkRepository = taskPageLinkRepository;
        this.taskProjectLinkRepository = taskProjectLinkRepository;
        this.subtaskRepository = subtaskRepository;
        this.taskProgressService = taskProgressService;
    }

    public void removePages(Collection<String> pageIds, String actorUserId) {
        if (pageIds == null || pageIds.isEmpty()) {
            return;
        }
        List<String> ids = List.copyOf(pageIds);
        List<String> affectedTaskIds = subtaskRepository.findTaskIdsByPageIdIn(ids);
        taskPageLinkRepository.deleteByPageIdIn(ids);
        subtaskRepository.deleteByPageIdIn(ids);
        subtaskRepository.flush();
        taskProgressService.recompute(affectedTaskIds, actorUserId);
    }

    public void removeProject(String projectId, String actorUserId) {
        removePages(pageRepository.findIdsByProjectId(projectId), actorUserId);
        taskProjectLinkRepository.deleteByProjectId(projectId);
    }
}
