<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import { wsKey } from '@/utils/fetch-keys'

interface Props {
  projectId: string
  page: {
    id: string
    name: string
    description: string | null
    tags: string[]
  }
}

const props = defineProps<Props>()
const emit = defineEmits<{ close: [boolean] }>()

const workspace = useWorkspaceStore()

await workspace.validateAndSelectWorkspace()

const selectedWorkspace = computed(() => workspace.selectedWorkspaceId as string)
const projectPagesKey = computed(() => wsKey(selectedWorkspace.value, 'projects', props.projectId, 'pages'))

const { data: project } = await useFetch<{ tagSetId: string | null }>(
  () => `/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}`,
  { key: wsKey(selectedWorkspace.value, 'projects', props.projectId) }
)

const schema = z.object({
  name: z.string().trim().min(1, { error: 'Name is required' }),
  description: z.string().optional(),
  tags: z.array(z.string()).default([])
})

type Schema = z.output<typeof schema>

const state = reactive<Schema>({
  name: props.page.name,
  description: props.page.description || '',
  tags: [...props.page.tags]
})

const toast = useToast()
const isSubmitting = ref(false)

async function onSubmit(event: FormSubmitEvent<Schema>) {
  if (isSubmitting.value) return

  try {
    isSubmitting.value = true

    await $fetch(`/api/projects/${props.projectId}/pages/${props.page.id}`, {
      method: 'PUT',
      body: event.data
    })

    toast.add({
      title: 'Page updated successfully',
      color: 'success',
      icon: 'i-lucide-check'
    })

    await refreshNuxtData(projectPagesKey.value)

    emit('close', true)
  } catch (error: any) {
    toast.add({
      title: 'Failed to update page',
      description: error.message || 'An error occurred',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <USlideover
    :title="`Edit ${page.name}`"
    :close="{ onClick: () => emit('close', false) }"
  >
    <template #body>
      <UForm
        :schema="schema"
        :state="state"
        class="space-y-4"
        @submit="onSubmit"
      >
        <UFormField label="Page Name" name="name">
          <UInput
            v-model="state.name"
            placeholder="Enter page name"
            required
          />
        </UFormField>

        <UFormField label="Description" name="description">
          <UTextarea
            v-model="state.description"
            placeholder="Enter page description (optional)"
            :rows="3"
          />
        </UFormField>

        <UFormField label="Tags" name="tags">
          <TagSetTagSelector
            v-model="state.tags"
            :tag-set-id="project?.tagSetId"
            :workspace-id="selectedWorkspace"
            placeholder="Add tags"
          />
        </UFormField>

        <div class="flex gap-2 justify-end pt-4">
          <UButton
            color="neutral"
            variant="outline"
            :disabled="isSubmitting"
            @click="emit('close', false)"
          >
            Cancel
          </UButton>
          <UButton
            type="submit"
            color="primary"
            variant="subtle"
            icon="i-lucide-save"
            :loading="isSubmitting"
          >
            Save Changes
          </UButton>
        </div>
      </UForm>
    </template>
  </USlideover>
</template>
