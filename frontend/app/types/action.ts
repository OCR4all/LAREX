export type ActionExecuteRole = 'EDITOR' | 'CURATOR'
export type ActionLockMode = 'PAGES' | 'PROJECT'
export type ActionRunStatus = 'PENDING' | 'DISPATCHING' | 'RUNNING' | 'IMPORTING_RESULTS' | 'COMPLETED' | 'FAILED' | 'CANCEL_REQUESTED' | 'CANCELLED'

export interface ActionValidationDiagnostic {
  severity: string
  path: string
  line: number | null
  column: number | null
  code?: string
  message: string
}

export interface ActionDefinitionPreview {
  processorKey: string
  name: string
  description: string | null
  endpointUrl: string
  endpointTimeoutSeconds: number
  executeRole: ActionExecuteRole
  lockMode: ActionLockMode
  acceptsImages: boolean
  acceptsXml: boolean
  outputsImages: boolean
  outputsXml: boolean
  parameters: Record<string, ActionParameterDefinition>
}

export interface ActionValidationResponse {
  valid: boolean
  diagnostics: ActionValidationDiagnostic[]
  preview: ActionDefinitionPreview | null
}

export interface ActionParameterDefinition {
  type?: 'string' | 'number' | 'integer' | 'boolean'
  default?: unknown
  defaultValue?: unknown
  min?: number
  max?: number
  description?: string
  required?: boolean
}

export interface ActionDefinition {
  id: string
  processorKey: string
  name: string
  description: string | null
  yaml: string
  endpointUrl: string
  endpointTimeoutSeconds: number
  executeRole: ActionExecuteRole
  lockMode: ActionLockMode
  acceptsImages: boolean
  acceptsXml: boolean
  outputsImages: boolean
  outputsXml: boolean
  enabled: boolean
  created: string
  updated: string
}

export interface ActionAssignment {
  id: string
  workspaceId: string
  projectId: string | null
  enabled: boolean
  processor: ActionDefinition
}

export type ActionDefinitionResponse = ActionDefinition
export type ActionAssignmentResponse = ActionAssignment
export type ExecutableActionProcessorResponse = ExecutableActionProcessor

export interface ExecutableActionProcessor {
  assignmentId: string
  processor: ActionDefinition
  executable: boolean
  blockedReason: string | null
}

export interface ActionRun {
  id: string
  processorDefinitionId: string
  processorKey: string
  processorName: string
  workspaceId: string
  projectId: string
  pageIds: string[]
  status: ActionRunStatus
  lockMode: ActionLockMode
  progressPercent: number
  statusMessage: string | null
  errorMessage: string | null
  cancelRequested: boolean
  lastHeartbeatAt: string | null
  created: string
  updated: string
  completedAt: string | null
}

export interface StartActionRunResponse {
  run: ActionRun
}

export const DEFAULT_ACTION_YAML = `version: 1
id: mock-image-copy
name: Mock Image Copy
description: Development processor that copies the first page image and XML back as Action outputs.

endpoint:
  url: http://mock-action-processor:9000/dispatch
  timeoutSeconds: 30
  auth:
    type: hmac
    secretRef: mock-processor-v1

access:
  execute: CURATOR

locking:
  mode: PAGES

inputs:
  images: true
  xml: true

outputs:
  xml:
    enabled: true
    mode: upsert
  images:
    enabled: true
    variant: action-copy
    mode: upsert

runtime:
  model:
    name: mock
    optional: true

parameters:
  threshold:
    type: number
    default: 0.5
    min: 0
    max: 1
`
