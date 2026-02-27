<script setup lang="ts">
import { z } from 'zod'
import type { FormSubmitEvent } from '#ui/types'
import { wsKey } from '@/utils/fetch-keys'

import type { LabelSetSummary } from '~/types/label-set'
const UNDEFINED_RECOGNITION_SENTINEL = -1

interface Project {
  id: string
  name: string
  description: string
  tags: string[]
  codecId?: string
  labelSetId?: string
  tagSetId?: string
  defaultGtIndex?: number | null
  defaultRecognitionIndices?: number[] | null
}
type WorkspaceDefaults = {
  defaultGtIndex?: number | null
}

const props = defineProps<{ project: Project }>()
const emit = defineEmits<{ close: [boolean], updated: [project: Project] }>()

const workspace = useWorkspaceStore()
const toast = useToast()

const schema = z.object({
  name: z.string().trim().min(1, { error: 'Required' }).max(100),
  description: z.string().max(500).optional().or(z.literal('')),
  tags: z.array(z.string()).optional(),
  codecId: z.string().optional().or(z.literal('')),
  labelSetId: z.string().optional().or(z.literal('')),
  tagSetId: z.string().optional().or(z.literal('')),
  defaultGtIndexInput: z.union([z.string(), z.number()]).optional(),
  defaultGtIndexUndefined: z.boolean().optional(),
  defaultRecognitionIndicesInput: z.array(z.union([z.string(), z.number()])).optional(),
  defaultRecognitionIndicesUndefined: z.boolean().optional()
})

type Schema = z.output<typeof schema>

const state = ref<Schema>({
  name: props.project.name,
  description: props.project.description || '',
  tags: props.project.tags || [],
  codecId: props.project.codecId || '',
  labelSetId: props.project.labelSetId || '',
  tagSetId: props.project.tagSetId || '',
  defaultGtIndexInput: String(props.project.defaultGtIndex ?? 0),
  defaultGtIndexUndefined: props.project.defaultGtIndex == null,
  defaultRecognitionIndicesInput: Array.isArray(props.project.defaultRecognitionIndices) && props.project.defaultRecognitionIndices.length > 0
    ? props.project.defaultRecognitionIndices.filter(index => index !== UNDEFINED_RECOGNITION_SENTINEL).map(index => String(index))
    : ['1'],
  defaultRecognitionIndicesUndefined: Array.isArray(props.project.defaultRecognitionIndices)
    ? props.project.defaultRecognitionIndices.includes(UNDEFINED_RECOGNITION_SENTINEL)
    : false
})

const { data: codecs, error: codecsError } = await useFetch<any[]>(
  `/api/workspaces/${workspace.selectedWorkspaceId}/codecs`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'codecs', 'list'),
    default: () => [],
    transform: (codecs: any[]) => codecs.map(codec => ({ label: codec.name, value: codec.id }))
  }
)

const { data: labelSets, error: labelSetsError } = await useFetch<LabelSetSummary[]>(
  `/api/workspaces/${workspace.selectedWorkspaceId}/label-sets`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'label-sets', 'list'),
    default: () => [],
    transform: (sets: LabelSetSummary[]) => sets.map(s => ({ label: s.meta.name, value: s.id }))
  }
)

const { data: tagSets, error: tagSetsError } = await useFetch<any[]>(
  `/api/workspaces/${workspace.selectedWorkspaceId}/tag-sets`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'tag-sets', 'list'),
    default: () => [],
    transform: (tagSets: any[]) => tagSets.map(t => ({ label: t.meta.name, value: t.id }))
  }
)

const { data: workspaceDefaults } = await useFetch<WorkspaceDefaults>(
  `/api/workspaces/${workspace.selectedWorkspaceId}`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'details')
  }
)

const effectiveTagSetId = computed(() => state.value.tagSetId || null)
const canEditTextIndexDefaults = computed(() => workspace.isCurrentUserOwner)

const isSubmitting = ref(false)

function parseDefaultGtIndex(value: string | number | undefined): number {
  const parsed = Number.parseInt(String(value ?? '').trim(), 10)
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error('Default GT index must be a non-negative integer.')
  }
  return parsed
}

function parseRecognitionIndices(values: Array<string | number> | undefined, gtIndex: number, includeUndefined: boolean): number[] {
  const parsed = (values ?? [])
    .flatMap(v => String(v).split(','))
    .map(v => v.trim())
    .filter(Boolean)
    .map(v => {
      if (!/^\d+$/.test(v)) {
        throw new Error('Recognition indices must be non-negative integers.')
      }
      return Number.parseInt(v, 10)
    })

  const withUndefined = includeUndefined ? [UNDEFINED_RECOGNITION_SENTINEL, ...parsed] : parsed
  const unique = [...new Set(withUndefined)].sort((a, b) => a - b)
  if (unique.length === 0) {
    throw new Error('Provide at least one recognition index and/or enable Undefined.')
  }
  if (unique.includes(gtIndex)) {
    throw new Error('Recognition indices must not include the GT index.')
  }
  return unique
}

