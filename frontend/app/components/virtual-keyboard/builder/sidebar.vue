<script setup lang="ts">
const props = defineProps<{
  state: VirtualKeyboardBuilderState
}>()

const { gridCols, gridRows, items, layoutName, layoutDesc, tags, changeGridCols, changeGridRows } = props.state
</script>

<template>
  <aside data-tour="vk-builder-sidebar" class="w-80 bg-neutral-50/30 dark:bg-neutral-800/50 border-r border-neutral-200 dark:border-neutral-700 flex flex-col shrink-0">
    <div class="flex-1 p-4 space-y-4 overflow-y-auto">
      <UFormField label="Name">
        <UInput v-model="layoutName" placeholder="e.g. Latin Extended" />
      </UFormField>

      <UFormField label="Description">
        <UTextarea v-model="layoutDesc" placeholder="Describe this keyboard layout..." :rows="3" />
      </UFormField>

      <UFormField label="Tags">
        <UInputTags v-model="tags" placeholder="Add tags..." />
      </UFormField>

      <div class="pt-4 border-t border-neutral-200 dark:border-neutral-700 space-y-4">
        <UFormField label="Grid Size">
          <div class="flex items-center justify-between rounded-sm border border-neutral-200 dark:border-neutral-700 p-3 gap-x-4">
            <div class="flex flex-col items-center flex-1">
              <span class="text-xs font-bold mb-2 text-neutral-500">Cols</span>
              <div class="flex items-center gap-1">
                <UButton
                  color="neutral"
                  icon="i-lucide-minus"
                  variant="outline"
                  size="xs"
                  @click="changeGridCols(-1)"
                />
                <span class="w-6 text-center font-mono font-bold text-sm">{{ gridCols }}</span>
                <UButton
                  color="neutral"
                  icon="i-lucide-plus"
                  variant="outline"
                  size="xs"
                  @click="changeGridCols(1)"
                />
              </div>
            </div>
            <USeparator orientation="vertical" color="neutral" class="h-10" />
            <div class="flex flex-col items-center flex-1">
              <span class="text-xs font-bold mb-2 text-neutral-500">Rows</span>
              <div class="flex items-center gap-1">
                <UButton
                  color="neutral"
                  icon="i-lucide-minus"
                  variant="outline"
                  size="xs"
                  @click="changeGridRows(-1)"
                />
                <span class="w-6 text-center font-mono font-bold text-sm">{{ gridRows }}</span>
                <UButton
                  color="neutral"
                  icon="i-lucide-plus"
                  variant="outline"
                  size="xs"
                  @click="changeGridRows(1)"
                />
              </div>
            </div>
          </div>
        </UFormField>
      </div>

      <div class="pt-4 border-t border-neutral-200 dark:border-neutral-700">
        <div class="text-sm text-neutral-600 dark:text-neutral-400">
          <div class="flex justify-between">
            <span>Keys Placed:</span>
            <span class="font-medium">{{ items.length }}</span>
          </div>
          <div class="flex justify-between mt-1">
            <span>Total Cells:</span>
            <span class="font-medium">{{ gridCols * gridRows }}</span>
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>
