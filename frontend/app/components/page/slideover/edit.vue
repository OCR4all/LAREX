<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'

interface Props {
  projectId: string
  page: {
    id: string
    name: string
    description: string | null
    tags: string[]
  }
  editable?: {
    name?: boolean
    description?: boolean
    tags?: boolean
  }
}

const props = withDefaults(defineProps<Props>(), {
  editable: () => ({ name: true, description: true, tags: true })
})
const emit = defineEmits<{ close: [boolean] }>()

const workspace = useWorkspaceStore()

await workspace.validateAndSelectWorkspace()

const selectedWorkspace = computed(() => workspace.selectedWorkspaceId as string)
const projectPagesKey = computed(() => wsKey(selectedWorkspace.value, 'projects', props.projectId, 'pages'))

const canEditName = computed(() => props.editable?.name ?? true)
const canEditDescription = computed(() => props.editable?.description ?? true)
const canEditTags = computed(() => props.editable?.tags ?? true)

const { data: project } = await useFetch<{ tagSetId: string | null }>(
  () => `/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}`,
  { key: wsKey(selectedWorkspace.value, 'projects', props.projectId) }
)

const schema = z.object({
  name: canEditName.value
    ? z.string().trim().min(1, { error: 'Name is required' })
    : z.string().optional(),
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
const formId = useId()

async function onSubmit(event: FormSubmitEvent<Schema>) {
  if (isSubmitting.value) return

  try {
    isSubmitting.value = true

    const body: { name?: string, description?: string | null, tags?: string[] } = {}
    if (canEditName.value) body.name = event.data.name
    if (canEditDescription.value) body.description = event.data.description || null
    if (canEditTags.value) body.tags = event.data.tags

    await $fetch(`/api/projects/${props.projectId}/pages/${props.page.id}`, {
      method: 'PUT',
      body
    })

    toast.add({
      title: 'Page updated successfully',
      color: 'success',
      icon: 'i-lucide-check'
    })

    await refreshNuxtData(projectPagesKey.value)

    emit('close', true)
  } catch (error: unknown) {
    toast.add({
      title: 'Failed to update page',
      description: extractApiErrorMessage(error, 'An error occurred'),
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <UiResponsiveSlideover
    :close="{ onClick: () => emit('close', false) }"
  >
    <template #header>
      <UiSlideoverHeader
        :title="`Edit ${page.name}`"
        icon="i-lucide-edit"
        description="Update the page name, description, and organizational tags."
      />
    </template>

    <template #body>
      <UForm
        :id="formId"
        :schema="schema"
        :state="state"
        class="space-y-4"
        @submit="onSubmit"
      >
        <UiSlideoverSection
          title="Page Details"
          description="Metadata used to identify and organize this page."
          icon="i-lucide-file-pen-line"
        >
          <div class="space-y-4">
            <UFormField v-if="canEditName" label="Page Name" name="name">
              <UInput
                v-model="state.name"
                placeholder="Enter page name"
                required
              />
            </UFormField>

            <UFormField v-if="canEditDescription" label="Description" name="description">
              <UTextarea
                v-model="state.description"
                placeholder="Enter page description (optional)"
                :rows="3"
              />
            </UFormField>

            <UFormField v-if="canEditTags" label="Tags" name="tags">
              <TagSetTagSelector
                v-model="state.tags"
                :tag-set-id="project?.tagSetId"
                :workspace-id="selectedWorkspace"
                placeholder="Add tags"
              />
            </UFormField>
          </div>
        </UiSlideoverSection>
      </UForm>
    </template>

    <template #footer>
      <UButton
        color="neutral"
        variant="ghost"
        :disabled="isSubmitting"
        @click="emit('close', false)"
      >
        Cancel
      </UButton>
      <UButton
        :form="formId"
        type="submit"
        icon="i-lucide-save"
        :loading="isSubmitting"
      >
        Save Changes
      </UButton>
    </template>
  </UiResponsiveSlideover>
</template>
