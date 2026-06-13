<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import type { DatasetCreateReleaseRequest, DatasetRelease } from '@/types/dataset'

const props = defineProps<{
  datasetId: string
  suggestedTag?: string
}>()

const emit = defineEmits<{ close: [string | null] }>()

const toast = useToast()
const backgroundDownloads = useBackgroundDownloads()
const { selectedWorkspace } = await useWorkspaceBootstrap()

const schema = z.object({
  versionTag: z.string().max(128, { error: 'Release tag is too long' }).optional().or(z.literal('')),
  notes: z.string().max(4000, { error: 'Release notes are too long' }).optional().or(z.literal(''))
})

type Schema = z.output<typeof schema>

const state = reactive<Schema>({
  versionTag: props.suggestedTag || '',
  notes: ''
})

const formRef = ref<HTMLFormElement | null>(null)
const creating = ref(false)

const submit = () => formRef.value?.submit()

async function onSubmit(event: FormSubmitEvent<Schema>) {
  if (!selectedWorkspace.value) return

  const workspaceId = selectedWorkspace.value
  creating.value = true
  const payload: DatasetCreateReleaseRequest = {
    versionTag: event.data.versionTag?.trim() || null,
    notes: event.data.notes?.trim() || null
  }

  try {
    const release = await backgroundDownloads.runBackgroundJob({
      title: 'Creating dataset release',
      subtitle: payload.versionTag || 'Next release',
      statusLabel: 'Generating',
      completedLabel: 'Created',
      icon: 'i-lucide-package-plus',
      retryable: false,
      task: async () => await $fetch<DatasetRelease>(`/api/workspaces/${workspaceId}/datasets/${props.datasetId}/releases`, {
        method: 'POST',
        body: payload
      })
    })

    toast.add({
      title: 'Release created',
      description: `${release.versionTag} is now frozen and downloadable.`,
      color: 'success'
    })

    emit('close', release.id)
  } catch (error: unknown) {
    toast.add({
      title: 'Release failed',
      description: extractApiErrorMessage(error, 'Failed to create release'),
      color: 'error'
    })
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <UiResponsiveSlideover
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #header>
      <UiSlideoverHeader title="Create Release" icon="i-lucide-plus" />
    </template>

    <template #body>
      <UForm
        ref="formRef"
        :schema="schema"
        :state="state"
        class="space-y-5"
        @submit="onSubmit"
      >
        <UFormField
          label="Release tag"
          name="versionTag"
          hint="Leave blank to use the next version tag automatically."
        >
          <UInput
            v-model="state.versionTag"
            placeholder="e.g. v3 or baseline-2026-03"
          />
        </UFormField>

        <UFormField label="Release notes" name="notes">
          <UTextarea
            v-model="state.notes"
            :rows="6"
            placeholder="What changed in this release and what should training jobs know?"
          />
        </UFormField>
      </UForm>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="emit('close', null)">
          Cancel
        </UButton>
        <UButton
          color="primary"
          icon="i-lucide-tag"
          :loading="creating"
          @click="submit"
        >
          Create Release
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
