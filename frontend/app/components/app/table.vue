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

function withDefaultDateCells(columns: unknown[] | undefined): unknown[] | undefined {
  if (!columns?.length) return columns

  const dateColumnIds = new Set(props.dateColumnIds)

  return columns.map((value) => {
    if (!isTableColumnLike(value)) return value

    const column = value
    if (Array.isArray(column.columns) && column.columns.length > 0) {
      return {
        ...column,
        columns: withDefaultDateCells(column.columns)
      }
    }

    const id = getTableColumnId(column)
    if (!id || !dateColumnIds.has(id) || column.cell) return column

    return {
      ...column,
      cell: ({ row }: { row: { getValue: (columnId: string) => unknown, original?: Record<string, unknown> } }) => {
        return h(AppDateTime, {
          createdAt: row.original?.created as DateTimeInput,
          updatedAt: row.original?.updated as DateTimeInput,
          value: row.getValue(id) as DateTimeInput
        })
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
  return withDefaultDateCells(visibleColumns) as TableColumn<unknown, unknown>[] | undefined
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
