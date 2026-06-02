export type ActionExecuteRole = 'EDITOR' | 'CURATOR'
export type ActionLockMode = 'PAGES' | 'PROJECT'
export type ActionCategory = 'WORKFLOW' | 'OCR_HTR' | 'LAYOUT'
export type ActionTarget = 'PAGE' | 'REGION' | 'TEXT_LINE'
export type ActionRunStatus = 'QUEUED' | 'PENDING' | 'DISPATCHING' | 'RUNNING' | 'IMPORTING_RESULTS' | 'COMPLETED' | 'FAILED' | 'CANCEL_REQUESTED' | 'CANCELLED'

export interface ActionTargetSelectionPage {
  pageId: string
  regionIds: string[]
  textLineIds: string[]
}

export interface ActionTargetSelection {
  type: ActionTarget
  pages: ActionTargetSelectionPage[]
}

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
  category: ActionCategory
  targets: ActionTarget[]
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
  category: ActionCategory
  targets: ActionTarget[]
  acceptsImages: boolean
  acceptsXml: boolean
  outputsImages: boolean
  outputsXml: boolean
  enabled: boolean
  global: boolean
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

export interface ActionWorkspaceAvailability {
  id: string
  workspaceId: string
  enabled: boolean
  processor: ActionDefinition
  created: string
  updated: string
}

export type ActionDefinitionResponse = ActionDefinition
export type ActionAssignmentResponse = ActionAssignment
export type ExecutableActionProcessorResponse = ExecutableActionProcessor

export interface ExecutableActionProcessor {
  assignmentId: string | null
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
  targetSelection: ActionTargetSelection
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

export interface ActionRunDetail {
  run: ActionRun
  logText: string | null
  logEvents: ActionRunLogEvent[]
  resultSummary: unknown
  durationSeconds: number | null
}

export interface AdminActionRun {
  id: string
  processorDefinitionId: string
  processorKey: string
  processorName: string
  workspaceId: string
  workspaceLabel: string
  projectId: string
  projectLabel: string
  pageCount: number
  status: ActionRunStatus
  progressPercent: number
  statusMessage: string | null
  errorMessage: string | null
  cancelRequested: boolean
  logText: string | null
  logEvents: ActionRunLogEvent[]
  resultSummary: unknown
  lastHeartbeatAt: string | null
  created: string
  updated: string
  completedAt: string | null
  durationSeconds: number | null
}

export interface ActionRunLogEvent {
  id: string
  level: string
  message: string
  created: string
}

export interface ClearActionRunsResponse {
  deletedCount: number
}

export interface ActionHealthCheckResponse {
  ok: boolean
  statusCode: number
  url: string
  message: string
  durationMillis: number
}

export interface ActionAuditEvent {
  id: string
  action: string
  outcome: string
  actorUserId: string | null
  processorDefinitionId: string | null
  runId: string | null
  workspaceId: string | null
  projectId: string | null
  details: unknown
  created: string
}

export const DEFAULT_ACTION_YAML = `version: 1
id: mock-image-copy
name: Mock Image Copy
description: Development processor that copies the first page image and XML back as Action outputs.
category: WORKFLOW
targets:
  - PAGE

endpoint:
  url: http://mock-action-processor:9000/dispatch
  healthUrl: http://mock-action-processor:9000/health
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

concurrency:
  maxActiveRuns: 1
  scope: PROJECT

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
