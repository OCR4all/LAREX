import { describe, expect, it } from 'vitest'
import {
  activeActionDefinitionFilterCount,
  emptyActionDefinitionFilters,
  matchesActionDefinitionFilters,
  type ActionDefinitionFilters
} from '../action-definition-filters'

const action = { enabled: true, global: true, workflowId: null }
const disabledScopedWorkflow = { enabled: false, global: false, workflowId: 'workflow' }
const filters = (changes: Partial<ActionDefinitionFilters>): ActionDefinitionFilters => ({ ...emptyActionDefinitionFilters(), ...changes })

describe('Action definition filters', () => {
  it('keeps everything when no filter is set', () => {
    expect(matchesActionDefinitionFilters(action, emptyActionDefinitionFilters())).toBe(true)
    expect(matchesActionDefinitionFilters(disabledScopedWorkflow, emptyActionDefinitionFilters())).toBe(true)
    expect(activeActionDefinitionFilterCount(emptyActionDefinitionFilters())).toBe(0)
  })

  it('filters by enabled state', () => {
    expect(matchesActionDefinitionFilters(action, filters({ status: 'ENABLED' }))).toBe(true)
    expect(matchesActionDefinitionFilters(action, filters({ status: 'DISABLED' }))).toBe(false)
    expect(matchesActionDefinitionFilters(disabledScopedWorkflow, filters({ status: 'DISABLED' }))).toBe(true)
    expect(matchesActionDefinitionFilters(disabledScopedWorkflow, filters({ status: 'ENABLED' }))).toBe(false)
  })

  it('filters by global or scoped availability', () => {
    expect(matchesActionDefinitionFilters(action, filters({ scope: 'GLOBAL' }))).toBe(true)
    expect(matchesActionDefinitionFilters(action, filters({ scope: 'SCOPED' }))).toBe(false)
    expect(matchesActionDefinitionFilters(disabledScopedWorkflow, filters({ scope: 'SCOPED' }))).toBe(true)
  })

  it('filters workflows from plain Actions, treating a missing workflow id as an Action', () => {
    expect(matchesActionDefinitionFilters(action, filters({ kind: 'ACTION' }))).toBe(true)
    expect(matchesActionDefinitionFilters({ ...action, workflowId: undefined }, filters({ kind: 'ACTION' }))).toBe(true)
    expect(matchesActionDefinitionFilters(action, filters({ kind: 'WORKFLOW' }))).toBe(false)
    expect(matchesActionDefinitionFilters(disabledScopedWorkflow, filters({ kind: 'WORKFLOW' }))).toBe(true)
  })

  it('combines all filters and counts the active ones', () => {
    const combined = filters({ status: 'DISABLED', scope: 'SCOPED', kind: 'WORKFLOW' })
    expect(matchesActionDefinitionFilters(disabledScopedWorkflow, combined)).toBe(true)
    expect(matchesActionDefinitionFilters({ ...disabledScopedWorkflow, enabled: true }, combined)).toBe(false)
    expect(matchesActionDefinitionFilters({ ...disabledScopedWorkflow, global: true }, combined)).toBe(false)
    expect(matchesActionDefinitionFilters({ ...disabledScopedWorkflow, workflowId: null }, combined)).toBe(false)
    expect(activeActionDefinitionFilterCount(combined)).toBe(3)
    expect(activeActionDefinitionFilterCount(filters({ scope: 'GLOBAL' }))).toBe(1)
  })
})
