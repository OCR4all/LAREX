<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import type { CodecSummary } from '~/types/codec'
import type { DictionarySummary } from '~/types/dictionary'
import type { LabelSetSummary } from '~/types/label-set'
import type { NormalizationProfileSummary } from '~/types/normalization-profile'
import type { TagSetSummary } from '~/types/tag-set'
import type { ValidationRulesetSummary } from '~/types/validation-ruleset'
import type { KeyboardLayout } from '@/types/virtual-keyboard'

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
  virtualKeyboardId: z.string().optional(),
  allowCodecOverride: z.boolean().optional(),
  allowDictionaryOverride: z.boolean().optional(),
  allowVirtualKeyboardOverride: z.boolean().optional(),
  allowLabelSetOverride: z.boolean().optional(),
  allowTagSetOverride: z.boolean().optional(),
  allowNormalizationProfileOverride: z.boolean().optional(),
  allowValidationRulesetOverride: z.boolean().optional(),
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
  virtualKeyboardId: undefined,
  allowCodecOverride: true,
  allowDictionaryOverride: true,
  allowVirtualKeyboardOverride: true,
  allowLabelSetOverride: true,
  allowTagSetOverride: true,
  allowNormalizationProfileOverride: true,
  allowValidationRulesetOverride: true,
  defaultGtIndexInput: '0',
  defaultGtIndexUndefined: true,
  defaultRecognitionIndicesInput: ['1'],
  defaultRecognitionIndicesUndefined: false
})

const formId = useId()

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

