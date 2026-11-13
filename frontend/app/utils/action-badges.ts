import type { ActionKind } from '~/types/action'

export interface ActionBadgeMeta {
  label: string
  icon: string
  class: string
}

export const actionKindMeta: Record<ActionKind, ActionBadgeMeta> = {
  PROCESSING: {
    label: 'Processing',
    icon: 'i-lucide-play',
    class: 'bg-sky-500/10 text-sky-700 ring-sky-500/25 dark:text-sky-300'
  },
  TRAINING: {
    label: 'Training',
    icon: 'i-lucide-brain-circuit',
    class: 'bg-violet-500/10 text-violet-700 ring-violet-500/25 dark:text-violet-300'
  },
  EVALUATION: {
    label: 'Evaluation',
    icon: 'i-lucide-chart-no-axes-combined',
    class: 'bg-orange-500/10 text-orange-700 ring-orange-500/25 dark:text-orange-300'
  },
  INTEGRATION: {
    label: 'Integration',
    icon: 'i-lucide-plug',
    class: 'bg-teal-500/10 text-teal-700 ring-teal-500/25 dark:text-teal-300'
  }
}

export const actionWorkflowMeta: ActionBadgeMeta = {
  label: 'Workflow',
  icon: 'i-lucide-workflow',
  class: 'bg-indigo-500/10 text-indigo-700 ring-indigo-500/25 dark:text-indigo-300'
}

export const actionScopeMeta: Record<'global' | 'workspace', ActionBadgeMeta> = {
  global: {
    label: 'Global',
    icon: 'i-lucide-globe',
    class: 'bg-navy-500/10 text-navy-700 ring-navy-500/25 dark:text-navy-300'
  },
  workspace: {
    label: 'Workspace',
    icon: 'i-lucide-layers',
    class: 'bg-slate-500/10 text-slate-700 ring-slate-500/25 dark:text-slate-300'
  }
}

export function actionKindBadge(kind?: ActionKind, workflow = false): ActionBadgeMeta {
  return workflow ? actionWorkflowMeta : actionKindMeta[kind ?? 'PROCESSING']
}

export function actionScopeBadge(global: boolean): ActionBadgeMeta {
  return global ? actionScopeMeta.global : actionScopeMeta.workspace
}
