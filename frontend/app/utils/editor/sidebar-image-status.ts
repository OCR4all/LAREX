import type { PageData } from '@/stores/editor/types'

export type SidebarStatusColor = 'success' | 'info' | 'warning' | 'error' | 'neutral'
export type SidebarStatusDisplay = 'label' | 'icon'
export type SidebarStatusSeverity = 0 | 1 | 2 | 3
export type SidebarStatusCategory = 'core' | 'attention' | 'metadata'

export interface SidebarStatusDescriptor {
  key: string
  label: string
  description: string
  icon: string
  color: SidebarStatusColor
  severity: SidebarStatusSeverity
  display: SidebarStatusDisplay
  category: SidebarStatusCategory
  /** Width used by the rail partitioner before the browser has painted the row. */
  compactWidth?: number
}

export interface SidebarStatusContext {
  pageLockReason?: string | null
  hasUnsavedChanges?: boolean
  previewImageFailed?: boolean
}

export interface StatusRailPartition {
  visible: SidebarStatusDescriptor[]
  hidden: SidebarStatusDescriptor[]
  overflowCount: number
}

const WORKFLOW_DERIVED_LOCK_REASON = 'Page workflow state is Done'
const STATUS_GAP_PX = 4
const ICON_STATUS_WIDTH_PX = 28
const OVERFLOW_STATUS_WIDTH_PX = 28

export function hasPageAnnotations(page: Pick<PageData, 'xmlFiles' | 'xmlFileCount'>): boolean {
  return (page.xmlFiles?.length ?? 0) > 0 || (page.xmlFileCount ?? 0) > 0
}

export function isWorkflowDerivedLock(
  page: Pick<PageData, 'workflowState'>,
  pageLockReason?: string | null
): boolean {
  return page.workflowState === 'DONE' && pageLockReason === WORKFLOW_DERIVED_LOCK_REASON
}

export function getWorkflowStatus(page: Pick<PageData, 'workflowState'>): SidebarStatusDescriptor {
  const state = page.workflowState ?? 'OPEN'

  if (state === 'DONE') {
    return {
      key: 'workflow-done',
      label: 'Done',
      description: 'Workflow: Done. This page is read-only until it is reopened.',
      icon: 'i-lucide-circle-check',
      color: 'success',
      severity: 0,
      display: 'label',
      category: 'core',
      compactWidth: 58
    }
  }

  if (state === 'IN_PROGRESS') {
    return {
      key: 'workflow-in-progress',
      label: 'In progress',
      description: 'Workflow: In progress.',
      icon: 'i-lucide-loader-circle',
      color: 'info',
      severity: 0,
      display: 'label',
      category: 'core',
      compactWidth: 92
    }
  }

  return {
    key: 'workflow-open',
    label: 'Open',
    description: 'Workflow: Open.',
    icon: 'i-lucide-circle',
    color: 'neutral',
    severity: 0,
    display: 'label',
    category: 'core',
    compactWidth: 54
  }
}

export function getAnnotationStatus(page: Pick<PageData, 'xmlFiles' | 'xmlFileCount'>): SidebarStatusDescriptor {
  if (hasPageAnnotations(page)) {
    return {
      key: 'annotations-available',
      label: 'Annotations',
      description: 'Annotations are available for this page.',
      icon: 'i-lucide-file-pen-line',
      color: 'success',
      severity: 0,
      display: 'icon',
      category: 'core'
    }
  }

  return {
    key: 'annotations-missing',
    label: 'No annotations',
    description: 'No annotation file is available for this page yet.',
    icon: 'i-lucide-file-plus-2',
    color: 'neutral',
    severity: 0,
    display: 'icon',
    category: 'core'
  }
}

