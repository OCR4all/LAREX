<script setup lang="ts">
import type { ActionRun, ActionRunDetail } from '@/types/action'
import { extractApiErrorMessage } from '@/utils/api-error'

const props = defineProps<{ run: ActionRun }>()
const open = defineModel<boolean>('open', { default: false })
const detail = ref<ActionRunDetail | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

watch([open, () => props.run.id], async ([isOpen]) => {
  if (!isOpen) return
  const id = props.run.id
  loading.value = true
  error.value = null
  detail.value = null
  try {
    const response = await $fetch<ActionRunDetail>(`/api/workspaces/${props.run.workspaceId}/actions/runs/${id}`)
    if (props.run.id === id) detail.value = response
  } catch (cause: unknown) {
    if (props.run.id === id) error.value = extractApiErrorMessage(cause, 'Could not load run report')
  } finally {
    if (props.run.id === id) loading.value = false
  }
}, { immediate: true })
</script>

<template>
  <UModal v-model:open="open" :title="`${run.processorName} report`" :ui="{ content: 'sm:max-w-4xl' }">
    <template #body>
      <USkeleton v-if="loading" class="h-48 w-full" />
      <UAlert v-else-if="error" color="error" :description="error" />
      <ActionRunReport v-else-if="detail?.runReport" :report="detail.runReport" :run-id="run.id" />
      <p v-else class="text-sm text-muted">
        No report available.
      </p>
    </template>
  </UModal>
</template>
