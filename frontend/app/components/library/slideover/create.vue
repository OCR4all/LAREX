<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import { wsKey } from '@/utils/fetch-keys'
import type { CodecSummary } from '~/types/codec'
import type { DictionarySummary } from '~/types/dictionary'
import type { LabelSetSummary } from '~/types/label-set'
import type { NormalizationProfileSummary } from '~/types/normalization-profile'
import type { TagSetSummary } from '~/types/tag-set'
import type { ValidationRulesetSummary } from '~/types/validation-ruleset'

type SelectOption = { label: string, value: string }
type WorkspaceDefaults = {
  labelSetId?: string | null
  normalizationProfileId?: string | null
  validationRulesetId?: string | null
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
  dictionaryId: z.string().optional(),
  tagSetId: z.string().optional(),
  normalizationProfileId: z.string().optional(),
  validationRulesetId: z.string().optional(),
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
  dictionaryId: undefined,
  tagSetId: undefined,
  normalizationProfileId: undefined,
  validationRulesetId: undefined,
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
    default: () => []
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

const dictionariesKey = computed(() => wsKey(selectedWorkspace.value, 'dictionaries', 'list'))
const { data: dictionaries, error: dictionariesError } = await useFetch<DictionarySummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/dictionaries`,
  {
    key: dictionariesKey,
    default: () => []
  }
)

const labelSetsKey = computed(() => wsKey(selectedWorkspace.value, 'label-sets', 'list'))
const { data: labelSets, error: labelSetsError } = await useFetch<LabelSetSummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/label-sets`,
  {
    key: labelSetsKey,
    default: () => []
  }
)

const tagSetsKey = computed(() => wsKey(selectedWorkspace.value, 'tag-sets', 'list'))
const { data: tagSets, error: tagSetsError } = await useFetch<TagSetSummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/tag-sets`,
  {
    key: tagSetsKey,
    default: () => []
  }
)

const normalizationProfilesKey = computed(() => wsKey(selectedWorkspace.value, 'normalization-profiles', 'list'))
const { data: normalizationProfiles, error: normalizationProfilesError } = await useFetch<NormalizationProfileSummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/normalization-profiles`,
  {
    key: normalizationProfilesKey,
    default: () => []
  }
)

