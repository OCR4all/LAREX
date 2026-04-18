<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import { computed, ref, useAttrs, useSlots } from 'vue'
import { usePersistentTableColumnVisibility } from '@/composables/use-table-column-visibility'

defineOptions({ inheritAttrs: false })

type NormalizedColumn = {
  id: string
  label: string
  canHide: boolean
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
  columns?: any[]
  data?: any[]
  showColumnVisibility?: boolean
}>(), {
  showColumnVisibility: true
})

defineSlots<Record<string, (props?: any) => any>>()

const attrs = useAttrs()
const slots = useSlots()

const rootRef = ref<HTMLElement | null>(null)
const tableRef = ref<{ $el?: HTMLElement | null, tableApi?: unknown } | null>(null)

const wrapperClass = computed(() => attrs.class)
const wrapperStyle = computed(() => attrs.style)

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

function normalizeColumns(columns: any[] | undefined): NormalizedColumn[] {
  if (!columns?.length) return []

  const normalized: NormalizedColumn[] = []
  const seen = new Set<string>()

  const walkColumns = (input: any[]) => {
    for (const column of input) {
      if ('columns' in column && Array.isArray(column.columns) && column.columns.length > 0) {
        walkColumns(column.columns as any[])
        continue
      }

      const id = (() => {
        if (typeof column.id === 'string') return column.id
        if ('accessorKey' in column && typeof column.accessorKey === 'string') return column.accessorKey
        return ''
      })()

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

const { columnVisibility } = usePersistentTableColumnVisibility(
  computed(() => props.tableId),
  computed(() => normalizedColumns.value.filter(column => column.canHide).map(column => column.id))
)

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

defineExpose({
  get $el() {
    return tableRef.value?.$el ?? rootRef.value
  },
  tableRef,
  get tableApi() {
    return tableRef.value?.tableApi
  }
})
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
      v-model:column-visibility="columnVisibility"
      :columns="props.columns"
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
