import { toValue, type MaybeRefOrGetter } from 'vue'
import type { Page, Region } from '@/models/editor'
import { getEditorSession } from '@/session/editor/editor-session'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { buildTableModel, type TableModel } from '@/utils/editor/table/table-model'

export interface TableSnapshot {
  page: Page
  table: Region
  model: TableModel
}

export function useTableSnapshot(canvasId: MaybeRefOrGetter<string>, tableId: MaybeRefOrGetter<string>) {
  return computed<TableSnapshot | undefined>(() => {
    const page = getEditorSession(toValue(canvasId))?.document.value?.page
    const table = page ? findRegionRecursive(page.regions, toValue(tableId))?.region : undefined
    return page && table?.kind === 'TableRegion' ? { page, table, model: buildTableModel(table) } : undefined
  })
}
