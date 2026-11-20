import { toValue, type MaybeRefOrGetter } from 'vue'
import { RepairTableCommand } from '@/commands/editor'
import { createTextViewCommandContext, getTextViewRuntimeControls } from '@/components/editor/text/shared/text-view-runtime'
import { getEditorSession } from '@/session/editor/editor-session'
import { isRepairableTableIssue, validateTables, type TableIssue } from '@/utils/editor/table/table-validation'

export function showTableRepairResult(remaining: number): void {
  useToast().add({
    title: 'Table repaired',
    description: remaining === 0
      ? 'No problems are left in this table.'
      : `${remaining} problem${remaining === 1 ? ' needs' : 's need'} manual fixing.`,
    color: remaining === 0 ? 'success' : 'warning'
  })
}

export interface TableIssueGroup {
  tableId: string | null
  issues: TableIssue[]
  repairable: boolean
}

export function useTableIssues(canvasId: MaybeRefOrGetter<string | null | undefined>) {
  const editorStore = useEditorStore()

  const issues = computed<TableIssue[]>(() => {
    const id = toValue(canvasId)
    const page = id ? getEditorSession(id)?.document.value?.page : undefined
    return page ? validateTables(page.regions) : []
  })

  const groups = computed<TableIssueGroup[]>(() => {
    const byTable = new Map<string | null, TableIssue[]>()
    for (const issue of issues.value) {
      const key = issue.tableId ?? null
      byTable.set(key, [...(byTable.get(key) ?? []), issue])
    }
    return [...byTable].map(([tableId, tableIssues]) => ({
      tableId,
      issues: tableIssues,
      repairable: tableId !== null && tableIssues.some(isRepairableTableIssue)
    }))
  })

  function issuesForTable(tableId: string): TableIssue[] {
    return issues.value.filter(issue => issue.tableId === tableId)
  }

  function selectRegion(regionId: string): void {
    const id = toValue(canvasId)
    if (!id || !regionId) return
    editorStore.setActiveCanvas(id)
    const runtime = getTextViewRuntimeControls(id, editorStore)
    runtime?.selectPolylineById?.(null, { focusMode: 'none' })
    runtime?.selectPolygonById?.(regionId, { focusMode: 'fit-width' })
  }

  function repairTable(tableId: string): void {
    const id = toValue(canvasId)
    if (!id) return
    try {
      const result = getTextViewRuntimeControls(id, editorStore)?.commander?.execute(
        new RepairTableCommand({ tableId }),
        createTextViewCommandContext(id)
      )
      showTableRepairResult(result?.remaining.length ?? 0)
    } catch (error) {
      useToast().add({
        title: 'Could not repair table',
        description: error instanceof Error ? error.message : 'Check the table structure and try again.',
        color: 'error'
      })
    }
  }

  return { issues, groups, issuesForTable, selectRegion, repairTable }
}
