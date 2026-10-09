package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorAssignment;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorAssignmentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionDefinitionService;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionWorkspaceActivationService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ActionWorkspaceActivationIntegrationTest {
    @Autowired ActionDefinitionService definitions;
    @Autowired ActionRunService actions;
    @Autowired ActionWorkspaceActivationService activation;
    @Autowired ActionProcessorDefinitionRepository definitionRepository;
    @Autowired ActionProcessorAssignmentRepository assignmentRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GlobalAdminService globalAdmin;
    @MockitoBean WorkspaceAccessService access;

    @BeforeEach
    void authorize() {
        when(globalAdmin.isGlobalAdmin()).thenReturn(true);
        when(access.canManageProjects(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void globalActionsRequireManualActivationAndDisabledAssignmentsDoNotExecute() {
        ActionProcessorDefinition definition = definition(true);
        String workspace = UUID.randomUUID().toString();
        assertThat(activation.isActivated(definition, workspace, null)).isFalse();
        ActionDto.AssignmentResponse enabled = actions.assignProcessor(workspace,
                new ActionDto.AssignmentRequest(definition.getId(), null, true), "curator");
        assertThat(activation.isActivated(definition, workspace, "any-project")).isTrue();
        ActionDto.AssignmentResponse disabled = actions.assignProcessor(workspace,
                new ActionDto.AssignmentRequest(definition.getId(), null, false), "curator");
        assertThat(disabled.id()).isEqualTo(enabled.id());
        assertThat(activation.isActivated(definition, workspace, null)).isFalse();
        actions.assignProcessor(workspace, new ActionDto.AssignmentRequest(definition.getId(), null, true), "admin");
        assertThat(activation.isActivated(definition, workspace, null)).isTrue();
        actions.unassignProcessor(workspace, enabled.id(), "curator");
        assertThat(activation.isActivated(definition, workspace, null)).isFalse();
    }

    @Test
    void editorsCannotEnableOrDisableActions() {
        ActionProcessorDefinition definition = definition(true);
        doThrow(new SecurityException("Project management access required"))
                .when(access).requireManageProjectsAccess("workspace", "editor");
        assertThatThrownBy(() -> actions.assignProcessor("workspace",
                new ActionDto.AssignmentRequest(definition.getId(), null, true), "editor"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> actions.unassignProcessor("workspace", "assignment", "editor"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void projectAssignmentsApplyOnlyToTheirProject() {
        ActionProcessorDefinition definition = definition(true);
        String workspace = UUID.randomUUID().toString();
        assignment(definition, workspace, "project-a", true);
        assertThat(activation.isActivated(definition, workspace, "project-a")).isTrue();
        assertThat(activation.isActivated(definition, workspace, "project-b")).isFalse();
        assertThat(activation.isActivated(definition, workspace, null)).isFalse();
    }

    @Test
    void scopeChangesRemoveAllExcludedAssignmentsAndRestoringAccessDoesNotReactivate() {
        ActionProcessorDefinition definition = definition(true);
        String allowed = UUID.randomUUID().toString();
        String excluded = UUID.randomUUID().toString();
        definitions.assignWorkspaceAvailability(definition.getId(), new ActionDto.WorkspaceAvailabilityRequest(allowed, true), "admin");
        assignment(definition, allowed, null, true);
        assignment(definition, excluded, null, true);
        assignment(definition, excluded, "project", false);

        definitions.setGlobalAvailable(definition.getId(), false, "admin");
        assertThat(assignmentRepository.findByDefinitionIds(List.of(definition.getId())))
                .extracting(ActionProcessorAssignment::getWorkspaceId).containsExactly(allowed);
        assertThat(actions.listAvailableDefinitions(excluded, "curator")).isEmpty();
        assertThatThrownBy(() -> actions.assignProcessor(excluded,
                new ActionDto.AssignmentRequest(definition.getId(), null, true), "curator"))
                .isInstanceOf(SecurityException.class);

        definitions.assignWorkspaceAvailability(definition.getId(), new ActionDto.WorkspaceAvailabilityRequest(excluded, true), "admin");
        assertThat(assignmentRepository.findByWorkspaceIdOrderByCreatedAsc(excluded)).isEmpty();
        actions.assignProcessor(excluded, new ActionDto.AssignmentRequest(definition.getId(), null, true), "curator");
        definitions.assignWorkspaceAvailability(definition.getId(), new ActionDto.WorkspaceAvailabilityRequest(excluded, false), "admin");
        assertThat(assignmentRepository.findByWorkspaceIdOrderByCreatedAsc(excluded)).isEmpty();

        String availabilityId = definitions.listWorkspaceAvailability(definition.getId()).stream()
                .filter(record -> record.workspaceId().equals(allowed)).findFirst().orElseThrow().id();
        definitions.removeWorkspaceAvailability(definition.getId(), availabilityId);
        assertThat(assignmentRepository.findByDefinitionIds(List.of(definition.getId()))).isEmpty();
        definitions.setGlobalAvailable(definition.getId(), true, "admin");
        assertThat(assignmentRepository.findByDefinitionIds(List.of(definition.getId()))).isEmpty();
    }

    @Test
    void removingWorkspaceAvailabilityDoesNotRevokeGloballyEligibleAssignments() {
        ActionProcessorDefinition definition = definition(true);
        String workspace = UUID.randomUUID().toString();
        String availabilityId = definitions.assignWorkspaceAvailability(definition.getId(),
                new ActionDto.WorkspaceAvailabilityRequest(workspace, true), "admin").id();
        actions.assignProcessor(workspace, new ActionDto.AssignmentRequest(definition.getId(), null, true), "curator");
        definitions.removeWorkspaceAvailability(definition.getId(), availabilityId);
        assertThat(activation.isActivated(definition, workspace, null)).isTrue();
    }

    @Test
    void migrationRemovesOnlyIneligibleScopedAssignmentsWithoutBackfillingGlobals() {
        ActionProcessorDefinition scoped = definition(false);
        ActionProcessorDefinition global = definition(true);
        ActionProcessorDefinition unassignedGlobal = definition(true);
        String eligible = UUID.randomUUID().toString();
        definitions.assignWorkspaceAvailability(scoped.getId(), new ActionDto.WorkspaceAvailabilityRequest(eligible, true), "admin");
        ActionProcessorAssignment retained = assignment(scoped, eligible, null, false);
        ActionProcessorAssignment removed = assignment(scoped, "removed", "project", true);
        ActionProcessorAssignment retainedGlobal = assignment(global, "global-workspace", null, true);

        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V33__remove_ineligible_action_assignments.sql"))
                .execute(jdbc.getDataSource());
        assertThat(assignmentRepository.existsById(retained.getId())).isTrue();
        assertThat(assignmentRepository.findById(retained.getId()).orElseThrow().isEnabled()).isFalse();
        assertThat(assignmentRepository.existsById(removed.getId())).isFalse();
        assertThat(assignmentRepository.existsById(retainedGlobal.getId())).isTrue();
        assertThat(assignmentRepository.findByDefinitionIds(List.of(unassignedGlobal.getId()))).isEmpty();
    }

    @Test
    void concurrentActivationWaitsForScopeRevocationAndThenFails() throws Exception {
        ActionProcessorDefinition definition = definition(true);
        String workspace = UUID.randomUUID().toString();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch activationStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var revoke = executor.submit(() -> transaction.executeWithoutResult(status -> {
                definitionRepository.findByIdForUpdate(definition.getId()).orElseThrow();
                definitions.setGlobalAvailable(definition.getId(), false, "admin");
                lockAcquired.countDown();
                await(releaseLock);
            }));
            try {
                assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();
                var enable = executor.submit(() -> {
                    activationStarted.countDown();
                    return actions.assignProcessor(workspace, new ActionDto.AssignmentRequest(definition.getId(), null, true), "curator");
                });
                assertThat(activationStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> enable.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                releaseLock.countDown();
                revoke.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> enable.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(SecurityException.class);
            } finally {
                releaseLock.countDown();
            }
        }
        assertThat(assignmentRepository.findByDefinitionIds(List.of(definition.getId()))).isEmpty();
    }

    @Test
    void activationCommittedBeforeScopeRevocationIsRemoved() throws Exception {
        ActionProcessorDefinition definition = definition(true);
        String workspace = UUID.randomUUID().toString();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch activated = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch revocationStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var enable = executor.submit(() -> transaction.executeWithoutResult(status -> {
                actions.assignProcessor(workspace, new ActionDto.AssignmentRequest(definition.getId(), null, true), "curator");
                activated.countDown();
                await(releaseLock);
            }));
            try {
                assertThat(activated.await(10, TimeUnit.SECONDS)).isTrue();
                var revoke = executor.submit(() -> {
                    revocationStarted.countDown();
                    return definitions.setGlobalAvailable(definition.getId(), false, "admin");
                });
                assertThat(revocationStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> revoke.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                releaseLock.countDown();
                enable.get(10, TimeUnit.SECONDS);
                revoke.get(10, TimeUnit.SECONDS);
            } finally {
                releaseLock.countDown();
            }
        }
        assertThat(assignmentRepository.findByDefinitionIds(List.of(definition.getId()))).isEmpty();
    }

    @Test
    void systemDefinitionScopeUpdateAlsoReconcilesAssignments() {
        ActionProcessorDefinition definition = definition(true);
        assignment(definition, UUID.randomUUID().toString(), null, true);
        String yaml = """
                version: 1
                id: %s
                name: System processor
                tags: []
                targets: [PAGE]
                endpoint:
                  url: http://localhost:8081/dispatch
                access:
                  execute: CURATOR
                locking:
                  mode: PAGES
                inputs:
                  images: false
                  xml: true
                outputs:
                  xml:
                    enabled: true
                """.formatted(definition.getProcessorKey());
        definitions.upsertSystemDefinition(definition.getProcessorKey(), yaml, true, false, "system");
        assertThat(assignmentRepository.findByDefinitionIds(List.of(definition.getId()))).isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Lock release timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private ActionProcessorDefinition definition(boolean global) {
        ActionProcessorDefinition definition = new ActionProcessorDefinition();
        definition.setProcessorKey("activation-" + UUID.randomUUID());
        definition.setName(definition.getProcessorKey());
        definition.setYamlSource("");
        definition.setParsedJson("{}");
        definition.setEndpointUrl("http://processor/dispatch");
        definition.setCreatedByUserId("admin");
        definition.setUpdatedByUserId("admin");
        definition.setGlobalAvailable(global);
        return definitionRepository.saveAndFlush(definition);
    }

    private ActionProcessorAssignment assignment(ActionProcessorDefinition definition, String workspace, String project, boolean enabled) {
        ActionProcessorAssignment assignment = new ActionProcessorAssignment();
        assignment.setProcessorDefinition(definition);
        assignment.setWorkspaceId(workspace);
        assignment.setProjectId(project);
        assignment.setEnabled(enabled);
        assignment.setCreatedByUserId("curator");
        return assignmentRepository.saveAndFlush(assignment);
    }
}