async function onSubmit(event: FormSubmitEvent<Schema>) {
  isSubmitting.value = true
  try {
    if (event.data.defaultGtIndexUndefined === true && event.data.defaultRecognitionIndicesUndefined === true) {
      throw new Error('Undefined cannot be selected for both GT and Recognition indices.')
    }

    const defaultGtIndex = event.data.defaultGtIndexUndefined === true
      ? undefined
      : parseDefaultGtIndex(event.data.defaultGtIndexInput)
    const effectiveGtIndexForValidation = defaultGtIndex ?? (workspaceDefaults.value?.defaultGtIndex ?? props.project.defaultGtIndex ?? 0)
    const defaultRecognitionIndices = parseRecognitionIndices(
      event.data.defaultRecognitionIndicesInput,
      effectiveGtIndexForValidation,
      event.data.defaultRecognitionIndicesUndefined === true
    )
    const response = await $fetch<Project>(`/api/workspaces/${workspace.selectedWorkspaceId}/projects/${props.project.id}`, {
      method: 'PUT',
      body: {
        name: event.data.name,
        description: event.data.description || null,
        tags: event.data.tags,
        codecId: event.data.codecId || null,
        labelSetId: event.data.labelSetId || null,
        tagSetId: event.data.tagSetId || null,
        ...(defaultGtIndex !== undefined ? { defaultGtIndex } : {}),
        ...(defaultRecognitionIndices.length > 0 ? { defaultRecognitionIndices } : {})
      }
    })
    toast.add({ title: 'Project Updated', color: 'success', icon: 'i-lucide-check' })
    emit('updated', response)
    emit('close')
  } catch (error: any) {
    toast.add({ title: 'Update Failed', description: error.data?.message || error.message || 'Failed to update project', color: 'error' })
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <USlideover
    title="Edit Project"
    :close="{ onClick: () => emit('close', false) }"
  >
    <template #body>
      <UForm
        :schema="schema"
        :state="state"
        class="space-y-4"
        @submit="onSubmit"
      >
        <UFormField label="Project Name" name="name" required>
          <UInput
            v-model="state.name"
            placeholder="Enter project name"
            :disabled="isSubmitting"
            class="w-full"
          />
        </UFormField>
        <UFormField label="Description" name="description">
          <UTextarea
            v-model="state.description"
            placeholder="Enter description (optional)"
            :rows="3"
            :disabled="isSubmitting"
            class="w-full"
          />
        </UFormField>

        <USeparator />

        <UFormField label="Tag Set" name="tagSetId" hint="Tag structure to use for this project">
          <USelect
            v-model="state.tagSetId"
            :items="tagSets"
            placeholder="Select a tag set (or use free-form tags)"
            class="w-full"
            :disabled="isSubmitting || !!tagSetsError || tagSets.length === 0"
          />
        </UFormField>
        <UFormField label="Tags" name="tags">
          <TagSetTagSelector
            v-model="state.tags"
            :tag-set-id="effectiveTagSetId"
            :workspace-id="workspace.selectedWorkspaceId!"
            :disabled="isSubmitting"
            class="w-full"
          />
        </UFormField>
        <UFormField label="Primary Codec" name="codecId" hint="Codec to use for this project">
          <USelect
            v-model="state.codecId"
            :items="codecs"
            placeholder="Select a codec"
            class="w-full"
            :disabled="isSubmitting || !!codecsError || codecs.length === 0"
          />
        </UFormField>
        <UFormField label="Label Set" name="labelSetId" hint="Label set to use for this project">
          <USelect
            v-model="state.labelSetId"
            :items="labelSets"
            placeholder="Select a label set"
            class="w-full"
            :disabled="isSubmitting || !!labelSetsError || labelSets.length === 0"
          />
        </UFormField>
        <USeparator />
        <UFormField label="Default GT Index" name="defaultGtIndexInput" hint="Single Ground Truth index used in the text editor.">
          <div class="flex items-center gap-3">
            <UInput
              v-model="state.defaultGtIndexInput"
              type="number"
              min="0"
              step="1"
              class="flex-1"
              placeholder="0"
              :disabled="isSubmitting || state.defaultGtIndexUndefined === true || !canEditTextIndexDefaults"
            />
            <UCheckbox
              v-model="state.defaultGtIndexUndefined"
              label="Undefined"
              :disabled="isSubmitting || !canEditTextIndexDefaults"
            />
          </div>
        </UFormField>
        <UFormField label="Default Recognition Indices" name="defaultRecognitionIndicesInput" hint="Recognition indices used in the text editor (multiple allowed).">
          <div class="space-y-2">
            <UInputTags
              v-model="state.defaultRecognitionIndicesInput"
              placeholder="Add indices (e.g. 1, 2)"
              :disabled="isSubmitting || !canEditTextIndexDefaults"
            />
            <UCheckbox
              v-model="state.defaultRecognitionIndicesUndefined"
              label="Include Undefined"
              :disabled="isSubmitting || !canEditTextIndexDefaults"
            />
          </div>
        </UFormField>
        <p v-if="!canEditTextIndexDefaults" class="text-xs text-muted">
          Only the workspace owner can change project text-index defaults.
        </p>
        <UButton
          type="submit"
          icon="i-lucide-save"
          :loading="isSubmitting"
          variant="solid"
        >
          Save
        </UButton>
      </UForm>
    </template>
  </USlideover>
</template>
