package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorAssignment;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorWorkspaceAvailability;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorAssignmentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorWorkspaceAvailabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionWorkspaceActivationServiceTest {
    @Mock ActionProcessorAssignmentRepository assignments;
    @Mock ActionProcessorWorkspaceAvailabilityRepository availability;
    ActionWorkspaceActivationService service;
    ActionProcessorDefinition definition;

    @BeforeEach
    void setUp() {
        service = new ActionWorkspaceActivationService(availability, assignments);
        definition = new ActionProcessorDefinition();
        definition.setId("action");
        definition.setEnabled(true);
    }

    @ParameterizedTest
    @EnumSource(ActionProcessorDefinition.ActionKind.class)
    void globalAvailabilityDoesNotActivateAnyActionKind(ActionProcessorDefinition.ActionKind kind) {
        definition.setGlobalAvailable(true);
        definition.setActionKind(kind);
        assertThat(service.isEligible(definition, "workspace")).isTrue();
        assertThat(service.isActivated(definition, "workspace", null)).isFalse();
        assertThatThrownBy(() -> service.requireActivated(definition, "workspace", null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void oldAssignmentCannotGrantEligibilityAfterScopeRevocation() {
        assertThat(service.isEligible(definition, "workspace")).isFalse();
        assertThat(service.isActivated(definition, "workspace", "project")).isFalse();
        verifyNoInteractions(assignments);
    }

    @Test
    void explicitlyAssignedGlobalActionIsActivated() {
        definition.setGlobalAvailable(true);
        when(assignments.findExecutableAssignments("workspace", "project"))
                .thenReturn(List.of(assignment("workspace", "project")));
        assertThat(service.isActivated(definition, "workspace", "project")).isTrue();
        definition.setEnabled(false);
        assertThat(service.isActivated(definition, "workspace", "project")).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ActionProcessorDefinition.ActionKind.class, names = {"TRAINING", "EVALUATION"})
    void datasetActionsRequireWorkspaceActivation(ActionProcessorDefinition.ActionKind kind) {
        definition.setGlobalAvailable(true);
        definition.setActionKind(kind);
        assertThatThrownBy(() -> service.requireAssignable(definition, "workspace", "project"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("workspace");
        service.requireAssignable(definition, "workspace", null);
    }

    @Test
    void disabledDefinitionsCannotBeActivated() {
        definition.setGlobalAvailable(true);
        definition.setEnabled(false);
        assertThatThrownBy(() -> service.requireAssignable(definition, "workspace", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scopeNarrowingRemovesWorkspaceAndProjectAssignmentsButPreservesEligibleOnes() {
        ActionProcessorWorkspaceAvailability allowed = new ActionProcessorWorkspaceAvailability();
        allowed.setWorkspaceId("allowed");
        ActionProcessorWorkspaceAvailability disabled = new ActionProcessorWorkspaceAvailability();
        disabled.setWorkspaceId("disabled");
        disabled.setEnabled(false);
        ActionProcessorAssignment workspaceAssignment = assignment("removed", null);
        ActionProcessorAssignment projectAssignment = assignment("removed", "project");
        ActionProcessorAssignment disabledScopeAssignment = assignment("disabled", null);
        when(availability.findByProcessorDefinitionIdOrderByWorkspaceIdAsc("action"))
                .thenReturn(List.of(allowed, disabled));
        when(assignments.findByDefinitionIds(List.of("action")))
                .thenReturn(List.of(assignment("allowed", null), workspaceAssignment, projectAssignment, disabledScopeAssignment));

        service.reconcileAssignments(definition);
        verify(assignments).deleteAll(List.of(workspaceAssignment, projectAssignment, disabledScopeAssignment));
    }

    @Test
    void globalScopeExpansionDoesNotCreateOrRemoveAssignments() {
        definition.setGlobalAvailable(true);
        service.reconcileAssignments(definition);
        verifyNoInteractions(assignments, availability);
    }

    private ActionProcessorAssignment assignment(String workspaceId, String projectId) {
        ActionProcessorAssignment assignment = new ActionProcessorAssignment();
        assignment.setProcessorDefinition(definition);
        assignment.setWorkspaceId(workspaceId);
        assignment.setProjectId(projectId);
        return assignment;
    }
}
