package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Status;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionOutputRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunPageResultRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionOutputService;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRealtimePublisher;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ActionRunCancellationIntegrationTest {
    @Autowired ActionRunService actions;
    @Autowired ActionOutputService outputService;
    @Autowired ActionOutputRepository outputs;
    @Autowired ActionProcessorDefinitionRepository definitions;
    @Autowired ActionRunRepository runs;
    @Autowired ActionRunPageResultRepository results;
    @Autowired LibraryRepository libraries;
    @Autowired ProjectRepository projects;
    @Autowired PageRepository pages;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GlobalAdminService globalAdmin;
    @MockitoBean WorkspaceAccessService access;
    @MockitoBean ActionRealtimePublisher realtime;
    @MockitoBean WorkspaceQuotaGuardService quota;
    @MockitoBean WorkspaceQuotaRefreshService quotaRefresh;
    @MockitoBean(name = "importTaskExecutor") TaskExecutor importExecutor;

    enum Route { WORKSPACE, PROJECT, ADMIN }

    @BeforeEach
    void authorize() {
        when(globalAdmin.isGlobalAdmin()).thenReturn(true);
    }

    @ParameterizedTest
    @EnumSource(value = Route.class, names = {"WORKSPACE", "PROJECT"})
    void cancellationWaitsForHeartbeatAndPreservesProgress(Route route) throws Exception {
        Fixture f = fixture(Status.RUNNING, ActionRun.Kind.PROCESSING);
        race(() -> heartbeat(f), () -> cancel(f, route));
        ActionRun saved = persisted(f);
        assertThat(saved.getStatus()).isEqualTo(Status.CANCEL_REQUESTED);
        assertThat(saved.isCancelRequested()).isTrue();
        assertThat(saved.getProgressPercent()).isEqualTo(42);
        assertThat(saved.getCancelRequestedAt()).isNotNull();
        assertThat(heartbeat(f).cancelRequested()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Route.class, names = {"WORKSPACE", "ADMIN"})
    void cancellationDoesNotOverwriteCommittedCompletion(Route route) throws Exception {
        Fixture f = fixture(Status.RUNNING, ActionRun.Kind.INTEGRATION);
        Object cancellation = race(() -> result(f, "completed"), () -> cancel(f, route));
        ActionRun saved = persisted(f);
        assertThat(saved.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(saved.isCancelRequested()).isFalse();
        assertThat(saved.getCancelRequestedAt()).isNull();
        assertThat(saved.getCompletedAt()).isNotNull();
        if (route == Route.ADMIN) {
            assertThat(((ActionDto.BulkCancelRunsResponse) cancellation).cancelledCount()).isZero();
        }
    }

    @Test
    void resultsCannotCompleteARunAfterCancellation() throws Exception {
        Fixture f = fixture(Status.RUNNING, ActionRun.Kind.INTEGRATION);
        race(() -> cancel(f, Route.WORKSPACE), () -> {
            assertThatThrownBy(() -> result(f, "completed")).isInstanceOf(SecurityException.class)
                    .hasMessageContaining("cancelled");
            return null;
        }, true);
        assertThat(persisted(f).getStatus()).isEqualTo(Status.CANCEL_REQUESTED);
    }

    @Test
    void concurrentCancellationPreservesOriginalDeadline() throws Exception {
        Fixture f = fixture(Status.RUNNING, ActionRun.Kind.PROCESSING);
        var requestedAt = new AtomicReference<LocalDateTime>();
        race(() -> {
            Object response = cancel(f, Route.WORKSPACE);
            runs.flush();
            requestedAt.set(jdbc.queryForObject("select cancel_requested_at from action_runs where id = ?",
                    LocalDateTime.class, f.id()));
            return response;
        }, () -> cancel(f, Route.PROJECT));
        assertThat(persisted(f).getCancelRequestedAt()).isEqualTo(requestedAt.get());
        assertThat(persisted(f).getStatus()).isEqualTo(Status.CANCEL_REQUESTED);
    }

    @Test
    void cancellationWaitsForIncrementalPageResult() throws Exception {
        Fixture f = fixture(Status.RUNNING, ActionRun.Kind.PROCESSING);
        race(() -> result(f, "running"), () -> cancel(f, Route.WORKSPACE));
        assertThat(results.countByRunId(f.id())).isEqualTo(1);
        assertThat(persisted(f).getProgressPercent()).isEqualTo(99);
        assertThat(persisted(f).getStatus()).isEqualTo(Status.CANCEL_REQUESTED);
        assertThat(pages.findById(f.page()).orElseThrow().isLocked()).isFalse();
    }

    @Test
    void bulkCancellationIncludesAllActiveRunsAndExcludesTerminalAndOtherDefinitions() throws Exception {
        Fixture active = fixture(Status.RUNNING, ActionRun.Kind.PROCESSING);
        Fixture queued = fixture(Status.QUEUED, ActionRun.Kind.PROCESSING);
        Fixture terminal = fixture(Status.COMPLETED, ActionRun.Kind.PROCESSING);
        Fixture other = fixture(Status.RUNNING, ActionRun.Kind.PROCESSING);
        jdbc.update("update action_runs set processor_definition_id = ? where id in (?, ?)",
                active.definition(), queued.id(), terminal.id());
        var response = (ActionDto.BulkCancelRunsResponse) race(() -> heartbeat(active), () -> cancel(active, Route.ADMIN));
        assertThat(response.cancelledCount()).isEqualTo(2);
        assertThat(persisted(active).getStatus()).isEqualTo(Status.CANCEL_REQUESTED);
        assertThat(persisted(queued).getStatus()).isEqualTo(Status.CANCELLED);
        assertThat(persisted(terminal).getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(persisted(other).getStatus()).isEqualTo(Status.RUNNING);
    }

    @Test
    void forceCancellationWaitsForHeartbeatAndCleansUpRun() throws Exception {
        Fixture f = fixture(Status.RUNNING, ActionRun.Kind.PROCESSING);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            ActionRun run = runs.findWithProcessorDefinitionById(f.id()).orElseThrow();
            run.getProcessorDefinition().setOutputsFiles(true);
            outputService.createDraft(run, projects.findById(f.project()).orElseThrow());
        });
        assertThat(outputs.findBySourceRunId(f.id())).isPresent();
        race(() -> heartbeat(f), () -> actions.cancelWorkspaceRun(f.workspace(), f.id(), "owner", true));
        ActionRun saved = persisted(f);
        assertThat(saved.getStatus()).isEqualTo(Status.CANCELLED);
        assertThat(saved.isCancelRequested()).isTrue();
        assertThat(saved.getProgressPercent()).isEqualTo(42);
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getSecretExpiresAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(outputs.findBySourceRunId(f.id())).isEmpty();
        assertThat(pages.findById(f.page()).orElseThrow().isLocked()).isFalse();
        assertThat(projects.findById(f.project()).orElseThrow().isLocked()).isFalse();
        assertThatThrownBy(() -> heartbeat(f)).isInstanceOf(SecurityException.class);
    }

    private Object cancel(Fixture f, Route route) {
        return switch (route) {
            case ADMIN -> actions.cancelActiveAdminRuns(f.definition(), "owner");
            case PROJECT -> actions.cancelRun(f.workspace(), f.project(), f.id(), "owner", false);
            case WORKSPACE -> actions.cancelWorkspaceRun(f.workspace(), f.id(), "owner", false);
        };
    }

    private ActionDto.HeartbeatResponse heartbeat(Fixture f) {
        return actions.heartbeat(f.id(), "Bearer " + f.secret(),
                new ActionDto.HeartbeatRequest(42, "Working", null, null, null, null));
    }

    private Object result(Fixture f, String status) {
        try {
            return actions.receiveResults(f.id(), "Bearer " + f.secret(),
                    new ActionDto.ResultManifest(1, status, "Processor result", status.equals("running") ? f.page() : null,
                            List.of(), List.of()), new LinkedMultiValueMap<>());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Both operations use the real service and commit in separate database transactions. */
    private Object race(Supplier<?> first, Supplier<?> second) throws Exception {
        return race(first, second, false);
    }

    private Object race(Supplier<?> first, Supplier<?> second, boolean rejectionExpected) throws Exception {
        var writerReady = new CountDownLatch(1);
        var releaseWriter = new CountDownLatch(1);
        var writerFinished = new CountDownLatch(1);
        var contenderPublished = new CountDownLatch(1);
        var contenderThread = new AtomicReference<Thread>();
        var contenderPid = new CompletableFuture<Integer>();
        doAnswer(call -> {
            if (Thread.currentThread() == contenderThread.get()) {
                contenderPublished.countDown();
                await(writerFinished);
            }
            return null;
        }).when(realtime).publishRunUpdated(any());
        var transaction = new TransactionTemplate(transactions);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> {
                try {
                    return transaction.execute(status -> {
                        Object response = first.get();
                        writerReady.countDown();
                        await(releaseWriter);
                        return response;
                    });
                } finally {
                    writerFinished.countDown();
                }
            });
            try {
                await(writerReady);
                var contender = executor.submit(() -> transaction.execute(status -> {
                    contenderThread.set(Thread.currentThread());
                    contenderPid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    Object response = second.get();
                    if (rejectionExpected) {
                        // The service marks its joined transaction rollback-only on rejection.
                        // Roll back explicitly after asserting that rejection inside the callback.
                        assertThat(status.isRollbackOnly()).isTrue();
                        status.setRollbackOnly();
                    } else {
                        assertThat(status.isRollbackOnly()).isFalse();
                    }
                    return response;
                }));
                int pid = contenderPid.get(10, TimeUnit.SECONDS);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                // An unlocked implementation can reach publication with a stale entity. A fixed
                // implementation must wait on PostgreSQL's lock before reading/mutating the run.
                while (contenderPublished.getCount() != 0 && !Boolean.TRUE.equals(jdbc.queryForObject(
                        "select coalesce(bool_or(wait_event_type = 'Lock'), false) from pg_stat_activity where pid = ?",
                        Boolean.class, pid))) {
                    assertThat(contender.isDone()).as("contender must overlap the uncommitted writer").isFalse();
                    assertThat(System.nanoTime()).as("contender must read or wait for the row lock").isLessThan(deadline);
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                }
                releaseWriter.countDown();
                writer.get(10, TimeUnit.SECONDS);
                return contender.get(10, TimeUnit.SECONDS);
            } finally {
                releaseWriter.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("transaction interleaving checkpoint").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private ActionRun persisted(Fixture f) {
        return runs.findById(f.id()).orElseThrow();
    }

    private Fixture fixture(Status status, ActionRun.Kind kind) throws Exception {
        String workspace = UUID.randomUUID().toString();
        Library library = libraries.saveAndFlush(new Library(workspace, "Cancellation tests"));
        Project project = projects.saveAndFlush(new Project("Project", null, library));
        Page page = pages.saveAndFlush(new Page("Page", null, project));
        var definition = new ActionProcessorDefinition();
        definition.setProcessorKey("cancel-" + UUID.randomUUID());
        definition.setName("Cancellation test");
        definition.setYamlSource("");
        definition.setParsedJson("{}");
        definition.setEndpointUrl("http://processor/dispatch");
        definition.setCreatedByUserId("owner");
        definition.setUpdatedByUserId("owner");
        definition = definitions.saveAndFlush(definition);
        String secret = UUID.randomUUID().toString();
        var run = new ActionRun();
        run.setProcessorDefinition(definition);
        run.setWorkspaceId(workspace);
        run.setKind(kind);
        run.setProjectId(project.getId());
        run.setCreatedByUserId("owner");
        run.setPageIdsJson("[\"" + page.getId() + "\"]");
        run.setStatus(status);
        run.setSecretHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8))));
        run.setSecretPrefix("cancel-test");
        run.setSecretExpiresAt(LocalDateTime.now().plusMinutes(10));
        if (status.isTerminal()) run.setCompletedAt(LocalDateTime.now().minusMinutes(1));
        String id = runs.saveAndFlush(run).getId();
        if (!status.isTerminal() && kind == ActionRun.Kind.PROCESSING) {
            project.setLocked(true);
            project.setLockedByActionRunId(id);
            projects.saveAndFlush(project);
            page.setLocked(true);
            page.setLockedByActionRunId(id);
            pages.saveAndFlush(page);
        }
        return new Fixture(workspace, project.getId(), page.getId(), definition.getId(), id, secret);
    }

    private record Fixture(String workspace, String project, String page, String definition, String id, String secret) {}
}
