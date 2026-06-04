<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'
import type { PageData } from '@/stores/editor/types'
import type { PageResponse } from '@/services/editor/project-loader'
import { createPageSortOrderRequest } from '@/utils/editor/page-sort'

const props = defineProps<{
  projectId: string
  projectName: string
  pages: PageData[]
}>()

const emit = defineEmits<{ close: [PageResponse[] | null] }>()

const toast = useToast()
const orderedPages = ref<PageData[]>([])
const isSaving = ref(false)

function getErrorMessage(error: unknown): string | undefined {
  if (error && typeof error === 'object') {
    const candidate = error as { data?: { message?: unknown }, message?: unknown }
    if (typeof candidate.data?.message === 'string') return candidate.data.message
    if (typeof candidate.message === 'string') return candidate.message
  }
  return undefined
}

watch(() => props.pages, (pages) => {
  orderedPages.value = pages.map(page => ({ ...page }))
}, { immediate: true })

const hasChanged = computed(() => {
  const originalIds = props.pages.map(page => page.id).join('\u0000')
  const currentIds = orderedPages.value.map(page => page.id).join('\u0000')
  return originalIds !== currentIds
})

function cancel() {
  emit('close', null)
}

async function saveOrder() {
  if (!hasChanged.value || isSaving.value) {
    emit('close', null)
    return
  }

  isSaving.value = true
  try {
    const response = await $fetch<PageResponse[]>(`/api/projects/${props.projectId}/pages/sort-order`, {
      method: 'PUT',
      body: createPageSortOrderRequest(orderedPages.value)
    })
    toast.add({ title: 'Page order saved', color: 'success', icon: 'i-lucide-check' })
    emit('close', response)
  } catch (error: unknown) {
    toast.add({
      title: 'Failed to save page order',
      description: getErrorMessage(error),
      color: 'error',
      icon: 'i-lucide-triangle-alert'
    })
  } finally {
    isSaving.value = false
  }
}
</script>

<template>
  <UiResponsiveSlideover :close="{ onClick: cancel }">
    <template #header>
      <UiSlideoverHeader title="Edit Page Order" icon="i-lucide-list-ordered" />
    </template>

    <template #body>
      <div class="flex min-h-0 flex-col gap-3">
        <div class="text-sm text-muted">
          {{ projectName }}
        </div>

        <VueDraggable
          v-model="orderedPages"
          item-key="id"
          handle=".page-order-drag-handle"
          :animation="150"
          ghost-class="opacity-50"
          class="flex min-h-0 flex-col gap-1 overflow-y-auto"
        >
          <div
            v-for="(page, index) in orderedPages"
            :key="page.id"
            class="group flex min-h-10 items-center gap-2 rounded-md px-2 py-1.5 hover:bg-elevated/50"
          >
            <UButton
              icon="i-lucide-grip-vertical"
              color="neutral"
              variant="ghost"
              size="xs"
              square
              class="page-order-drag-handle cursor-grab"
              aria-label="Drag page"
            />
            <span class="w-8 shrink-0 text-right text-xs tabular-nums text-muted">{{ index + 1 }}</span>
            <span class="min-w-0 flex-1 truncate text-sm">{{ page.label }}</span>
          </div>
        </VueDraggable>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          :disabled="isSaving"
          @click="cancel"
        >
          Cancel
        </UButton>
        <UButton
          icon="i-lucide-save"
          :loading="isSaving"
          :disabled="!hasChanged"
          @click="saveOrder"
        >
          Save Order
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
