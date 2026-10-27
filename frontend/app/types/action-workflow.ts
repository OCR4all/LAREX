import type { KnownImageType } from './image'
import type { ActionHealthCheckResponse, ActionImageVariantSelection, ActionParameterValue } from './action'

export interface ActionWorkflowStep {
  processorDefinitionId: string
  parameters: Record<string, ActionParameterValue | '' | undefined>
  imageVariantSelection: ActionImageVariantSelection | null
  imageTypes?: KnownImageType[] | null
}

export interface ActionWorkflow {
  id: string
  name: string
  description: string | null
  enabled: boolean
  steps: ActionWorkflowStep[]
  version: number
  processorDefinitionId?: string
  global?: boolean
}

export interface ActionWorkflowTest {
  ok: boolean
  steps: {
    step: number
    processorDefinitionId: string
    processorName: string
    ok: boolean
    message: string
    endpoint: ActionHealthCheckResponse | null
  }[]
}

export interface ActionWorkflowRun {
  id: string
  workflowId: string
  workflowName: string
  workspaceId: string
  projectId: string
  createdByUserId: string
  stepNames: string[]
  status: 'RUNNING' | 'CANCEL_REQUESTED' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  steps: ActionWorkflowStep[]
  pageIds: string[]
  stepRunIds: string[]
  errorMessage: string | null
  canCancel: boolean
  created: string
  completedAt: string | null
}
