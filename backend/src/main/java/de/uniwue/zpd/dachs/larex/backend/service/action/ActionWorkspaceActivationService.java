package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorAssignment;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorAssignmentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorWorkspaceAvailabilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Availability permits activation; only an enabled assignment permits execution. */
@Service
@Transactional(readOnly = true)
public class ActionWorkspaceActivationService {
    private final ActionProcessorWorkspaceAvailabilityRepository availabilityRepository;
    private final ActionProcessorAssignmentRepository assignmentRepository;

    public ActionWorkspaceActivationService(ActionProcessorWorkspaceAvailabilityRepository availabilityRepository,
                                            ActionProcessorAssignmentRepository assignmentRepository) {
        this.availabilityRepository = availabilityRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public boolean isEligible(ActionProcessorDefinition definition, String workspaceId) {
        return definition.isGlobalAvailable()
                || availabilityRepository.existsByProcessorDefinitionIdAndWorkspaceIdAndEnabledTrue(definition.getId(), workspaceId);
    }

    public void requireAssignable(ActionProcessorDefinition definition, String workspaceId, String projectId) {
        if (!definition.isEnabled()) {
            throw new IllegalArgumentException("Action processor is disabled");
        }
        if (!isEligible(definition, workspaceId)) {
            throw new SecurityException("Action is not available to this workspace");
        }
        if (projectId != null && definition.getActionKind() != ActionProcessorDefinition.ActionKind.PROCESSING) {
            throw new IllegalArgumentException("Training and evaluation Actions must be enabled for the workspace");
        }
    }

    public List<ActionProcessorDefinition> listActivatedDefinitions(String workspaceId, String projectId) {
        return assignmentRepository.findExecutableAssignments(workspaceId, projectId).stream()
                .map(ActionProcessorAssignment::getProcessorDefinition)
                .filter(definition -> isEligible(definition, workspaceId))
                .distinct()
                .toList();
    }

    public boolean isActivated(ActionProcessorDefinition definition, String workspaceId, String projectId) {
        return definition.isEnabled() && isEligible(definition, workspaceId)
                && assignmentRepository.findExecutableAssignments(workspaceId, projectId).stream()
                .anyMatch(assignment -> assignment.getProcessorDefinition().getId().equals(definition.getId()));
    }

    public void requireActivated(ActionProcessorDefinition definition, String workspaceId, String projectId) {
        if (!isActivated(definition, workspaceId, projectId)) {
            throw new SecurityException("Action is not enabled for this workspace or project");
        }
    }

    /** Caller holds the definition write lock, also used by assignment creation. */
    @Transactional
    public void reconcileAssignments(ActionProcessorDefinition definition) {
        if (definition.isGlobalAvailable()) {
            return;
        }
        Set<String> eligibleWorkspaces = availabilityRepository
                .findByProcessorDefinitionIdOrderByWorkspaceIdAsc(definition.getId()).stream()
                .filter(availability -> availability.isEnabled())
                .map(availability -> availability.getWorkspaceId())
                .collect(Collectors.toSet());
        List<ActionProcessorAssignment> revoked = assignmentRepository.findByDefinitionIds(List.of(definition.getId())).stream()
                .filter(assignment -> !eligibleWorkspaces.contains(assignment.getWorkspaceId()))
                .toList();
        assignmentRepository.deleteAll(revoked);
    }
}
