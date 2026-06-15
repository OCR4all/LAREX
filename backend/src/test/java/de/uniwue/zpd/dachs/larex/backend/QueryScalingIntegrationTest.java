package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.BulkTaskDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectTransferRequest;
import de.uniwue.zpd.dachs.larex.backend.entity.ResourceTransferRequest;
import de.uniwue.zpd.dachs.larex.backend.entity.Subtask;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectTransferRequestRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ResourceTransferRequestRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.service.task.BulkTaskService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectReadService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectTransferService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ResourceTransferService;
import de.uniwue.zpd.dachs.larex.backend.service.search.SearchService;
import de.uniwue.zpd.dachs.larex.backend.service.task.SubtaskService;
import de.uniwue.zpd.dachs.larex.backend.service.task.TaskActivityService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadConflictService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class QueryScalingIntegrationTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private ProjectReadService projectReadService;

    @Autowired
    private ProjectTransferService projectTransferService;

    @Autowired
    private ResourceTransferService resourceTransferService;

    @Autowired
    private SubtaskService subtaskService;

    @Autowired
    private BulkTaskService bulkTaskService;

    @Autowired
    private UploadConflictService uploadConflictService;

    @Autowired
    private PersonalWorkspaceRepository personalWorkspaceRepository;

    @Autowired
    private TeamWorkspaceRepository teamWorkspaceRepository;

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private CodecRepository codecRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private PageImageRepository pageImageRepository;

    @Autowired
    private PageXmlRepository pageXmlRepository;

    @Autowired
    private ProjectTransferRequestRepository projectTransferRequestRepository;

    @Autowired
    private ResourceTransferRequestRepository resourceTransferRequestRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private SubtaskRepository subtaskRepository;

    @Autowired
    private UploadSessionRepository uploadSessionRepository;

    @Autowired
    private UploadSessionFileRepository uploadSessionFileRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TaskActivityService taskActivityService;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.clear();

        when(userService.getUsersByIds(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> ids = invocation.getArgument(0, List.class);
            Map<String, UserDto> users = new HashMap<>();
            for (String id : ids) {
                users.put(id, new UserDto(id, "user-" + id, id + "@example.test", "First", "Last", null));
            }
            return users;
        });
    }

    @Test
    void searchServiceGlobalSearchStatementCountStaysBounded() {
        String userId = "search-user";
        WorkspaceFixture fixture = createPersonalWorkspaceFixture(userId, "search");
        addProjectsWithPages(fixture.library(), 3, "needle", new AtomicInteger());

        long smallCount = measurePreparedStatements(() -> searchService.globalSearch("needle", 500, userId));

        addProjectsWithPages(fixture.library(), 30, "needle", new AtomicInteger(100));
        long largeCount = measurePreparedStatements(() -> searchService.globalSearch("needle", 500, userId));

        assertThat(largeCount).isLessThanOrEqualTo(smallCount + 2);
    }

    @Test
    void projectReadServiceListingStatementCountStaysBounded() {
        String userId = "project-read-user";
        WorkspaceFixture fixture = createPersonalWorkspaceFixture(userId, "read");
        addProjectsWithAssets(fixture.library(), 4, new AtomicInteger());

        long smallCount = measurePreparedStatements(() -> {
            List<Project> projects = projectRepository.findByLibraryWorkspaceId(fixture.workspaceId());
            projectReadService.toResponses(projects, userId);
        });

        addProjectsWithAssets(fixture.library(), 25, new AtomicInteger(100));
        long largeCount = measurePreparedStatements(() -> {
            List<Project> projects = projectRepository.findByLibraryWorkspaceId(fixture.workspaceId());
            projectReadService.toResponses(projects, userId);
        });

        assertThat(largeCount).isLessThanOrEqualTo(smallCount + 3);
    }

    @Test
    void projectTransferListingStatementCountStaysBounded() {
        WorkspaceFixture source = createTeamWorkspaceFixture("transfer-owner", "source");
        WorkspaceFixture target = createTeamWorkspaceFixture("transfer-owner", "target");
        AtomicInteger counter = new AtomicInteger();

        addProjectTransfers(source.library(), source.workspaceId(), target.workspaceId(), "requester", 3, counter);
        long smallCount = measurePreparedStatements(() -> {
            List<ProjectTransferRequest> requests = projectTransferRequestRepository.findByRequestedByUserId("requester");
            projectTransferService.toResponses(requests);
        });

        addProjectTransfers(source.library(), source.workspaceId(), target.workspaceId(), "requester", 25, counter);
        long largeCount = measurePreparedStatements(() -> {
            List<ProjectTransferRequest> requests = projectTransferRequestRepository.findByRequestedByUserId("requester");
            projectTransferService.toResponses(requests);
        });

        assertThat(largeCount).isLessThanOrEqualTo(smallCount + 2);
    }

    @Test
    void resourceTransferListingStatementCountStaysBounded() {
        WorkspaceFixture source = createTeamWorkspaceFixture("resource-owner", "resource-source");
        WorkspaceFixture target = createTeamWorkspaceFixture("resource-owner", "resource-target");
        AtomicInteger counter = new AtomicInteger();

        addResourceTransfers(source.library(), source.workspaceId(), target.workspaceId(), "resource-requester", 3, counter);
        long smallCount = measurePreparedStatements(() -> {
            List<ResourceTransferRequest> requests = resourceTransferRequestRepository.findByRequestedByUserId("resource-requester");
            resourceTransferService.toResponses(requests);
        });

        addResourceTransfers(source.library(), source.workspaceId(), target.workspaceId(), "resource-requester", 25, counter);
        long largeCount = measurePreparedStatements(() -> {
            List<ResourceTransferRequest> requests = resourceTransferRequestRepository.findByRequestedByUserId("resource-requester");
            resourceTransferService.toResponses(requests);
        });

        assertThat(largeCount).isLessThanOrEqualTo(smallCount + 2);
    }

    @Test
    void subtaskListingStatementCountStaysBounded() {
        String userId = "subtask-user";
        WorkspaceFixture fixture = createPersonalWorkspaceFixture(userId, "subtask");
        Project project = projectRepository.save(new Project("subtask-project", "desc", fixture.library()));
        List<Page> pages = addPages(project, 3, new AtomicInteger());

        Task task = new Task("task", "task-desc", userId, Task.TaskPriority.MEDIUM, fixture.workspaceId());
        task.setAssignedUserIds(List.of(userId));
        task = taskRepository.save(task);

        addSubtasks(task, pages, userId, 4, new AtomicInteger());
        List<String> pageIds = pages.stream().map(Page::getId).toList();

        long smallCount = measurePreparedStatements(() -> subtaskService.getOpenSubtasksForPages(pageIds, userId));

        addSubtasks(task, pages, userId, 35, new AtomicInteger(100));
        long largeCount = measurePreparedStatements(() -> subtaskService.getOpenSubtasksForPages(pageIds, userId));

        assertThat(largeCount).isLessThanOrEqualTo(smallCount + 2);
    }

    @Test
    void bulkTaskUpdateStatementCountStaysBoundedForMissingIds() {
        String userId = "bulk-user";
        WorkspaceFixture fixture = createPersonalWorkspaceFixture(userId, "bulk");

        List<String> smallIds = randomIds(8);
        long smallCount = measurePreparedStatements(() -> bulkTaskService.bulkUpdateStatus(
                fixture.workspaceId(),
                userId,
                new BulkTaskDto.BulkStatusRequest(smallIds, Task.TaskStatus.OPEN)
        ));

        List<String> largeIds = randomIds(120);
        long largeCount = measurePreparedStatements(() -> bulkTaskService.bulkUpdateStatus(
                fixture.workspaceId(),
                userId,
                new BulkTaskDto.BulkStatusRequest(largeIds, Task.TaskStatus.OPEN)
        ));

        assertThat(largeCount).isLessThanOrEqualTo(smallCount + 1);
    }

    @Test
    void uploadConflictRetrievalStatementCountStaysBounded() {
        String userId = "conflict-user";
        WorkspaceFixture fixture = createPersonalWorkspaceFixture(userId, "conflict");
        Project project = projectRepository.save(new Project("conflict-project", "desc", fixture.library()));
        AtomicInteger counter = new AtomicInteger();

        addConflictFiles(project, fixture.workspaceId(), userId, 4, counter);
        long smallCount = measurePreparedStatements(() -> uploadConflictService.getProjectConflicts(project.getId(), userId));

        addConflictFiles(project, fixture.workspaceId(), userId, 30, counter);
        long largeCount = measurePreparedStatements(() -> uploadConflictService.getProjectConflicts(project.getId(), userId));

        assertThat(largeCount).isLessThanOrEqualTo(smallCount + 2);
    }

    private long measurePreparedStatements(Runnable operation) {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        operation.run();

        entityManager.flush();
        entityManager.clear();
        return statistics.getPrepareStatementCount();
    }

    private WorkspaceFixture createPersonalWorkspaceFixture(String ownerUserId, String namePrefix) {
        PersonalWorkspace workspace = personalWorkspaceRepository.save(new PersonalWorkspace(ownerUserId));
        Library library = libraryRepository.save(new Library(workspace.getId(), namePrefix + "-library"));
        return new WorkspaceFixture(workspace.getId(), library);
    }

    private WorkspaceFixture createTeamWorkspaceFixture(String ownerUserId, String namePrefix) {
        TeamWorkspace workspace = teamWorkspaceRepository.save(new TeamWorkspace(
                namePrefix + "-" + UUID.randomUUID(),
                namePrefix + "-description",
                ownerUserId
        ));
        Library library = libraryRepository.save(new Library(workspace.getId(), workspace.getName()));
        return new WorkspaceFixture(workspace.getId(), library);
    }

    private void addProjectsWithPages(Library library, int count, String query, AtomicInteger counter) {
        for (int i = 0; i < count; i++) {
            int index = counter.incrementAndGet();
            Project project = projectRepository.save(new Project(
                    "project-" + query + "-" + index,
                    "description-" + query + "-" + index,
                    library
            ));
            pageRepository.save(new Page("page-" + query + "-" + index, "page-desc-" + index, project));
        }
    }

    private void addProjectsWithAssets(Library library, int count, AtomicInteger counter) {
        for (int i = 0; i < count; i++) {
            int index = counter.incrementAndGet();
            Project project = projectRepository.save(new Project("asset-project-" + index, "desc-" + index, library));
            Page page = pageRepository.save(new Page("asset-page-" + index, "page-desc-" + index, project));

            pageImageRepository.save(new PageImage(
                    "image-" + index + ".png",
                    "/tmp/image-" + index + ".png",
                    "image/png",
                    100L + index,
                    "original",
                    "asset-page-" + index,
                    page
            ));
            pageXmlRepository.save(new PageXml(
                    "xml-" + index + ".xml",
                    "/tmp/xml-" + index + ".xml",
                    "application/xml",
                    50L + index,
                    "original",
                    "asset-page-" + index,
                    XmlSchema.PAGE_XML,
                    "1.0",
                    page
            ));
        }
    }

    private void addProjectTransfers(
            Library sourceLibrary,
            String sourceWorkspaceId,
            String targetWorkspaceId,
            String requestedByUserId,
            int count,
            AtomicInteger counter
    ) {
        for (int i = 0; i < count; i++) {
            int index = counter.incrementAndGet();
            Project project = projectRepository.save(new Project(
                    "transfer-project-" + index,
                    "transfer-desc-" + index,
                    sourceLibrary
            ));
            ProjectTransferRequest request = new ProjectTransferRequest(
                    project.getId(),
                    sourceWorkspaceId,
                    targetWorkspaceId,
                    requestedByUserId,
                    "message-" + index,
                    ProjectTransferRequest.TransferType.COPY
            );
            projectTransferRequestRepository.save(request);
        }
    }

    private void addResourceTransfers(
            Library sourceLibrary,
            String sourceWorkspaceId,
            String targetWorkspaceId,
            String requestedByUserId,
            int count,
            AtomicInteger counter
    ) {
        for (int i = 0; i < count; i++) {
            int index = counter.incrementAndGet();
            Codec codec = new Codec("resource-codec-" + index, sourceLibrary);
            codec = codecRepository.save(codec);
            ResourceTransferRequest request = new ResourceTransferRequest(
                    codec.getId(),
                    ResourceTransferRequest.ResourceType.CODEC,
                    sourceWorkspaceId,
                    targetWorkspaceId,
                    requestedByUserId,
                    "resource-message-" + index,
                    ResourceTransferRequest.TransferType.COPY
            );
            resourceTransferRequestRepository.save(request);
        }
    }

    private List<Page> addPages(Project project, int count, AtomicInteger counter) {
        List<Page> pages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int index = counter.incrementAndGet();
            pages.add(pageRepository.save(new Page("subtask-page-" + index, "desc-" + index, project)));
        }
        return pages;
    }

    private void addSubtasks(Task task, List<Page> pages, String assignedUserId, int count, AtomicInteger counter) {
        for (int i = 0; i < count; i++) {
            int index = counter.incrementAndGet();
            Page page = pages.get(i % pages.size());
            Subtask subtask = new Subtask(task.getId(), "subtask-" + index, index);
            subtask.setPageId(page.getId());
            subtask.setAssignedUserId(assignedUserId);
            subtaskRepository.save(subtask);
        }
    }

    private void addConflictFiles(
            Project project,
            String workspaceId,
            String userId,
            int count,
            AtomicInteger counter
    ) {
        UploadSession session = new UploadSession(project.getId(), workspaceId, userId, count, count * 100L);
        session.setStatus(UploadSession.UploadSessionStatus.PROCESSING);
        session = uploadSessionRepository.save(session);

        for (int i = 0; i < count; i++) {
            int index = counter.incrementAndGet();
            String baseName = "conflict-page-" + index;
            pageRepository.save(new Page(baseName, "conflict-desc-" + index, project));

            UploadSessionFile file = new UploadSessionFile(
                    baseName + ".png",
                    123L,
                    "image/png",
                    baseName,
                    "original",
                    1
            );
            file.setSession(session);
            file.setStatus(UploadSessionFile.UploadFileStatus.CONFLICT);
            file.setConflictType("IMAGE_VARIANT_EXISTS");
            file.setTempFilePath("/tmp/" + baseName + ".png");
            uploadSessionFileRepository.save(file);
        }
    }

    private List<String> randomIds(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> UUID.randomUUID().toString())
                .toList();
    }

    private record WorkspaceFixture(String workspaceId, Library library) {
    }
}