const validationRulesetsKey = computed(() => wsKey(selectedWorkspace.value, 'validation-rulesets', 'list'))
const { data: validationRulesets, error: validationRulesetsError } = await useFetch<ValidationRulesetSummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/validation-rulesets`,
  {
    key: validationRulesetsKey,
    default: () => []
  }
)

const codecsSafe = computed<SelectOption[]>(() => (codecs.value ?? []).map(codec => ({
  label: codec.name,
  value: codec.id
})))
const dictionariesSafe = computed<SelectOption[]>(() => (dictionaries.value ?? []).map(dictionary => ({
  label: dictionary.name,
  value: dictionary.id
})))
const labelSetsSafe = computed<SelectOption[]>(() => (labelSets.value ?? []).map(set => ({
  label: set.meta.name,
  value: set.id
})))
const tagSetsSafe = computed<SelectOption[]>(() => (tagSets.value ?? []).map(tagSet => ({
  label: tagSet.meta.name,
  value: tagSet.id
})))
const normalizationProfilesSafe = computed<SelectOption[]>(() => (normalizationProfiles.value ?? []).map(profile => ({
  label: profile.name,
  value: profile.id
})))
const validationRulesetsSafe = computed<SelectOption[]>(() => (validationRulesets.value ?? []).map(ruleset => ({
  label: ruleset.name,
  value: ruleset.id
})))

const hasAppliedLabelSetDefault = ref(false)
const hasAppliedTextIndexDefaults = ref(false)
const canEditTextIndexDefaults = computed(() => workspace.isCurrentUserOwner)
const openConfigurationPanels = ref<string[]>([])
const configurationPanelItems = [
  { label: 'Presets', value: 'presets', slot: 'presets', icon: 'i-lucide-sliders-horizontal' },
  { label: 'Text Variants', value: 'text-variants', slot: 'text-variants', icon: 'i-lucide-text' }
]

watch(selectedWorkspace, () => {
  hasAppliedLabelSetDefault.value = false
  hasAppliedTextIndexDefaults.value = false
  state.labelSetId = undefined
  state.defaultGtIndexUndefined = true
  state.defaultRecognitionIndicesUndefined = false
})

watch([workspaceDetails, labelSetsSafe], ([workspace, availableLabelSets]) => {
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
  if (!workspace) return
  if (state.normalizationProfileId == null && workspace.normalizationProfileId) {
    state.normalizationProfileId = workspace.normalizationProfileId
  }
  if (state.validationRulesetId == null && workspace.validationRulesetId) {
    state.validationRulesetId = workspace.validationRulesetId
  }
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
    .map((value) => {
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
      dictionaryId: event.data.dictionaryId,
      tagSetId: event.data.tagSetId,
      normalizationProfileId: event.data.normalizationProfileId,
      validationRulesetId: event.data.validationRulesetId,
      ...(parsedGtIndex !== undefined ? { defaultGtIndex: parsedGtIndex } : {}),
      ...(parsedRecognitionIndices.length > 0 ? { defaultRecognitionIndices: parsedRecognitionIndices } : {})
    }
  } catch (e: unknown) {
    toast.add({
      title: 'Invalid Text Index Defaults',
      description: e instanceof Error ? e.message : 'Please check the default GT and recognition indices.',
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

        <UAccordion
          v-model="openConfigurationPanels"
          :items="configurationPanelItems"
          type="multiple"
        >
          <template #presets>
            <div class="space-y-4 p-1">
              <UFormField label="Tag Set" name="tagSetId" hint="Tag structure to use for this project">
                <USelect
                  v-model="state.tagSetId"
                  :items="tagSetsSafe"
                  placeholder="Select a tag set"
                  :disabled="!!tagSetsError || tagSetsSafe.length === 0"
                />
              </UFormField>

              <UFormField label="Codec" name="codecId" hint="Codec to use for this project">
                <USelect
                  v-model="state.codecId"
                  :items="codecsSafe"
                  placeholder="Select a codec"
                  :disabled="!!codecsError || codecsSafe.length === 0"
                />
              </UFormField>

              <UFormField label="Label Set" name="labelSetId" hint="Label set to use for this project">
                <USelect
                  v-model="state.labelSetId"
                  :items="labelSetsSafe"
                  placeholder="Select a label set"
                  :disabled="!!labelSetsError || labelSetsSafe.length === 0"
                />
              </UFormField>

              <UFormField label="Dictionary" name="dictionaryId" hint="Dictionary to validate project GT text against">
                <USelect
                  v-model="state.dictionaryId"
                  :items="dictionariesSafe"
                  placeholder="Select a dictionary"
                  :disabled="!!dictionariesError || dictionariesSafe.length === 0"
                />
              </UFormField>
              <UFormField label="Normalization Profile" name="normalizationProfileId" hint="Normalize text before QA and export">
                <USelect
                  v-model="state.normalizationProfileId"
                  :items="normalizationProfilesSafe"
                  placeholder="Select a normalization profile"
                  :disabled="!!normalizationProfilesError || normalizationProfilesSafe.length === 0"
                />
              </UFormField>
              <UFormField label="Validation Ruleset" name="validationRulesetId" hint="Run project text QA with this ruleset">
                <USelect
                  v-model="state.validationRulesetId"
                  :items="validationRulesetsSafe"
                  placeholder="Select a validation ruleset"
                  :disabled="!!validationRulesetsError || validationRulesetsSafe.length === 0"
                />
              </UFormField>
            </div>
          </template>

          <template #text-variants>
            <div class="space-y-4 p-1">
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
                <div class="flex items-center gap-3">
                  <UInputTags
                    v-model="state.defaultRecognitionIndicesInput"
                    placeholder="Add indices (e.g. 1, 2)"
                    :disabled="!canEditTextIndexDefaults"
                  />
                  <UCheckbox
                    v-model="state.defaultRecognitionIndicesUndefined"
                    label="Undefined"
                    :disabled="!canEditTextIndexDefaults"
                  />
                </div>
              </UFormField>

              <p v-if="!canEditTextIndexDefaults" class="text-xs text-muted">
                Only the workspace owner can set project text-index defaults.
              </p>
            </div>
          </template>
        </UAccordion>
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
