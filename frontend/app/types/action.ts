export type ActionExecuteRole = 'EDITOR' | 'CURATOR'
export type ActionLockMode = 'NONE' | 'PAGES' | 'PROJECT'
export type ActionKind = 'PROCESSING' | 'TRAINING' | 'EVALUATION'
export type ActionCategory = 'WORKFLOW' | 'OCR_HTR' | 'LAYOUT' | 'POSTPROCESSING'
export type ActionTarget = 'PAGE' | 'REGION' | 'TEXT_LINE'
export type ActionInputLevel = 'NONE' | 'OPTIONAL' | 'REQUIRED'
export type ActionRunStatus = 'QUEUED' | 'PENDING' | 'DISPATCHING' | 'RUNNING' | 'IMPORTING_RESULTS' | 'COMPLETED' | 'FAILED' | 'CANCEL_REQUESTED' | 'CANCELLED'

export interface ActionInputRequirement {
  level: ActionInputLevel
  requiredForTargets: ActionTarget[]
}

export interface ActionInputRequirements {
  images: ActionInputRequirement
  xml: ActionInputRequirement
}

export interface ActionTargetSelectionPage {
  pageId: string
  regionIds: string[]
  textLineIds: string[]
}

export interface ActionTargetSelection {
  type: ActionTarget
  pages: ActionTargetSelectionPage[]
}

export type ActionImageVariantSelectionMode = 'GLOBAL' | 'PER_PAGE'

export interface ActionImageVariantSelection {
  mode: ActionImageVariantSelectionMode
  variant?: string | null
  pageVariants?: Record<string, string>
  fallbackImage: boolean
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
  kind?: ActionKind
  executeRole: ActionExecuteRole
  lockMode: ActionLockMode
  category: ActionCategory
  targets: ActionTarget[]
  inputs: ActionInputRequirements
  acceptsImages: boolean
  acceptsXml: boolean
  outputsImages: boolean
  outputsXml: boolean
  outputsFiles: boolean
  trainingSplits: ActionTrainingSplitRequirements | null
  evaluation?: ActionEvaluationDefinition | null
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
  allowedValues?: ActionAllowedValues
}

export type ActionParameterValue = string | number | boolean

export interface ActionParameterChoice {
  value: ActionParameterValue
  label: string
}

export interface ActionAllowedValues {
  values?: ActionParameterChoice[]
  provider?: string
}

export interface ActionParameterValuesResponse {
  values: Record<string, ActionParameterChoice[]>
}

export interface ActionDefinition {
  id: string
  processorKey: string
  name: string
  description: string | null
  yaml: string
  endpointUrl: string
  endpointTimeoutSeconds: number
  kind: ActionKind
  executeRole: ActionExecuteRole
  lockMode: ActionLockMode
  category: ActionCategory
  targets: ActionTarget[]
  inputs: ActionInputRequirements
  acceptsImages: boolean
  acceptsXml: boolean
  outputsImages: boolean
  outputsXml: boolean
  outputsFiles: boolean
  enabled: boolean
  global: boolean
  created: string
  updated: string
  trainingSplits: ActionTrainingSplitRequirements | null
  evaluation?: ActionEvaluationDefinition | null
  parameters: Record<string, ActionParameterDefinition>
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
  kind?: ActionKind
  workspaceId: string
  projectId: string | null
  projectLabel: string | null
  datasetId?: string | null
  datasetLabel?: string | null
  inputCount?: number
  splitCounts?: Record<string, number>
  pageCount: number
  pageIds: string[]
  completedPageIds: string[]
  targetSelection: ActionTargetSelection | null
  status: ActionRunStatus
  lockMode: ActionLockMode
  progressPercent: number
  queuePosition: number | null
  statusMessage: string | null
  errorMessage: string | null
  canCancel: boolean
  cancelRequested: boolean
  lastHeartbeatAt: string | null
  created: string
  updated: string
  completedAt: string | null
}

export interface ActionTrainingSplitRequirements {
  train: ActionInputLevel
  val: ActionInputLevel
  test: ActionInputLevel
}

export interface ActionEvaluationDefinition {
  profile: string
  profileVersion: number
  splits: ActionTrainingSplitRequirements
}

export interface TrainingInputImage {
  id: string
  fileName: string
  variant: string | null
}

export interface TrainingInputItem {
  itemId: string
  sourcePageId: string
  pageName: string
  split: 'TRAIN' | 'VAL' | 'TEST'
  status: 'READY' | 'BROKEN'
  brokenReason: string | null
  xmlAvailable: boolean
  images: TrainingInputImage[]
}

export interface TrainingInputResponse {
  datasetId: string
  datasetName: string
  items: TrainingInputItem[]
}

export interface StartActionRunResponse {
  run: ActionRun
}

export interface ActionRunDetail {
  run: ActionRun
  logText: string | null
  logEvents: ActionRunLogEvent[]
  resultSummary: unknown
  evaluationReport?: EvaluationReport | null
  durationSeconds: number | null
}

export type EvaluationMetricFormat = 'NUMBER' | 'INTEGER' | 'PERCENT'
export type EvaluationMetricDirection = 'HIGHER_IS_BETTER' | 'LOWER_IS_BETTER' | 'NEUTRAL'

export interface EvaluationMetric {
  key: string
  label: string
  value: number
  format: EvaluationMetricFormat
  unit?: string | null
  direction: EvaluationMetricDirection
}

export interface EvaluationTableColumn {
  key: string
  label: string
  type: 'STRING' | 'NUMBER' | 'INTEGER' | 'BOOLEAN'
}

export interface EvaluationTable {
  key: string
  title: string
  columns: EvaluationTableColumn[]
  rows: Array<{ values: Record<string, unknown> }>
  truncated: boolean
  totalRows: number
}

export interface EvaluationSample {
  id: string
  inputId: string
  targetId?: string | null
  label?: string | null
  status: 'OK' | 'SKIPPED' | 'FAILED'
  fields: Record<string, unknown>
  metrics: EvaluationMetric[]
}

export interface EvaluationReport {
  schemaVersion: 1
  profile: string
  profileVersion: number
  title: string
  summary: EvaluationMetric[]
  tables: EvaluationTable[]
  samples: EvaluationSample[]
  warnings: string[]
  metadata: Record<string, unknown>
}

export interface AdminActionRun {
  id: string
  processorDefinitionId: string
  processorKey: string
  processorName: string
  kind?: ActionKind
  workspaceId: string
  workspaceLabel: string
  projectId: string | null
  projectLabel: string | null
  datasetId?: string | null
  datasetLabel?: string | null
  inputCount?: number
  splitCounts?: Record<string, number>
  pageCount: number
  status: ActionRunStatus
  progressPercent: number
  queuePosition: number | null
  statusMessage: string | null
  errorMessage: string | null
  canCancel: boolean
  cancelRequested: boolean
  logText: string | null
  logEvents: ActionRunLogEvent[]
  resultSummary: unknown
  evaluationReport?: EvaluationReport | null
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

export interface BulkCancelActionRunsResponse {
  cancelledCount: number
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

export interface ActionEndpointSecret {
  id: string | null
  ref: string
  envName: string
  displayName: string | null
  description: string | null
  createdBy: string | null
  createdAt: string | null
  updatedAt: string | null
  lastUsedAt: string | null
  rotatedAt: string | null
  source: 'DATABASE' | 'ENV_FALLBACK'
}

export interface ActionEndpointSecretRevealResponse {
  secret: ActionEndpointSecret
  plaintext: string
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
  images:
    level: optional
  xml:
    level: optional

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
