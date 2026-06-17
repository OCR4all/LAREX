<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { StyleValue } from 'vue'
import AppDateTime from '@/components/app/date-time.vue'
import type { DateTimeInput } from '@/composables/use-local-date-time'
import {
  filterVisibleTableColumns,
  getTableColumnId,
  isTableColumnLike,
  normalizeTableColumns
} from '@/utils/table-columns'

defineOptions({ inheritAttrs: false })
const DEFAULT_TABLE_UI = {
  base: 'table-fixed border-separate border-spacing-0',
  thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
  tbody: '[&>tr]:last:[&>td]:border-b-0',
  th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
  td: 'border-b border-default',
  separator: 'h-0'
}

const props = withDefaults(defineProps<{
  tableId: string
  columns?: unknown[]
  data?: unknown[]
  dateColumnIds?: string[]
  defaultVisibleColumnIds?: string[]
}>(), {
  dateColumnIds: () => ['created', 'updated']
})

// Dynamic table slots preserve their row types at each AppTable call site.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
defineSlots<Record<string, (props?: any) => any>>()

const attrs = useAttrs()
const slots = useSlots()

const rootRef = ref<HTMLElement | null>(null)
const tableRef = ref<{ $el?: HTMLElement | null, tableApi?: unknown } | null>(null)

const wrapperClass = computed(() => attrs.class)
const wrapperStyle = computed(() => attrs.style as StyleValue)

const tableAttrs = computed(() => {
  const { class: _class, style: _style, ui: _ui, ...rest } = attrs
  return rest
})

const tableUi = computed(() => ({
  ...DEFAULT_TABLE_UI,
  ...((attrs.ui as Record<string, unknown> | undefined) ?? {})
}))

type TableRow = {
  getValue: (columnId: string) => unknown
  original?: Record<string, unknown>
}

type TableCellProps = {
  row: TableRow
}

function renderEmptyTableCell() {
  return h('div', { class: 'text-neutral-400 dark:text-neutral-500 text-sm' }, '—')
}

function isEmptyTableValue(value: unknown) {
  return value === null
    || value === undefined
    || (typeof value === 'string' && value.trim().length === 0)
}

function isEmptyTableCellContent(content: unknown) {
  return content === null
    || content === undefined
    || (typeof content === 'string' && content.trim().length === 0)
    || (Array.isArray(content) && content.length === 0)
}

function createDefaultTableCell(
  id: string,
  dateColumnIds: Set<string>
) {
  return ({ row }: TableCellProps) => {
    if (dateColumnIds.has(id)) {
      return h(AppDateTime, {
        createdAt: row.original?.created as DateTimeInput,
        updatedAt: row.original?.updated as DateTimeInput,
        value: row.getValue(id) as DateTimeInput
      })
    }

    const value = row.getValue(id)
    return isEmptyTableValue(value) ? renderEmptyTableCell() : value
  }
}

function withDefaultCells(columns: unknown[] | undefined): unknown[] | undefined {
  if (!columns?.length) return columns

  const dateColumnIds = new Set(props.dateColumnIds)

  return columns.map((value) => {
    if (!isTableColumnLike(value)) return value

    const column = value
    if (Array.isArray(column.columns) && column.columns.length > 0) {
      return {
        ...column,
        columns: withDefaultCells(column.columns)
      }
    }

    const id = getTableColumnId(column)
    if (!id) return column

    const baseCell = typeof column.cell === 'function'
      ? column.cell as (props: TableCellProps) => unknown
      : createDefaultTableCell(id, dateColumnIds)

    return {
      ...column,
      cell: (cellProps: TableCellProps) => {
        const content = baseCell(cellProps)
        return isEmptyTableCellContent(content) ? renderEmptyTableCell() : content
      }
    }
  })
}
const normalizedColumns = computed(() => normalizeTableColumns(props.columns))

defineExpose({
  get $el() {
    return tableRef.value?.$el ?? rootRef.value
  },
  tableRef,
  get tableApi() {
    return tableRef.value?.tableApi
  }
})

const { columnVisibility } = usePersistentTableColumnVisibility(
  computed(() => props.tableId),
  computed(() => normalizedColumns.value.filter(column => column.canHide).map(column => column.id)),
  computed(() => props.defaultVisibleColumnIds)
)

const tableColumns = computed(() => {
  const visibleColumns = filterVisibleTableColumns(props.columns, columnVisibility.value)
  return withDefaultCells(visibleColumns) as TableColumn<unknown, unknown>[] | undefined
})
</script>

<template>
  <div
    ref="rootRef"
    class="flex min-h-0 flex-col gap-2"
    :class="wrapperClass"
    :style="wrapperStyle"
  >
    <UTable
      ref="tableRef"
      sticky
      :columns="tableColumns"
      :data="props.data"
      :ui="tableUi"
      v-bind="tableAttrs"
    >
      <template
        v-for="(_, slotName) in slots"
        :key="slotName"
        #[slotName]="slotProps"
      >
        <slot :name="slotName" v-bind="slotProps ?? {}" />
      </template>
    </UTable>
  </div>
</template>
