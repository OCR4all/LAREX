-- Existing explicit activations remain valid only within the administrator's scope.
-- Global Actions receive no assignments: each workspace must manually enable them.
DELETE FROM action_processor_assignments a
USING action_processor_definitions d
WHERE a.processor_definition_id = d.id
  AND d.global_available = false
  AND NOT EXISTS (
      SELECT 1 FROM action_processor_workspace_availability w
      WHERE w.processor_definition_id = a.processor_definition_id
        AND w.workspace_id = a.workspace_id
        AND w.enabled = true
  );
