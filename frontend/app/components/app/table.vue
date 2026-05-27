<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { StyleValue } from 'vue'
import AppDateTime from '@/components/app/date-time.vue'
import type { DateTimeInput } from '@/composables/use-local-date-time'

defineOptions({ inheritAttrs: false })

type NormalizedColumn = {
  id: string
  label: string
  canHide: boolean
}

type ColumnLike = {
  id?: unknown
  accessorKey?: unknown
  cell?: unknown
  header?: unknown
  columns?: unknown
  enableHiding?: unknown
}

const FIXED_VISIBLE_COLUMN_IDS = new Set(['select', 'actions'])
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
  showColumnVisibility?: boolean
}>(), {
  dateColumnIds: () => ['created', 'updated'],
  showColumnVisibility: true
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

function humanizeColumnLabel(id: string): string {
  if (id === 'select') return 'Select'
  if (id === 'actions') return 'Actions'

  return id
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, char => char.toUpperCase())
}

function isColumnLike(value: unknown): value is ColumnLike {
  return !!value && typeof value === 'object'
}

function getColumnId(column: ColumnLike): string {
  if (typeof column.id === 'string') return column.id
  if (typeof column.accessorKey === 'string') return column.accessorKey
  return ''
}

function withDefaultDateCells(columns: unknown[] | undefined): unknown[] | undefined {
  if (!columns?.length) return columns

  const dateColumnIds = new Set(props.dateColumnIds)

  return columns.map((value) => {
    if (!isColumnLike(value)) return value

    const column = value
    if (Array.isArray(column.columns) && column.columns.length > 0) {
      return {
        ...column,
        columns: withDefaultDateCells(column.columns)
      }
    }

    const id = getColumnId(column)
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

function filterVisibleColumns(columns: unknown[] | undefined): unknown[] | undefined {
  if (!columns?.length) return columns

  return columns.flatMap((value) => {
    if (!isColumnLike(value)) return [value]

    const column = value
    if (Array.isArray(column.columns) && column.columns.length > 0) {
      const visibleChildColumns = filterVisibleColumns(column.columns)
      if (!visibleChildColumns?.length) return []

      return [{
        ...column,
        columns: visibleChildColumns
      }]
    }

    const id = getColumnId(column)
    if (!id || FIXED_VISIBLE_COLUMN_IDS.has(id) || columnVisibility.value[id] !== false) {
      return [column]
    }

    return []
  })
}

function normalizeColumns(columns: unknown[] | undefined): NormalizedColumn[] {
  if (!columns?.length) return []

  const normalized: NormalizedColumn[] = []
  const seen = new Set<string>()

  const walkColumns = (input: unknown[]) => {
    for (const value of input) {
      if (!isColumnLike(value)) continue

      const column = value
      if (Array.isArray(column.columns) && column.columns.length > 0) {
        walkColumns(column.columns)
        continue
      }

      const id = getColumnId(column)

      if (!id || seen.has(id)) continue
      seen.add(id)

      const label = typeof column.header === 'string' && column.header.trim().length > 0
        ? column.header.trim()
        : humanizeColumnLabel(id)

      normalized.push({
        id,
        label,
        canHide: !FIXED_VISIBLE_COLUMN_IDS.has(id) && column.enableHiding !== false
      })
    }
  }

  walkColumns(columns)
  return normalized
}

const normalizedColumns = computed(() => normalizeColumns(props.columns))

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
  const visibleColumns = filterVisibleColumns(props.columns)
  return withDefaultDateCells(visibleColumns) as TableColumn<unknown, unknown>[] | undefined
})

const columnVisibilityItems = computed<DropdownMenuItem[]>(() => normalizedColumns.value
  .filter(column => column.canHide)
  .map(column => ({
    type: 'checkbox',
    label: column.label,
    checked: columnVisibility.value[column.id] !== false,
    onUpdateChecked: (checked: boolean) => {
      columnVisibility.value = {
        ...columnVisibility.value,
        [column.id]: checked
      }
    }
  })))

const showColumnVisibilityMenu = computed(() =>
  props.showColumnVisibility && columnVisibilityItems.value.length > 0
)
</script>

<template>
  <div
    ref="rootRef"
    class="flex min-h-0 flex-col gap-2"
    :class="wrapperClass"
    :style="wrapperStyle"
  >
    <div v-if="showColumnVisibilityMenu" class="flex justify-end">
      <UDropdownMenu :items="columnVisibilityItems" :content="{ align: 'end' }">
        <UButton
          icon="i-lucide-columns-3"
          label="Columns"
          color="neutral"
          variant="outline"
          size="xs"
        />
      </UDropdownMenu>
    </div>

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
