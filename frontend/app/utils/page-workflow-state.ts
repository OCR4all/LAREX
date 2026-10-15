import type { PageWorkflowState } from '@/types/project-page'

export const PAGE_WORKFLOW_STATES: PageWorkflowState[] = ['OPEN', 'IN_PROGRESS', 'DONE']

export const PAGE_WORKFLOW_STATE_META: Record<PageWorkflowState, {
  label: string
  color: 'neutral' | 'info' | 'success'
  icon: string
}> = {
  OPEN: { label: 'Open', color: 'neutral', icon: 'i-lucide-circle' },
  IN_PROGRESS: { label: 'In progress', color: 'info', icon: 'i-lucide-loader-circle' },
  DONE: { label: 'Done', color: 'success', icon: 'i-lucide-circle-check' }
}