export function getAttentionStatuses(
  page: Pick<PageData, 'indexingStatus' | 'workflowState'>,
  context: SidebarStatusContext = {}
): SidebarStatusDescriptor[] {
  const statuses: SidebarStatusDescriptor[] = []

  if (context.previewImageFailed) {
    statuses.push({
      key: 'preview-unavailable',
      label: 'Preview unavailable',
      description: 'The page thumbnail could not be loaded.',
      icon: 'i-lucide-image-off',
      color: 'error',
      severity: 3,
      display: 'label',
      category: 'attention',
      compactWidth: 112
    })
  }

  if (page.indexingStatus === 'UNINDEXED') {
    statuses.push({
      key: 'not-indexed',
      label: 'Not indexed',
      description: 'Text search is not available yet because indexing has not completed.',
      icon: 'i-lucide-search-x',
      color: 'error',
      severity: 3,
      display: 'label',
      category: 'attention',
      compactWidth: 88
    })
  }

  if (context.hasUnsavedChanges) {
    statuses.push({
      key: 'unsaved',
      label: 'Unsaved',
      description: 'This page has annotation changes that have not been saved.',
      icon: 'i-lucide-save-off',
      color: 'warning',
      severity: 2,
      display: 'label',
      category: 'attention',
      compactWidth: 68
    })
  }

  if (context.pageLockReason && !isWorkflowDerivedLock(page, context.pageLockReason)) {
    statuses.push({
      key: 'locked',
      label: 'Locked',
      description: `Read-only: ${context.pageLockReason}`,
      icon: 'i-lucide-lock',
      color: 'warning',
      severity: 2,
      display: 'label',
      category: 'attention',
      compactWidth: 68
    })
  }

  if (page.indexingStatus === 'INDEXING') {
    statuses.push({
      key: 'indexing',
      label: 'Indexing',
      description: 'Text search index is being built in the background.',
      icon: 'i-lucide-loader-circle',
      color: 'info',
      severity: 1,
      display: 'label',
      category: 'attention',
      compactWidth: 74
    })
  }

  return statuses
}

export function getAnnotationModeStatus(
  page: Pick<PageData, 'annotationContext'>
): SidebarStatusDescriptor | null {
  const mode = page.annotationContext?.mode
  if (mode === 'DATASET_LINK') {
    return {
      key: 'dataset-link',
      label: 'Link',
      description: 'Linked dataset item: annotations are saved to the source project XML.',
      icon: 'i-lucide-link-2',
      color: 'info',
      severity: 0,
      display: 'icon',
      category: 'metadata'
    }
  }

  if (mode === 'DATASET_COPY') {
    return {
      key: 'dataset-copy',
      label: 'Copy',
      description: 'Dataset copy (frozen source): annotations are saved to the dataset copy XML.',
      icon: 'i-lucide-copy',
      color: 'warning',
      severity: 0,
      display: 'icon',
      category: 'metadata'
    }
  }

  return null
}

export function partitionStatusRail(
  statuses: SidebarStatusDescriptor[],
  availableWidth: number
): StatusRailPartition {
  if (statuses.length === 0) {
    return { visible: [], hidden: [], overflowCount: 0 }
  }

  if (!Number.isFinite(availableWidth) || availableWidth <= 0) {
    return { visible: statuses.slice(0, 1), hidden: statuses.slice(1), overflowCount: Math.max(0, statuses.length - 1) }
  }

  const widthFor = (status: SidebarStatusDescriptor) => status.compactWidth ?? ICON_STATUS_WIDTH_PX
  const visible: SidebarStatusDescriptor[] = []
  let usedWidth = 0

  for (const [index, status] of statuses.entries()) {
    const gap = visible.length > 0 ? STATUS_GAP_PX : 0
    const remaining = index < statuses.length - 1
    const overflowReservation = remaining ? STATUS_GAP_PX + OVERFLOW_STATUS_WIDTH_PX : 0
    const nextWidth = usedWidth + gap + widthFor(status) + overflowReservation

    if (nextWidth <= availableWidth || visible.length === 0) {
      visible.push(status)
      usedWidth += gap + widthFor(status)
      continue
    }

    break
  }

  let hidden = statuses.slice(visible.length)
  while (hidden.length > 0 && visible.length > 1 && usedWidth + STATUS_GAP_PX + OVERFLOW_STATUS_WIDTH_PX > availableWidth) {
    const removed = visible.pop()
    if (!removed) break
    usedWidth -= (visible.length > 0 ? STATUS_GAP_PX : 0) + widthFor(removed)
    hidden = [removed, ...hidden]
  }

  return {
    visible,
    hidden,
    overflowCount: hidden.length
  }
}
