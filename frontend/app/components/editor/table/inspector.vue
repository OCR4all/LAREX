<script setup lang="ts">
import { isTextRegion } from '@/models/editor'
import { TableStructureCommand } from '@/commands/editor'
import { useTableIssues } from '@/composables/editor/use-table-issues'
import { useTableSnapshot } from '@/composables/editor/use-table-snapshot'
import { linesCrossingCells } from '@/utils/editor/table/table-checks'
import { readGridMatrix } from '@/utils/editor/table/table-model'
import { describeTableIssue, isRepairableTableIssue } from '@/utils/editor/table/table-validation'
import { createTextViewCommandContext, getTextViewRuntimeControls } from '@/components/editor/text/shared/text-view-runtime'

const props = withDefaults(defineProps<{
  canvasId: string
  tableId: string
  readOnly?: boolean
}>(), {
  readOnly: false
})

const editorStore = useEditorStore()
const { issuesForTable, selectRegion, repairTable } = useTableIssues(() => props.canvasId)

const snapshot = useTableSnapshot(() => props.canvasId, () => props.tableId)
const model = computed(() => snapshot.value?.model)
const issues = computed(() => issuesForTable(props.tableId))
const repairable = computed(() => issues.value.some(isRepairableTableIssue))

const gridState = computed(() => {
  const current = snapshot.value
  if (!current) return ''
  if (current.model.grid) return 'Grid'
  return readGridMatrix(current.table) || current.table.grid?.rows?.length ? 'Unreadable grid' : 'No grid'
})
const headerRows = computed(() => {
  const current = model.value
  if (!current) return 0
  let count = 0
  for (let row = 0; row < current.rows; row++) {
    const cells = current.cells.filter(cell => cell.row <= row && row < cell.row + cell.rowSpan)
    if (cells.length === 0 || !cells.every(cell => cell.header)) break
    count++
  }
  return count
})

const runtime = computed(() => getTextViewRuntimeControls(props.canvasId, editorStore))
const selectedCell = computed(() => {
  const selectedId = runtime.value?.selectedPolygonId?.value
  const current = snapshot.value
  if (!selectedId || !current) return undefined
  const region = (current.table.regions ?? []).find(child => child.id === selectedId
    || (isTextRegion(child) && child.textLines?.some(line => line.id === selectedId)))
  const cell = region ? current.model.cells.find(candidate => candidate.regionId === region.id) : undefined
  return cell && region ? { cell, region } : undefined
})
const selectedLineCount = computed(() => {
  const region = selectedCell.value?.region
  return region && isTextRegion(region) ? region.textLines?.length ?? 0 : 0
})
const selectedCrossing = computed(() => {
  const cellId = selectedCell.value?.cell.regionId
  return cellId && snapshot.value ? linesCrossingCells(snapshot.value.table).get(cellId)?.length ?? 0 : 0
})

function setHeader(header: boolean | 'indeterminate'): void {
  const cell = selectedCell.value?.cell
  if (!cell || header === 'indeterminate') return
  runtime.value?.commander?.execute(
    new TableStructureCommand({ tableId: props.tableId, operation: { kind: 'set-cell-header', cellId: cell.regionId, header } }),
    createTextViewCommandContext(props.canvasId)
  )
}

function plural(count: number, word: string): string {
  return `${count} ${word}${count === 1 ? '' : 's'}`
}
</script>

<template>
  <div v-if="model" class="flex min-w-0 flex-col gap-3">
    <dl class="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs">
      <dt class="text-muted">
        Size
      </dt>
      <dd>{{ plural(model.rows, 'row') }} × {{ plural(model.columns, 'column') }}</dd>
      <dt class="text-muted">
        Cells
      </dt>
      <dd>{{ model.cells.length }}</dd>
      <dt class="text-muted">
        Header rows
      </dt>
      <dd>{{ headerRows }}</dd>
      <dt class="text-muted">
        Geometry
      </dt>
      <dd>{{ gridState }}</dd>
    </dl>

    <div v-if="issues.length > 0" class="flex flex-col gap-1.5 rounded-md border border-default p-2">
      <div class="flex items-center justify-between gap-2">
        <span class="text-xs font-medium text-highlighted">{{ plural(issues.length, 'problem') }}</span>
        <UButton
          v-if="repairable && !readOnly"
          size="xs"
          variant="soft"
          icon="i-lucide-wrench"
          label="Repair table"
          @click="repairTable(tableId)"
        />
      </div>
      <ul class="flex flex-col gap-0.5">
        <li v-for="(issue, index) in issues" :key="`${issue.code}:${index}`">
          <button
            type="button"
            class="flex w-full items-start gap-1.5 rounded px-1 py-0.5 text-left text-xs hover:bg-elevated"
            @click="selectRegion(issue.regionIds[0] ?? tableId)"
          >
            <UIcon
              :name="issue.severity === 'error' ? 'i-lucide-circle-x' : 'i-lucide-triangle-alert'"
              :class="issue.severity === 'error' ? 'text-error' : 'text-warning'"
              class="mt-0.5 size-3.5 shrink-0"
            />
            <span>{{ describeTableIssue(issue) }}</span>
          </button>
        </li>
      </ul>
    </div>

    <EditorTableMinimap :canvas-id="canvasId" :table-id="tableId" :read-only="readOnly" />

    <div v-if="selectedCell" class="flex flex-col gap-2 rounded-md border border-default p-2">
      <span class="text-xs font-medium text-highlighted">Selected cell</span>
      <dl class="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs">
        <dt class="text-muted">
          Position
        </dt>
        <dd>Row {{ selectedCell.cell.row + 1 }}, column {{ selectedCell.cell.col + 1 }}</dd>
        <dt class="text-muted">
          Span
        </dt>
        <dd>{{ plural(selectedCell.cell.rowSpan, 'row') }} × {{ plural(selectedCell.cell.colSpan, 'column') }}</dd>
        <dt class="text-muted">
          Text lines
        </dt>
        <dd>
          {{ selectedLineCount }}
          <span v-if="selectedCrossing > 0" class="text-warning">({{ selectedCrossing }} crossing the border)</span>
        </dd>
        <dt class="text-muted">
          ID
        </dt>
        <dd class="truncate font-mono">
          {{ selectedCell.cell.regionId }}
        </dd>
      </dl>
      <UCheckbox
        :model-value="selectedCell.cell.header"
        :disabled="readOnly || !model.grid"
        label="Header cell"
        size="sm"
        @update:model-value="setHeader"
      />
    </div>
  </div>
</template>
