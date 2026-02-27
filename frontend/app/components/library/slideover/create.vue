<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import { wsKey } from '@/utils/fetch-keys'
import type { CodecSummary } from '~/types/codec'
import type { LabelSetSummary } from '~/types/label-set'
import type { TagSetSummary } from '~/types/tag-set'

type SelectOption = { label: string, value: string }
type WorkspaceDefaults = {
  labelSetId?: string | null
  defaultGtIndex?: number | null
  defaultRecognitionIndices?: number[] | null
}

const PAGE_XML_STANDARD_LABEL_SET_NAME = 'PAGE XML Standard'
const UNDEFINED_RECOGNITION_SENTINEL = -1

const emit = defineEmits<{ close: [boolean] }>()

const workspace = useWorkspaceStore()
const selectedWorkspace = computed(() => workspace.selectedWorkspaceId as string)
const codecsKey = computed(() => wsKey(selectedWorkspace.value, 'codecs', 'list'))

const projectsListKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', 'list'))

const schema = z.object({
  name: z.preprocess(
    value => typeof value === 'string' ? value : '',
    z.string().trim().min(1, { error: 'Project name is required' }).max(100, { error: 'Name is too long' })
  ),
  description: z.string().optional(),
  tags: z.array(z.string()).optional(),
  codecId: z.string().optional(),
  labelSetId: z.string().optional(),
  tagSetId: z.string().optional(),
  defaultGtIndexInput: z.union([z.string(), z.number()]).optional(),
  defaultGtIndexUndefined: z.boolean().optional(),
  defaultRecognitionIndicesInput: z.array(z.union([z.string(), z.number()])).optional(),
  defaultRecognitionIndicesUndefined: z.boolean().optional()
})

type Schema = z.output<typeof schema>

const state = reactive<Partial<Schema>>({
  name: undefined,
  description: undefined,
  tags: undefined,
  codecId: undefined,
  labelSetId: undefined,
  tagSetId: undefined,
  defaultGtIndexInput: '0',
  defaultGtIndexUndefined: true,
  defaultRecognitionIndicesInput: ['1'],
  defaultRecognitionIndicesUndefined: false
})

const createProjectFormRef = ref<HTMLFormElement | null>(null)
const submit = () => {
  createProjectFormRef.value?.submit()
}

const { data: codecs, error: codecsError } = await useFetch<CodecSummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/codecs`,
  {
    key: codecsKey,
    default: () => [],
    transform: (codecs: CodecSummary[]) => codecs.map(codec => ({
      label: codec.name,
      value: codec.id
    }))
  }
)

const { data: workspaceDetails } = await useFetch<WorkspaceDefaults>(
  () => `/api/workspaces/${selectedWorkspace.value}`,
  {
    key: computed(() => wsKey(selectedWorkspace.value, 'details')),
    watch: [selectedWorkspace],
    immediate: !!selectedWorkspace.value
  }
)

const labelSetsKey = computed(() => wsKey(selectedWorkspace.value, 'label-sets', 'list'))
const { data: labelSets, error: labelSetsError } = await useFetch<SelectOption[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/label-sets`,
  {
    key: labelSetsKey,
    default: () => [],
    transform: (sets: LabelSetSummary[]) => sets.map(s => ({ label: s.meta.name, value: s.id }))
  }
)

