<script setup lang="ts">
import type { LabelSetImportPreview } from '@/utils/label-set-import-preview'
import { useBlockEditorCanvasInteractions } from '@/composables/editor/use-canvas-interaction-blocker'

const props = defineProps<{
  preview: LabelSetImportPreview
}>()

const emit = defineEmits<{
  close: [result: boolean]
}>()

const isOpen = ref(true)
useBlockEditorCanvasInteractions(isOpen)

const errorCount = computed(() => props.preview.issues.filter(issue => issue.level === 'error').length
  + props.preview.labelSets.reduce((count, labelSet) => count + labelSet.issues.filter(issue => issue.level === 'error').length, 0))

function close(result: boolean) {
  emit('close', result)
  isOpen.value = false
}
</script>

<template>
  <UModal
    v-model:open="isOpen"
    title="Review label set import"
    description="Nothing is imported until you confirm this preview."
    :ui="{ content: 'sm:max-w-2xl' }"
    @close="close(false)"
  >
    <template #body>
      <div class="max-h-[65vh] space-y-4 overflow-y-auto pr-1">
        <div class="flex items-center justify-between gap-3 rounded-lg border border-default bg-muted/30 px-4 py-3">
          <div class="min-w-0">
            <p class="truncate text-sm font-medium text-highlighted">
              {{ preview.fileName }}
            </p>
            <p class="mt-0.5 text-xs text-muted">
              {{ preview.labelSets.length }} label set{{ preview.labelSets.length === 1 ? '' : 's' }} found
            </p>
          </div>
          <UBadge :color="preview.canImport ? 'success' : 'error'" variant="subtle">
            {{ preview.canImport ? 'Ready' : `${errorCount} error${errorCount === 1 ? '' : 's'}` }}
          </UBadge>
        </div>

        <UAlert
          color="info"
          variant="subtle"
          icon="i-lucide-info"
          title="The open label set will not be overwritten"
          description="A new set is created. If the same name already exists, identical content is reused; otherwise the imported copy receives a unique name."
        />

        <UAlert
          v-for="issue in preview.issues"
          :key="`${issue.level}-${issue.message}`"
          :color="issue.level === 'error' ? 'error' : 'warning'"
          variant="subtle"
          :icon="issue.level === 'error' ? 'i-lucide-circle-x' : 'i-lucide-triangle-alert'"
          :title="issue.message"
        />

        <section
          v-for="labelSet in preview.labelSets"
          :key="labelSet.name"
          class="space-y-3 rounded-lg border border-default p-4"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h3 class="font-semibold text-highlighted">
                {{ labelSet.name }}
              </h3>
              <p class="mt-0.5 text-xs text-muted">
                {{ labelSet.labelCount }} labels · {{ labelSet.groupCount }} groups
              </p>
            </div>
            <UBadge v-if="labelSet.nameConflict" color="warning" variant="subtle">
              Name conflict
            </UBadge>
          </div>

          <div v-if="labelSet.comparison" class="grid grid-cols-2 gap-2 sm:grid-cols-5">
            <div class="rounded-md bg-success/10 px-2 py-2 text-center">
              <div class="text-sm font-semibold text-success">
                {{ labelSet.comparison.added }}
              </div>
              <div class="text-[10px] text-muted">
                Added
              </div>
            </div>
            <div class="rounded-md bg-error/10 px-2 py-2 text-center">
              <div class="text-sm font-semibold text-error">
                {{ labelSet.comparison.removed }}
              </div>
              <div class="text-[10px] text-muted">
                Missing
              </div>
            </div>
            <div class="rounded-md bg-warning/10 px-2 py-2 text-center">
              <div class="text-sm font-semibold text-warning">
                {{ labelSet.comparison.changed }}
              </div>
              <div class="text-[10px] text-muted">
                Changed
              </div>
            </div>
            <div class="rounded-md bg-muted px-2 py-2 text-center">
              <div class="text-sm font-semibold text-default">
                {{ labelSet.comparison.orderChanged ? 'Yes' : 'No' }}
              </div>
              <div class="text-[10px] text-muted">
                Reordered
              </div>
            </div>
            <div class="rounded-md bg-muted px-2 py-2 text-center">
              <div class="text-sm font-semibold text-default">
                {{ labelSet.comparison.metadataChanged ? 'Yes' : 'No' }}
              </div>
              <div class="text-[10px] text-muted">
                Metadata
              </div>
            </div>
          </div>

          <div>
            <p class="mb-1.5 text-xs font-medium text-muted">
              Label order
            </p>
            <ol class="grid gap-1 text-xs text-default sm:grid-cols-2">
              <li v-for="(name, index) in labelSet.labelNames.slice(0, 10)" :key="`${index}-${name}`" class="truncate">
                <span class="mr-1.5 tabular-nums text-muted">{{ index + 1 }}.</span>{{ name }}
              </li>
            </ol>
            <p v-if="labelSet.labelNames.length > 10" class="mt-1 text-xs text-muted">
              +{{ labelSet.labelNames.length - 10 }} more labels
            </p>
          </div>

          <div v-if="labelSet.issues.length > 0" class="rounded-md border border-error/30 bg-error/10 p-3">
            <p class="mb-2 text-xs font-semibold text-error">
              Import errors
            </p>
            <ul class="space-y-1 text-xs text-error">
              <li v-for="issue in labelSet.issues.slice(0, 10)" :key="`${issue.labelName}-${issue.message}`">
                <strong v-if="issue.labelName">{{ issue.labelName }}:</strong> {{ issue.message }}
              </li>
            </ul>
            <p v-if="labelSet.issues.length > 10" class="mt-2 text-xs text-error">
              +{{ labelSet.issues.length - 10 }} more errors
            </p>
          </div>
        </section>

        <div v-if="preview.otherResources.length > 0" class="rounded-lg border border-warning/30 bg-warning/10 p-4">
          <p class="mb-2 text-xs font-semibold text-warning">
            Additional toolkit resources
          </p>
          <ul class="space-y-1 text-xs text-default">
            <li v-for="resource in preview.otherResources" :key="`${resource.type}-${resource.name}`">
              {{ resource.name }} <span class="text-muted">({{ resource.type }})</span>
            </li>
          </ul>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex w-full justify-end gap-2">
        <UButton color="neutral" variant="outline" @click="close(false)">
          Cancel
        </UButton>
        <UButton
          color="primary"
          icon="i-lucide-upload"
          :disabled="!preview.canImport"
          @click="close(true)"
        >
          Import package
        </UButton>
      </div>
    </template>
  </UModal>
</template>
