import { describe, expect, it } from 'vitest'
import type { ActionExecuteRole } from '~/types/action'
import type { ActionWorkflowStep } from '~/types/action-workflow'
import { describeWorkflowRole, workflowCuratorSteps, workflowExecuteRole, workflowStepRoles } from '../action-workflow'

const step = (processorDefinitionId: string): ActionWorkflowStep => ({ processorDefinitionId, parameters: {}, imageVariantSelection: null })
const processor = (id: string, executeRole: ActionExecuteRole) => ({ id, executeRole })
const editor = processor('editor', 'EDITOR')
const curator = processor('curator', 'CURATOR')

describe('workflow execute roles', () => {
  it('reports the role of every step, repeating processors and marking unknown ones', () => {
    const steps = ['editor', 'curator', 'editor', 'gone'].map(step)
    expect(workflowStepRoles(steps, [editor, curator])).toEqual(['EDITOR', 'CURATOR', 'EDITOR', null])
    expect(workflowCuratorSteps(steps, [editor, curator])).toEqual([2])
  })

  it('needs a curator as soon as one step does, even when the other steps are open to editors', () => {
    expect(workflowExecuteRole(['editor', 'curator', 'editor'].map(step), [editor, curator])).toBe('CURATOR')
    expect(workflowExecuteRole(['editor', 'editor'].map(step), [editor, curator])).toBe('EDITOR')
  })

  it('does not guess when a step role is unknown unless another step already needs a curator', () => {
    expect(workflowExecuteRole(['editor', 'gone'].map(step), [editor])).toBeNull()
    expect(workflowExecuteRole(['gone', 'curator'].map(step), [curator])).toBe('CURATOR')
    expect(workflowExecuteRole([], [editor])).toBeNull()
  })

  it('explains which steps decide who may run the workflow', () => {
    expect(describeWorkflowRole([], 3)).toBe('Editors and curators can run every step.')
    expect(describeWorkflowRole([2], 3)).toBe('Step 2 requires curator access, so only curators can run this workflow. The other steps are open to editors.')
    expect(describeWorkflowRole([1, 3], 4)).toContain('Steps 1 and 3 require curator access')
    expect(describeWorkflowRole([1, 3, 4], 5)).toContain('Steps 1, 3 and 4 require curator access')
    expect(describeWorkflowRole([1, 2], 2)).toBe('Every step requires curator access, so only curators can run this workflow.')
  })
})
