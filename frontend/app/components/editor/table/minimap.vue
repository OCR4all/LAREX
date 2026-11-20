<script setup lang="ts">
import { isTextRegion } from '@/models/editor'
import { UpdateReadingOrderCommand } from '@/commands/editor'
import { getEditorSession } from '@/session/editor/editor-session'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { buildTableModel, type TableCell } from '@/utils/editor/table/table-model'
import { tableLayoutRows } from '@/utils/editor/table/table-layout'
import { withTableReadingOrder, type TableReadingDirection } from '@/utils/editor/table/table-reading-order'
import { createTextViewCommandContext, getTextViewRuntimeControls } from '@/components/editor/text/shared/text-view-runtime'
import type { CanvasContextMenuItem } from '@/types/editor/canvas-controls'

const props = withDefaults(defineProps<{
  canvasId: string
  tableId: string
  readOnly?: boolean
}>(), {
  readOnly: false
})

const editorStore = useEditorStore()

const page = computed(() => getEditorSession(props.canvasId)?.document.value?.page ?? null)
const table = computed(() => {
  const found = page.value ? findRegionRecursive(page.value.regions, props.tableId)?.region : undefined
  return found?.kind === 'TableRegion' ? found : undefined
})
const model = computed(() => table.value ? buildTableModel(table.value) : undefined)
const rows = computed(() => model.value ? tableLayoutRows(model.value) : [])

const lineCountByCell = computed(() => new Map((table.value?.regions ?? []).map(region =>
  [region.id, isTextRegion(region) ? region.textLines?.length ?? 0 : 0])))
const cellIdByLineId = computed(() => new Map((table.value?.regions ?? []).flatMap(region =>
  isTextRegion(region) ? (region.textLines ?? []).map(line => [line.id, region.id] as const) : [])))

const runtime = computed(() => getTextViewRuntimeControls(props.canvasId, editorStore))
const selectedId = computed(() => {
  const id = runtime.value?.selectedPolygonId?.value ?? null
  return id ? cellIdByLineId.value.get(id) ?? id : null
})

function selectCell(cellId: string): void {
  editorStore.setActiveCanvas(props.canvasId)
  runtime.value?.selectPolylineById?.(null, { focusMode: 'none' })
  runtime.value?.selectPolygonById?.(cellId, { focusMode: 'fit-width' })
}

function describeCell(cell: TableCell): string {
  const lines = lineCountByCell.value.get(cell.regionId) ?? 0
  const position = `Row ${cell.row + 1}, column ${cell.col + 1}`
  const span = cell.rowSpan > 1 || cell.colSpan > 1 ? ` (${cell.rowSpan} × ${cell.colSpan})` : ''
  return `${position}${span}${cell.header ? ', header' : ''}: ${lines} text line${lines === 1 ? '' : 's'}`
}

function generateReadingOrder(direction: TableReadingDirection): void {
  if (!model.value || !page.value) return
  runtime.value?.commander?.execute(
    new UpdateReadingOrderCommand({ readingOrder: withTableReadingOrder(page.value.readingOrder, model.value, direction) }),
    createTextViewCommandContext(props.canvasId)
  )
}

const contextMenuItems = ref<CanvasContextMenuItem[]>([])

function handleContextMenu(event: MouseEvent): void {
  const cellId = (event.target as HTMLElement | null)?.closest<HTMLElement>('[data-cell-id]')?.dataset.cellId
  const controls = getEditorSession(props.canvasId)?.controls.value
  const cellItems = cellId ? controls?.tableContextMenuItemsForPolygon?.(cellId, event) ?? [] : []
  contextMenuItems.value = cellItems.length > 0 ? cellItems : controls?.tableContextMenuItemsForPolygon?.(props.tableId, event) ?? []
}

const readingOrderItems = [[
  { label: 'Row by row', icon: 'i-lucide-arrow-right', onSelect: () => generateReadingOrder('rows') },
  { label: 'Column by column', icon: 'i-lucide-arrow-down', onSelect: () => generateReadingOrder('columns') }
]]
</script>

<template>
  <UContextMenu
    :items="contextMenuItems"
    :disabled="readOnly"
    :ui="{ content: 'min-w-48' }"
  >
    <div class="flex min-w-0 flex-col gap-2" @contextmenu="handleContextMenu">
      <div v-if="!model || model.cells.length === 0" class="text-xs text-muted">
        This table has no cells yet.
      </div>
      <template v-else>
        <div class="flex items-center justify-between gap-2 text-xs text-muted">
          <span>{{ model.rows }} × {{ model.columns }} table</span>
          <UDropdownMenu v-if="!readOnly" :items="readingOrderItems">
            <UButton
              size="xs"
              color="neutral"
              variant="ghost"
              icon="i-lucide-list-ordered"
              label="Reading order"
            />
          </UDropdownMenu>
        </div>
        <table class="w-full table-fixed border-collapse">
          <tbody>
            <tr v-for="(slots, rowIndex) in rows" :key="rowIndex">
              <template v-for="slot in slots" :key="slot.kind === 'cell' ? slot.cell.regionId : `${slot.row}:${slot.col}`">
                <td v-if="slot.kind === 'empty'" class="h-5 border border-dashed border-default" />
                <td
                  v-else
                  :data-cell-id="slot.cell.regionId"
                  :rowspan="slot.cell.rowSpan"
                  :colspan="slot.cell.colSpan"
                  :title="describeCell(slot.cell)"
                  :aria-label="describeCell(slot.cell)"
                  class="h-5 cursor-pointer border border-accented transition-colors hover:bg-primary/15"
                  :class="[
                    slot.cell.header ? 'bg-muted' : '',
                    (lineCountByCell.get(slot.cell.regionId) ?? 0) === 0 ? 'bg-[repeating-linear-gradient(45deg,transparent,transparent_3px,var(--ui-border)_3px,var(--ui-border)_4px)]' : '',
                    selectedId === slot.cell.regionId ? 'bg-primary/25 ring-2 ring-inset ring-primary' : ''
                  ]"
                  @mouseenter="runtime?.hoverPolygonById?.(slot.cell.regionId)"
                  @mouseleave="runtime?.unhoverPolygon?.()"
                  @click="selectCell(slot.cell.regionId)"
                />
              </template>
            </tr>
          </tbody>
        </table>
      </template>
    </div>
    <template #item-leading="{ item }">
      <div class="mr-2 flex shrink-0 items-center gap-1">
        <span v-if="item.dotColor" class="size-2.5 rounded-sm border border-neutral-300" :style="{ backgroundColor: item.dotColor }" />
        <Icon v-if="item.icon" :name="item.icon" class="size-4" />
      </div>
    </template>
  </UContextMenu>
</template>
