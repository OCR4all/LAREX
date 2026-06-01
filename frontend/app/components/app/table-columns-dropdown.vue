<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import { normalizeTableColumns } from '@/utils/table-columns'

const props = withDefaults(defineProps<{
  tableId: string
  columns?: unknown[]
  defaultVisibleColumnIds?: string[]
  showColumnVisibility?: boolean
}>(), {
  showColumnVisibility: true
})

const normalizedColumns = computed(() => normalizeTableColumns(props.columns))

const { columnVisibility } = usePersistentTableColumnVisibility(
  computed(() => props.tableId),
  computed(() => normalizedColumns.value.filter(column => column.canHide).map(column => column.id)),
  computed(() => props.defaultVisibleColumnIds)
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
</script>

<template>
  <UDropdownMenu
    v-if="showColumnVisibilityMenu"
    :items="columnVisibilityItems"
    :content="{ align: 'end' }"
  >
    <UButton
      icon="i-lucide-columns-3"
      color="neutral"
      variant="ghost"
      size="xs"
      square
      aria-label="Columns"
    />
  </UDropdownMenu>
</template>
