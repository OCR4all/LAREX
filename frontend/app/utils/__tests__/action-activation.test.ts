import { describe, expect, it } from 'vitest'
import { actionActivationOptions } from '@/utils/action-activation'
import type { ActionAssignmentResponse, ActionDefinitionResponse } from '@/types/action'

function definition(id: string, global = false, kind: ActionDefinitionResponse['kind'] = 'PROCESSING'): ActionDefinitionResponse {
  return {
    id, processorKey: id, name: id, description: null, yaml: '', endpointUrl: '', endpointTimeoutSeconds: 30,
    kind, executeRole: 'CURATOR', lockMode: 'NONE', category: 'WORKFLOW', targets: ['PAGE'],
    inputs: { images: { level: 'NONE', requiredForTargets: [] }, xml: { level: 'NONE', requiredForTargets: [] } },
    acceptsImages: false, acceptsXml: false, outputsImages: false, outputsXml: false, outputsFiles: false, overwrites: {},
    enabled: true, global, created: '', updated: '', trainingSplits: null, parameters: {}
  }
}

function assignment(processor: ActionDefinitionResponse, enabled: boolean): ActionAssignmentResponse {
  return { id: processor.id, workspaceId: 'workspace', projectId: null, enabled, processor }
}

describe('manual Action activation options', () => {
  it('allows both globally and workspace available Actions to be enabled', () => {
    const options = actionActivationOptions([definition('global', true), definition('scoped')], [], null)
    expect(options.map(option => option.value)).toEqual(['global', 'scoped'])
    expect(options.map(option => option.label)).toEqual(['global · Global availability', 'scoped · Workspace availability'])
  })

  it('allows disabled assignments to be re-enabled without duplicating enabled assignments', () => {
    const disabled = definition('disabled', true)
    const enabled = definition('enabled')
    expect(actionActivationOptions([disabled, enabled], [assignment(disabled, false), assignment(enabled, true)], null))
      .toEqual([{ label: 'disabled · Global availability', value: 'disabled' }])
  })

  it('offers dataset Actions only for workspace-wide activation', () => {
    const definitions = [definition('processing'), definition('training', true, 'TRAINING'), definition('evaluation', false, 'EVALUATION')]
    expect(actionActivationOptions(definitions, [], null).map(option => option.value))
      .toEqual(['processing', 'training', 'evaluation'])
    expect(actionActivationOptions(definitions, [], 'project').map(option => option.value)).toEqual(['processing'])
  })
})
