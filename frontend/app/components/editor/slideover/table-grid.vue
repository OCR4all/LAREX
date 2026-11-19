<script setup lang="ts">
import type { Point } from '@/models/editor'
import type { RenderablePolygon } from '@/types/editor/rendering'
import type { TableGridOptions } from '@/composables/editor/use-editor-command'
import { useBlockEditorCanvasInteractions } from '@/composables/editor/use-canvas-interaction-blocker'
import { createGridTableModel } from '@/utils/editor/table/table-builder'
import { interiorGridLines } from '@/utils/editor/table/table-model'

useBlockEditorCanvasInteractions()

const props = defineProps<{
  polygon: RenderablePolygon
  textLineCount: number
}>()

const emit = defineEmits<{ close: [TableGridOptions | null] }>()

const MAX_SIZE = 100

const formId = useId()
const rows = ref(3)
const columns = ref(3)
const headerRows = ref(0)
const moveTextLines = ref(true)

const isConversion = computed(() => props.polygon.regionKind !== 'TableRegion')
const validSize = (value: number) => Number.isInteger(value) && value >= 1 && value <= MAX_SIZE
const canSave = computed(() => validSize(rows.value) && validSize(columns.value)
  && Number.isInteger(headerRows.value) && headerRows.value >= 0 && headerRows.value <= rows.value)

const previewLines = computed<Point[][] | null>(() => {
  if (!canSave.value || props.polygon.points.length < 3) return null
  const model = createGridTableModel({
    tableId: props.polygon.id,
    outline: props.polygon.points.map(({ x, y }) => [x, y]),
    rows: rows.value,
    columns: columns.value,
    headerRows: headerRows.value,
    newCellId: (row, col) => `${row}-${col}`
  })
  return interiorGridLines(model).map(line => line.map(([x, y]) => ({ x, y })))
})

defineExpose({ previewLines })

const save = (): void => {
  if (!canSave.value) return
  emit('close', {
    rows: rows.value,
    columns: columns.value,
    headerRows: headerRows.value,
    moveTextLines: moveTextLines.value
  })
}
const cancel = () => emit('close', null)
</script>

<template>
  <UiResponsiveSlideover
    :open="true"
    :modal="false"
    :close="{ onClick: cancel }"
  >
    <template #header>
      <UiSlideoverHeader :title="isConversion ? 'Convert to Table' : 'Create Table Grid'" icon="i-lucide-table" />
    </template>

    <template #body>
      <UForm :id="formId" class="flex flex-col gap-4" @submit="save">
        <div class="grid grid-cols-2 gap-3">
          <UFormField label="Rows">
            <UInput
              v-model.number="rows"
              type="number"
              :min="1"
              :max="MAX_SIZE"
            />
          </UFormField>
          <UFormField label="Columns">
            <UInput
              v-model.number="columns"
              type="number"
              :min="1"
              :max="MAX_SIZE"
            />
          </UFormField>
        </div>
        <UFormField label="Header rows" description="Cells in these rows are marked as headers.">
          <UInput
            v-model.number="headerRows"
            type="number"
            :min="0"
            :max="rows"
          />
        </UFormField>
        <UCheckbox
          v-if="textLineCount > 0"
          v-model="moveTextLines"
          :label="`Move ${textLineCount} text line${textLineCount === 1 ? '' : 's'} into the cells`"
        />
      </UForm>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="cancel">
          Cancel
        </UButton>
        <UButton type="submit" :form="formId" :disabled="!canSave">
          Create
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