const tagSetsKey = computed(() => wsKey(selectedWorkspace.value, 'tag-sets', 'list'))
const { data: tagSets, error: tagSetsError } = await useFetch<TagSetSummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/tag-sets`,
  {
    key: tagSetsKey,
    default: () => [],
    transform: (tagSets: TagSetSummary[]) => tagSets.map(t => ({
      label: t.meta.name,
      value: t.id
    }))
  }
)

const hasAppliedLabelSetDefault = ref(false)
const hasAppliedTextIndexDefaults = ref(false)
const canEditTextIndexDefaults = computed(() => workspace.isCurrentUserOwner)

watch(selectedWorkspace, () => {
  hasAppliedLabelSetDefault.value = false
  hasAppliedTextIndexDefaults.value = false
  state.labelSetId = undefined
  state.defaultGtIndexUndefined = true
  state.defaultRecognitionIndicesUndefined = false
})

watch([workspaceDetails, labelSets], ([workspace, availableLabelSets]) => {
  if (hasAppliedLabelSetDefault.value) return

  const workspaceDefaultId = workspace?.labelSetId
  const workspaceDefaultExists = !!workspaceDefaultId && availableLabelSets.some(item => item.value === workspaceDefaultId)
  const pageXmlDefault = availableLabelSets.find(item => item.label === PAGE_XML_STANDARD_LABEL_SET_NAME)?.value
  const resolvedDefault = workspaceDefaultExists ? workspaceDefaultId : pageXmlDefault

  if (!resolvedDefault) return

  state.labelSetId = resolvedDefault
  hasAppliedLabelSetDefault.value = true
}, { immediate: true })

watch(workspaceDetails, (workspace) => {
  if (hasAppliedTextIndexDefaults.value || !workspace) return

  state.defaultGtIndexInput = String(workspace.defaultGtIndex ?? 0)
  state.defaultRecognitionIndicesInput = Array.isArray(workspace.defaultRecognitionIndices) && workspace.defaultRecognitionIndices.length > 0
    ? workspace.defaultRecognitionIndices.filter(index => index !== UNDEFINED_RECOGNITION_SENTINEL).map(index => String(index))
    : ['1']
  state.defaultRecognitionIndicesUndefined = Array.isArray(workspace.defaultRecognitionIndices)
    ? workspace.defaultRecognitionIndices.includes(UNDEFINED_RECOGNITION_SENTINEL)
    : false
  hasAppliedTextIndexDefaults.value = true
}, { immediate: true })

const toast = useToast()

function parseDefaultGtIndex(value: string | number | undefined): number {
  const parsed = Number.parseInt(String(value ?? '').trim(), 10)
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error('Default GT index must be a non-negative integer.')
  }
  return parsed
}

function parseRecognitionIndices(values: Array<string | number> | undefined, gtIndex: number, includeUndefined: boolean): number[] {
  const parsed = (values ?? [])
    .flatMap(value => String(value).split(','))
    .map(value => value.trim())
    .filter(Boolean)
    .map(value => {
      if (!/^\d+$/.test(value)) {
        throw new Error('Recognition indices must be non-negative integers.')
      }
      return Number.parseInt(value, 10)
    })

  const unique = [...new Set(parsed)].sort((a, b) => a - b)
  if (includeUndefined) unique.unshift(UNDEFINED_RECOGNITION_SENTINEL)
  const normalized = [...new Set(unique)].sort((a, b) => a - b)
  if (normalized.length === 0) {
    throw new Error('Provide at least one recognition index and/or enable Undefined.')
  }
  if (normalized.includes(gtIndex)) {
    throw new Error('Recognition indices must not include the GT index.')
  }
  return normalized
}

async function onSubmit(event: FormSubmitEvent<Schema>) {
  let body: Record<string, unknown>
  try {
    const defaultGtIndexUndefined = state.defaultGtIndexUndefined === true
    const includeUndefinedRecognition = state.defaultRecognitionIndicesUndefined === true

    if (defaultGtIndexUndefined && includeUndefinedRecognition) {
      throw new Error('Undefined cannot be selected for both GT and Recognition indices.')
    }

    const parsedGtIndex = defaultGtIndexUndefined
      ? undefined
      : parseDefaultGtIndex(state.defaultGtIndexInput)

    const effectiveGtIndexForValidation = parsedGtIndex ?? (workspaceDetails.value?.defaultGtIndex ?? 0)
    const parsedRecognitionIndices = parseRecognitionIndices(
      state.defaultRecognitionIndicesInput,
      effectiveGtIndexForValidation,
      includeUndefinedRecognition
    )

    body = {
      name: event.data.name,
      description: event.data.description,
      tags: event.data.tags,
      codecId: event.data.codecId,
      labelSetId: event.data.labelSetId,
      tagSetId: event.data.tagSetId,
      ...(parsedGtIndex !== undefined ? { defaultGtIndex: parsedGtIndex } : {}),
      ...(parsedRecognitionIndices.length > 0 ? { defaultRecognitionIndices: parsedRecognitionIndices } : {})
    }
  } catch (e: any) {
    toast.add({
      title: 'Invalid Text Index Defaults',
      description: e.message || 'Please check the default GT and recognition indices.',
      color: 'error'
    })
    return
  }

  const { data, error } = await useFetch<{ id: string }>(`/api/workspaces/${selectedWorkspace.value}/projects`, {
    method: 'POST',
    body
  })

  if (error.value) {
    const errorMessage = error.value.data?.message || error.value.message || 'An error occurred'
    toast.add({
      title: 'Error',
      description: errorMessage,
      color: 'error'
    })
    return
  }

  const createdProjectId = data.value?.id
  toast.add({ title: 'Success', description: 'Project has been created.', color: 'success' })

  if (createdProjectId) {
    await navigateTo(`/project/${createdProjectId}`)
    emit('close', true)
    return
  }

  await refreshNuxtData(projectsListKey.value)
  emit('close', true)
}
</script>

<template>
  <USlideover
    title="Create Project"
    description="Configure your new project"
    :close="{ onClick: () => emit('close', false) }"
  >
    <template #body>
      <UForm
        ref="createProjectFormRef"
        :schema="schema"
        :state="state"
        class="space-y-4"
        @submit="onSubmit"
      >
        <UiFormSectionHeader title="Basic Info" />
        <UFormField label="Name" name="name" required>
          <UInput v-model="state.name" placeholder="Enter project name" />
        </UFormField>

        <UFormField label="Description" name="description">
          <UInput v-model="state.description" placeholder="Brief description of your project" />
        </UFormField>

        <UFormField label="Tags" name="tags">
          <UInputTags v-model="state.tags" icon="i-lucide-tags" placeholder="Categorize your project via tags" />
        </UFormField>

        <UiFormSectionHeader title="Presets" />

        <UFormField label="Tag Set" name="tagSetId" hint="Tag structure to use for this project">
          <USelect
            v-model="state.tagSetId"
            :items="tagSets"
            placeholder="Select a tag set"
            :disabled="!!tagSetsError || tagSets.length === 0"
          />
        </UFormField>

        <UFormField label="Codec" name="codecId" hint="Codec to use for this project">
          <USelect
            v-model="state.codecId"
            :items="codecs"
            placeholder="Select a codec"
            :disabled="!!codecsError || codecs.length === 0"
          />
        </UFormField>

        <UFormField label="Label Set" name="labelSetId" hint="Label set to use for this project">
          <USelect
            v-model="state.labelSetId"
            :items="labelSets"
            placeholder="Select a label set"
            :disabled="!!labelSetsError || labelSets.length === 0"
          />
        </UFormField>

        <UiFormSectionHeader title="Text Variants" />

        <UFormField label="Default GT Index" name="defaultGtIndexInput" hint="Single Ground Truth index used in the text editor.">
          <div class="flex items-center gap-3">
            <UInput
              v-model="state.defaultGtIndexInput"
              type="number"
              min="0"
              step="1"
              class="flex-1"
              placeholder="0"
              :disabled="state.defaultGtIndexUndefined === true || !canEditTextIndexDefaults"
            />
            <UCheckbox
              v-model="state.defaultGtIndexUndefined"
              label="Undefined"
              :disabled="!canEditTextIndexDefaults"
            />
          </div>
        </UFormField>

        <UFormField
          label="Default Recognition Indices"
          name="defaultRecognitionIndicesInput"
          hint="Recognition indices used in the text editor (multiple allowed)."
        >
          <div class="space-y-2">
            <UInputTags
              v-model="state.defaultRecognitionIndicesInput"
              placeholder="Add indices (e.g. 1, 2)"
              :disabled="!canEditTextIndexDefaults"
            />
            <UCheckbox
              v-model="state.defaultRecognitionIndicesUndefined"
              label="Include Undefined"
              :disabled="!canEditTextIndexDefaults"
            />
          </div>
        </UFormField>

        <p v-if="!canEditTextIndexDefaults" class="text-xs text-muted">
          Only the workspace owner can set project text-index defaults.
        </p>
      </UForm>
    </template>
    <template #footer>
      <div class="flex justify-end gap-1 pt-4">
        <UButton color="neutral" variant="ghost" @click="emit('close', false)">
          Cancel
        </UButton>
        <UButton variant="solid" icon="i-lucide-package-plus" @click="submit">
          Submit
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
