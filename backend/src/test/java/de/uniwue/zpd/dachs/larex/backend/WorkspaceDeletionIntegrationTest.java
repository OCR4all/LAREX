package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.SearchLexiconEntry;
import de.uniwue.zpd.dachs.larex.backend.entity.Subtask;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.TaskPageLink;
import de.uniwue.zpd.dachs.larex.backend.entity.TaskProjectLink;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.search.SearchLexiconEntryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskProjectLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceDeletionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WorkspaceDeletionIntegrationTest {

    @Autowired private WorkspaceDeletionService workspaceDeletionService;
    @Autowired private TeamWorkspaceRepository teamWorkspaceRepository;
    @Autowired private LibraryRepository libraryRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private PageRepository pageRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private SubtaskRepository subtaskRepository;
    @Autowired private TaskPageLinkRepository taskPageLinkRepository;
    @Autowired private TaskProjectLinkRepository taskProjectLinkRepository;
    @Autowired private DatasetRepository datasetRepository;
    @Autowired private SearchLexiconEntryRepository lexiconRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void deletingTeamWorkspaceRemovesItsDataAndKeepsOtherWorkspaces() {
        String owner = "owner-" + UUID.randomUUID();
        Fixture deleted = fixture(owner);
        Fixture kept = fixture(owner);

        workspaceDeletionService.deleteTeamWorkspace(deleted.workspaceId());

        assertThat(teamWorkspaceRepository.existsById(deleted.workspaceId())).isFalse();
        assertThat(count("libraries", "workspace_id", deleted.workspaceId())).isZero();
        assertThat(count("projects", "id", deleted.projectId())).isZero();
        assertThat(count("pages", "id", deleted.pageId())).isZero();
        assertThat(count("tasks", "workspace_id", deleted.workspaceId())).isZero();
        assertThat(count("subtasks", "page_id", deleted.pageId())).isZero();
        assertThat(count("task_page_links", "page_id", deleted.pageId())).isZero();
        assertThat(count("task_project_links", "project_id", deleted.projectId())).isZero();
        assertThat(count("datasets", "workspace_id", deleted.workspaceId())).isZero();
        assertThat(count("search_lexicon_entries", "workspace_id", deleted.workspaceId())).isZero();
        assertThat(count("workspace_members", "workspace_id", deleted.workspaceId())).isZero();

        assertThat(teamWorkspaceRepository.existsById(kept.workspaceId())).isTrue();
        assertThat(count("projects", "id", kept.projectId())).isOne();
        assertThat(count("pages", "id", kept.pageId())).isOne();
        assertThat(count("subtasks", "page_id", kept.pageId())).isOne();
        assertThat(count("datasets", "workspace_id", kept.workspaceId())).isOne();
        assertThat(count("search_lexicon_entries", "workspace_id", kept.workspaceId())).isOne();
        assertThat(count("workspace_members", "workspace_id", kept.workspaceId())).isOne();
    }

    private Fixture fixture(String owner) {
        TeamWorkspace workspace = teamWorkspaceRepository.save(new TeamWorkspace("ws-" + UUID.randomUUID(), "", owner));
        Library library = libraryRepository.save(new Library(workspace.getId(), workspace.getName()));
        Project project = projectRepository.save(new Project("project", "", library));
        Page page = pageRepository.save(new Page("page", "", project));
        Task task = taskRepository.save(new Task("task", "", owner, Task.TaskPriority.MEDIUM, workspace.getId()));
        Subtask subtask = new Subtask(task.getId(), "subtask", 0);
        subtask.setPageId(page.getId());
        subtaskRepository.save(subtask);
        taskPageLinkRepository.save(new TaskPageLink(task.getId(), page.getId(), TaskPageLink.LinkType.MANUAL, owner));
        taskProjectLinkRepository.save(new TaskProjectLink(task.getId(), project.getId(), owner));
        Dataset dataset = new Dataset();
        dataset.setWorkspaceId(workspace.getId());
        dataset.setName("dataset");
        datasetRepository.save(dataset);
        lexiconRepository.save(new SearchLexiconEntry(workspace.getId(), project.getId(), "token", 1));
        memberRepository.save(new WorkspaceMember("member-" + UUID.randomUUID(), WorkspaceMember.Role.EDITOR,
                WorkspaceMember.InvitationStatus.ACCEPTED, workspace.getId()));
        return new Fixture(workspace.getId(), project.getId(), page.getId());
    }

    private int count(String table, String column, String value) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
        return count == null ? 0 : count;
    }

    private record Fixture(String workspaceId, String projectId, String pageId) {
    }
}