const virtualKeyboardsKey = computed(() => wsKey(selectedWorkspace.value, 'virtual-keyboards', 'list'))
const { data: virtualKeyboards, error: virtualKeyboardsError } = await useFetch<KeyboardLayout[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/virtual-keyboards`,
  {
    key: virtualKeyboardsKey,
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
const virtualKeyboardsSafe = computed<SelectOption[]>(() => (virtualKeyboards.value ?? []).map(keyboard => ({
  label: keyboard.name,
  value: keyboard.id
})))

const hasAppliedLabelSetDefault = ref(false)
const hasAppliedTextIndexDefaults = ref(false)
const canEditTextIndexDefaults = computed(() => workspace.currentWorkspace?.capabilities?.canSetPresets ?? workspace.isCurrentUserOwner)
const openConfigurationPanels = ref<string[]>([])
const editorOverridesOpen = ref(false)
const configurationPanelItems = [
  { label: 'Text Variants', value: 'text-variants', slot: 'text-variants', icon: 'i-lucide-text' }
]
const enabledEditorOverrideCount = computed(() => [
  state.allowCodecOverride,
  state.allowDictionaryOverride,
  state.allowVirtualKeyboardOverride,
  state.allowLabelSetOverride,
  state.allowTagSetOverride,
  state.allowNormalizationProfileOverride,
  state.allowValidationRulesetOverride
].filter(value => value !== false).length)

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
      virtualKeyboardId: event.data.virtualKeyboardId,
      allowCodecOverride: event.data.allowCodecOverride !== false,
      allowDictionaryOverride: event.data.allowDictionaryOverride !== false,
      allowVirtualKeyboardOverride: event.data.allowVirtualKeyboardOverride !== false,
      allowLabelSetOverride: event.data.allowLabelSetOverride !== false,
      allowTagSetOverride: event.data.allowTagSetOverride !== false,
      allowNormalizationProfileOverride: event.data.allowNormalizationProfileOverride !== false,
      allowValidationRulesetOverride: event.data.allowValidationRulesetOverride !== false,
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
    const errorMessage = extractApiErrorMessage(error.value, 'An error occurred')
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
  <UiResponsiveSlideover
    :ui="{ content: 'max-w-none xl:max-w-2xl' }"
    :close="{ onClick: () => emit('close', false) }"
  >
    <template #header>
      <UiSlideoverHeader
        title="Create Project"
        icon="i-lucide-package-plus"
        description="Configure your new project"
      />
    </template>

    <template #body>
      <UForm
        :id="formId"
        :schema="schema"
        :state="state"
        class="space-y-6"
        @submit="onSubmit"
      >
        <UCard variant="subtle">
          <template #header>
            <div class="flex items-start gap-3">
              <div class="flex size-9 shrink-0 items-center justify-center rounded-md bg-accented">
                <UIcon name="i-lucide-file-plus-2" class="size-4 text-muted" />
              </div>
              <div class="min-w-0">
                <h3 class="text-sm font-semibold text-highlighted">
                  General
                </h3>
                <p class="mt-1 text-sm text-muted">
                  The name, description, and tags shown throughout the workspace.
                </p>
              </div>
            </div>
          </template>

          <div class="grid gap-5">
            <UFormField label="Name" name="name" required>
              <UInput v-model="state.name" placeholder="Enter project name" />
            </UFormField>

            <UFormField label="Description" name="description">
              <UInput v-model="state.description" placeholder="Brief description of your project" />
            </UFormField>

            <UFormField label="Tags" name="tags">
              <UInputTags v-model="state.tags" icon="i-lucide-tags" placeholder="Categorize your project via tags" />
            </UFormField>
          </div>
        </UCard>

        <UCard variant="subtle">
          <template #header>
            <div class="flex items-start gap-3">
              <div class="flex size-9 shrink-0 items-center justify-center rounded-md bg-accented">
                <UIcon name="i-lucide-sliders-horizontal" class="size-4 text-muted" />
              </div>
              <div class="min-w-0">
                <h3 class="text-sm font-semibold text-highlighted">
                  Project Defaults
                </h3>
                <p class="mt-1 text-sm text-muted">
                  Choose the resources and tools editors use when working in this project.
                </p>
              </div>
            </div>
          </template>

          <div class="grid grid-cols-1 gap-x-5 gap-y-5 sm:grid-cols-2">
            <UFormField label="Tag Set" name="tagSetId" help="Defines the tag structure available to this project.">
              <USelect
                v-model="state.tagSetId"
                :items="tagSetsSafe"
                placeholder="Select a tag set"
                :disabled="!!tagSetsError || tagSetsSafe.length === 0"
              />
            </UFormField>

            <UFormField label="Codec" name="codecId" help="The default codec for this project.">
              <USelect
                v-model="state.codecId"
                :items="codecsSafe"
                placeholder="Select a codec"
                :disabled="!!codecsError || codecsSafe.length === 0"
              />
            </UFormField>

            <UFormField label="Label Set" name="labelSetId" help="The labels available during editing.">
              <USelect
                v-model="state.labelSetId"
                :items="labelSetsSafe"
                placeholder="Select a label set"
                :disabled="!!labelSetsError || labelSetsSafe.length === 0"
              />
            </UFormField>

            <UFormField label="Dictionary" name="dictionaryId" help="Validates project ground-truth text.">
              <USelect
                v-model="state.dictionaryId"
                :items="dictionariesSafe"
                placeholder="Select a dictionary"
                :disabled="!!dictionariesError || dictionariesSafe.length === 0"
              />
            </UFormField>

            <UFormField label="Normalization Profile" name="normalizationProfileId" help="Normalizes text before QA and export.">
              <USelect
                v-model="state.normalizationProfileId"
                :items="normalizationProfilesSafe"
                placeholder="Select a normalization profile"
                :disabled="!!normalizationProfilesError || normalizationProfilesSafe.length === 0"
              />
            </UFormField>

            <UFormField label="Validation Ruleset" name="validationRulesetId" help="Flags suspicious transcription patterns.">
              <USelect
                v-model="state.validationRulesetId"
                :items="validationRulesetsSafe"
                placeholder="Select a validation ruleset"
                :disabled="!!validationRulesetsError || validationRulesetsSafe.length === 0"
              />
            </UFormField>

            <UFormField label="Virtual Keyboard" name="virtualKeyboardId" help="The default keyboard layout for text editing.">
              <USelect
                v-model="state.virtualKeyboardId"
                :items="virtualKeyboardsSafe"
                placeholder="Select a virtual keyboard"
                :disabled="!!virtualKeyboardsError || virtualKeyboardsSafe.length === 0"
              />
            </UFormField>
          </div>
        </UCard>

        <UAccordion
          v-model="openConfigurationPanels"
          :items="configurationPanelItems"
          type="multiple"
          class="rounded-lg bg-elevated/50 px-4 ring ring-default"
          :ui="{
            trigger: 'py-4 font-semibold',
            body: 'pb-0'
          }"
        >
          <template #leading>
            <div class="flex size-9 shrink-0 items-center justify-center rounded-md bg-accented">
              <UIcon name="i-lucide-text" class="size-4 text-muted" />
            </div>
          </template>

          <template #text-variants>
            <div class="space-y-5 pb-4">
              <UFormField label="Default GT Index" name="defaultGtIndexInput" help="The single ground-truth index used in the text editor.">
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
                help="Recognition indices used in the text editor; multiple values are allowed."
              >
                <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
                  <UInputTags
                    v-model="state.defaultRecognitionIndicesInput"
                    class="flex-1"
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

        <UCard variant="subtle" :ui="{ body: 'p-4 sm:p-4' }">
          <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div class="flex min-w-0 items-start gap-3">
              <div class="flex size-9 shrink-0 items-center justify-center rounded-md bg-accented">
                <UIcon name="i-lucide-settings-2" class="size-4 text-muted" />
              </div>
              <div class="min-w-0">
                <p class="text-sm font-medium text-highlighted">
                  Editor Tool Overrides
                </p>
                <p class="mt-0.5 text-sm text-muted">
                  {{ enabledEditorOverrideCount }} of 7 overrides enabled
                </p>
              </div>
            </div>
            <UButton
              type="button"
              color="neutral"
              variant="outline"
              trailing-icon="i-lucide-chevron-right"
              @click="() => { editorOverridesOpen = true }"
            >
              Configure
            </UButton>
          </div>
        </UCard>
      </UForm>

      <UiResponsiveSlideover
        v-model:open="editorOverridesOpen"
        :ui="{ content: 'max-w-none xl:max-w-md' }"
      >
        <template #header>
          <UiSlideoverHeader
            title="Editor Tool Overrides"
            icon="i-lucide-settings-2"
            description="Choose which project defaults editors may temporarily replace."
          />
        </template>

        <template #body>
          <div class="divide-y divide-default overflow-hidden rounded-lg border border-default">
            <USwitch
              v-model="state.allowCodecOverride"
              label="Allow codec switching"
              description="Editors may select a different codec."
              class="p-4"
              :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
            />
            <USwitch
              v-model="state.allowDictionaryOverride"
              label="Allow dictionary switching"
              description="Editors may select a different dictionary."
              class="p-4"
              :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
            />
            <USwitch
              v-model="state.allowVirtualKeyboardOverride"
              label="Allow virtual keyboard switching"
              description="Editors may select a different keyboard layout."
              class="p-4"
              :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
            />
            <USwitch
              v-model="state.allowLabelSetOverride"
              label="Allow label set switching"
              description="Editors may select a different label set."
              class="p-4"
              :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
            />
            <USwitch
              v-model="state.allowTagSetOverride"
              label="Allow tag set switching"
              description="Editors may select a different tag set."
              class="p-4"
              :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
            />
            <USwitch
              v-model="state.allowNormalizationProfileOverride"
              label="Allow normalization profile switching"
              description="Editors may select a different normalization profile."
              class="p-4"
              :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
            />
            <USwitch
              v-model="state.allowValidationRulesetOverride"
              label="Allow validation ruleset switching"
              description="Editors may select a different validation ruleset."
              class="p-4"
              :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
            />
          </div>
        </template>

        <template #footer>
          <UButton @click="() => { editorOverridesOpen = false }">
            Done
          </UButton>
        </template>
      </UiResponsiveSlideover>
    </template>
    <template #footer>
      <div class="flex justify-end gap-1 pt-4">
        <UButton color="neutral" variant="ghost" @click="emit('close', false)">
          Cancel
        </UButton>
        <UButton
          type="submit"
          :form="formId"
          variant="solid"
          icon="i-lucide-package-plus"
        >
          Submit
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
