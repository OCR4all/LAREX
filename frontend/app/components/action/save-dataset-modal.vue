<script setup lang="ts">
import { pageCountLabel } from '@/composables/use-dataset-run'
import { extractApiErrorMessage } from '@/utils/api-error'
import type { DatasetDetail, DatasetSummary, SaveProjectDatasetRequest } from '@/types/dataset'

const props = defineProps<{
  workspaceId: string | null | undefined
  sourceDataset: DatasetSummary
  itemIds: string[]
}>()

const emit = defineEmits<{ saved: [dataset: DatasetDetail] }>()
const open = defineModel<boolean>('open', { required: true })

const toast = useToast()
const name = ref('')
const saving = ref(false)
const error = ref<string>()

watch(open, (isOpen) => {
  if (!isOpen) return
  name.value = props.sourceDataset.name
  error.value = undefined
})

async function save() {
  if (saving.value || !name.value.trim()) return
  saving.value = true
  error.value = undefined
  try {
    const body: SaveProjectDatasetRequest = { name: name.value.trim(), itemIds: props.itemIds }
    const dataset = await $fetch<DatasetDetail>(
      `/api/workspaces/${props.workspaceId}/project-datasets/${props.sourceDataset.id}/save`,
      { method: 'POST', body }
    )
    emit('saved', dataset)
    open.value = false
    toast.add({
      title: 'Dataset saved',
      description: dataset.name,
      color: 'success',
      actions: [{ label: 'Open dataset', to: `/datasets/${dataset.id}` }]
    })
  } catch (saveError: unknown) {
    error.value = extractApiErrorMessage(saveError, 'Could not save this dataset.')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <UModal
    v-model:open="open"
    title="Save as dataset"
    :description="`Keeps these ${pageCountLabel(itemIds.length)} and their splits. Pages added to the project later won't join this dataset.`"
  >
    <template #body>
      <form id="save-dataset-form" @submit.prevent="save">
        <UFormField label="Name" required :error="error">
          <UInput v-model="name" autofocus class="w-full" />
        </UFormField>
      </form>
    </template>
    <template #footer>
      <div class="flex w-full justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="open = false">
          Cancel
        </UButton>
        <UButton
          type="submit"
          form="save-dataset-form"
          :disabled="!name.trim()"
          :loading="saving"
        >
          Save dataset
        </UButton>
      </div>
    </template>
  </UModal>
</template>
