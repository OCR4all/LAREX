<script setup lang="ts">
import { z } from 'zod'
import type { FormSubmitEvent } from '#ui/types'
import type { CodecSummary } from '@/types/codec'
import type { DictionarySummary } from '@/types/dictionary'
import type { LabelSetSummary } from '~/types/label-set'
import type { NormalizationProfileSummary } from '~/types/normalization-profile'
import type { TagSetSummary } from '~/types/tag-set'
import type { ValidationRulesetSummary } from '~/types/validation-ruleset'
import type { KeyboardLayout } from '@/types/virtual-keyboard'

const UNDEFINED_RECOGNITION_SENTINEL = -1
type SelectOption = { label: string, value: string }

interface Project {
  id: string
  name: string
  description: string
  tags: string[]
  codecId?: string
  labelSetId?: string
  dictionaryId?: string
  tagSetId?: string
  normalizationProfileId?: string
  validationRulesetId?: string
  virtualKeyboardId?: string
  allowCodecOverride?: boolean
  allowDictionaryOverride?: boolean
  allowVirtualKeyboardOverride?: boolean
  allowLabelSetOverride?: boolean
  allowTagSetOverride?: boolean
  allowNormalizationProfileOverride?: boolean
  allowValidationRulesetOverride?: boolean
  defaultGtIndex?: number | null
  defaultRecognitionIndices?: number[] | null
}
type WorkspaceDefaults = {
  defaultGtIndex?: number | null
}

const props = defineProps<{ project: Project }>()
const emit = defineEmits<{ close: [updated: boolean], updated: [project: Project] }>()

const workspace = useWorkspaceStore()
const toast = useToast()
const { refreshProjectCaches } = useDataRefresh()

const schema = z.object({
  name: z.string().trim().min(1, { error: 'Required' }).max(100),
  description: z.string().max(500).optional().or(z.literal('')),
  tags: z.array(z.string()).optional(),
  codecId: z.string().optional().or(z.literal('')),
  labelSetId: z.string().optional().or(z.literal('')),
  dictionaryId: z.string().optional().or(z.literal('')),
  tagSetId: z.string().optional().or(z.literal('')),
  normalizationProfileId: z.string().optional().or(z.literal('')),
  validationRulesetId: z.string().optional().or(z.literal('')),
  virtualKeyboardId: z.string().optional().or(z.literal('')),
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

const state = ref<Schema>({
  name: props.project.name,
  description: props.project.description || '',
  tags: props.project.tags || [],
  codecId: props.project.codecId || '',
  labelSetId: props.project.labelSetId || '',
  dictionaryId: props.project.dictionaryId || '',
  tagSetId: props.project.tagSetId || '',
  normalizationProfileId: props.project.normalizationProfileId || '',
  validationRulesetId: props.project.validationRulesetId || '',
  virtualKeyboardId: props.project.virtualKeyboardId || '',
  allowCodecOverride: props.project.allowCodecOverride !== false,
  allowDictionaryOverride: props.project.allowDictionaryOverride !== false,
  allowVirtualKeyboardOverride: props.project.allowVirtualKeyboardOverride !== false,
  allowLabelSetOverride: props.project.allowLabelSetOverride !== false,
  allowTagSetOverride: props.project.allowTagSetOverride !== false,
  allowNormalizationProfileOverride: props.project.allowNormalizationProfileOverride !== false,
  allowValidationRulesetOverride: props.project.allowValidationRulesetOverride !== false,
  defaultGtIndexInput: String(props.project.defaultGtIndex ?? 0),
  defaultGtIndexUndefined: props.project.defaultGtIndex == null,
  defaultRecognitionIndicesInput: Array.isArray(props.project.defaultRecognitionIndices) && props.project.defaultRecognitionIndices.length > 0
    ? props.project.defaultRecognitionIndices.filter(index => index !== UNDEFINED_RECOGNITION_SENTINEL).map(index => String(index))
    : ['1'],
  defaultRecognitionIndicesUndefined: Array.isArray(props.project.defaultRecognitionIndices)
    ? props.project.defaultRecognitionIndices.includes(UNDEFINED_RECOGNITION_SENTINEL)
    : false
})

const { data: codecs, error: codecsError } = await useFetch<CodecSummary[]>(
  `/api/workspaces/${workspace.selectedWorkspaceId}/codecs`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'codecs', 'list'),
    default: () => []
  }
)

const { data: labelSets, error: labelSetsError } = await useFetch<LabelSetSummary[]>(
  `/api/workspaces/${workspace.selectedWorkspaceId}/label-sets`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'label-sets', 'list'),
    default: () => []
  }
)

const { data: dictionaries, error: dictionariesError } = await useFetch<DictionarySummary[]>(
  `/api/workspaces/${workspace.selectedWorkspaceId}/dictionaries`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'dictionaries', 'list'),
    default: () => []
  }
)

const { data: tagSets, error: tagSetsError } = await useFetch<TagSetSummary[]>(
  `/api/workspaces/${workspace.selectedWorkspaceId}/tag-sets`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'tag-sets', 'list'),
    default: () => []
  }
)

const { data: normalizationProfiles, error: normalizationProfilesError } = await useFetch<NormalizationProfileSummary[]>(
  `/api/workspaces/${workspace.selectedWorkspaceId}/normalization-profiles`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'normalization-profiles', 'list'),
    default: () => []
  }
)

const { data: validationRulesets, error: validationRulesetsError } = await useFetch<ValidationRulesetSummary[]>(
  `/api/workspaces/${workspace.selectedWorkspaceId}/validation-rulesets`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'validation-rulesets', 'list'),
    default: () => []
  }
)

const { data: virtualKeyboards, error: virtualKeyboardsError } = await useFetch<KeyboardLayout[]>(
  `/api/workspaces/${workspace.selectedWorkspaceId}/virtual-keyboards`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'virtual-keyboards', 'list'),
    default: () => []
  }
)

const codecsSafe = computed<SelectOption[]>(() => (codecs.value ?? []).map(codec => ({ label: codec.name, value: codec.id })))
const labelSetsSafe = computed<SelectOption[]>(() => (labelSets.value ?? []).map(set => ({ label: set.meta.name, value: set.id })))
const dictionariesSafe = computed<SelectOption[]>(() => (dictionaries.value ?? []).map(dictionary => ({ label: dictionary.name, value: dictionary.id })))
const tagSetsSafe = computed<SelectOption[]>(() => (tagSets.value ?? []).map(tagSet => ({ label: tagSet.meta.name, value: tagSet.id })))
const normalizationProfilesSafe = computed<SelectOption[]>(() => (normalizationProfiles.value ?? []).map(profile => ({ label: profile.name, value: profile.id })))
const validationRulesetsSafe = computed<SelectOption[]>(() => (validationRulesets.value ?? []).map(ruleset => ({ label: ruleset.name, value: ruleset.id })))
const virtualKeyboardsSafe = computed<SelectOption[]>(() => (virtualKeyboards.value ?? []).map(keyboard => ({ label: keyboard.name, value: keyboard.id })))

const { data: workspaceDefaults } = await useFetch<WorkspaceDefaults>(
  `/api/workspaces/${workspace.selectedWorkspaceId}`,
  {
    key: wsKey(workspace.selectedWorkspaceId!, 'details')
  }
)

const effectiveTagSetId = computed(() => state.value.tagSetId || null)
const canSetProjectPresets = computed(() => workspace.currentWorkspace?.capabilities?.canSetPresets ?? workspace.isCurrentUserOwner)
const canEditTextIndexDefaults = computed(() => canSetProjectPresets.value)
const openConfigurationPanels = ref<string[]>([])
const editorOverridesOpen = ref(false)
const formId = 'edit-project-form'
const configurationPanelItems = [
  { label: 'Text Variants', value: 'text-variants', slot: 'text-variants', icon: 'i-lucide-text' }
]
const enabledEditorOverrideCount = computed(() => [
  state.value.allowCodecOverride,
  state.value.allowDictionaryOverride,
  state.value.allowVirtualKeyboardOverride,
  state.value.allowLabelSetOverride,
  state.value.allowTagSetOverride,
  state.value.allowNormalizationProfileOverride,
  state.value.allowValidationRulesetOverride
].filter(value => value !== false).length)

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
    .map((v) => {
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
        dictionaryId: event.data.dictionaryId || null,
        tagSetId: event.data.tagSetId || null,
        normalizationProfileId: event.data.normalizationProfileId || null,
        validationRulesetId: event.data.validationRulesetId || null,
        virtualKeyboardId: event.data.virtualKeyboardId || null,
        allowCodecOverride: event.data.allowCodecOverride !== false,
        allowDictionaryOverride: event.data.allowDictionaryOverride !== false,
        allowVirtualKeyboardOverride: event.data.allowVirtualKeyboardOverride !== false,
        allowLabelSetOverride: event.data.allowLabelSetOverride !== false,
        allowTagSetOverride: event.data.allowTagSetOverride !== false,
        allowNormalizationProfileOverride: event.data.allowNormalizationProfileOverride !== false,
        allowValidationRulesetOverride: event.data.allowValidationRulesetOverride !== false,
        ...(defaultGtIndex !== undefined ? { defaultGtIndex } : {}),
        ...(defaultRecognitionIndices.length > 0 ? { defaultRecognitionIndices } : {})
      }
    })
    await refreshProjectCaches(workspace.selectedWorkspaceId, props.project.id)
    toast.add({ title: 'Project Updated', color: 'success', icon: 'i-lucide-check' })
    emit('updated', response)
    emit('close', true)
  } catch (error: unknown) {
    toast.add({ title: 'Update Failed', description: error instanceof Error ? error.message : 'Failed to update project', color: 'error' })
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <UiResponsiveSlideover
    inset
    :ui="{ content: 'max-w-none xl:max-w-2xl' }"
    @close="emit('close', false)"
  >
    <template #header>
      <UiSlideoverHeader
        title="Edit Project"
        icon="i-lucide-edit"
        description="Update project details, defaults, and text behavior."
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
        <UCard
          variant="subtle"
        >
          <template #header>
            <div class="flex items-start gap-3">
              <div class="flex size-9 shrink-0 items-center justify-center rounded-md bg-accented">
                <UIcon name="i-lucide-file-pen-line" class="size-4 text-muted" />
              </div>
              <div class="min-w-0">
                <h3 class="text-sm font-semibold text-highlighted">
                  General
                </h3>
                <p class="mt-1 text-sm text-muted">
                  The name and description shown throughout the workspace.
                </p>
              </div>
            </div>
          </template>

          <div class="grid gap-5">
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
          </div>
        </UCard>

        <UCard
          variant="subtle"
        >
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
            <UFormField
              label="Tag Set"
              name="tagSetId"
              help="Defines the tag structure available to this project."
            >
              <USelect
                v-model="state.tagSetId"
                :items="tagSetsSafe"
                placeholder="Select a tag set"
                class="w-full"
                :disabled="isSubmitting || !!tagSetsError || tagSetsSafe.length === 0"
              />
            </UFormField>
            <UFormField
              label="Tags"
              name="tags"
              help="Tags used to organize and filter the project."
            >
              <TagSetTagSelector
                :model-value="state.tags ?? []"
                :tag-set-id="effectiveTagSetId"
                :workspace-id="workspace.selectedWorkspaceId!"
                :disabled="isSubmitting"
                class="w-full"
                @update:model-value="state.tags = $event"
              />
            </UFormField>
            <UFormField
              label="Primary Codec"
              name="codecId"
              help="The default codec for this project."
            >
              <USelect
                v-model="state.codecId"
                :items="codecsSafe"
                placeholder="Select a codec"
                class="w-full"
                :disabled="isSubmitting || !!codecsError || codecsSafe.length === 0"
              />
            </UFormField>
            <UFormField
              label="Label Set"
              name="labelSetId"
              help="The labels available during editing."
            >
              <USelect
                v-model="state.labelSetId"
                :items="labelSetsSafe"
                placeholder="Select a label set"
                class="w-full"
                :disabled="isSubmitting || !!labelSetsError || labelSetsSafe.length === 0"
              />
            </UFormField>
            <UFormField
              label="Dictionary"
              name="dictionaryId"
              help="Validates project ground-truth text."
            >
              <USelect
                v-model="state.dictionaryId"
                :items="dictionariesSafe"
                placeholder="Select a dictionary"
                class="w-full"
                :disabled="isSubmitting || !!dictionariesError || dictionariesSafe.length === 0"
              />
            </UFormField>
            <UFormField
              label="Virtual Keyboard"
              name="virtualKeyboardId"
              help="The default keyboard layout for text editing."
            >
              <USelect
                v-model="state.virtualKeyboardId"
                :items="virtualKeyboardsSafe"
                placeholder="Select a virtual keyboard"
                class="w-full"
                :disabled="isSubmitting || !!virtualKeyboardsError || virtualKeyboardsSafe.length === 0"
              />
            </UFormField>
            <UFormField
              label="Normalization Profile"
              name="normalizationProfileId"
              help="Normalizes text before search, QA, and export."
            >
              <USelect
                v-model="state.normalizationProfileId"
                :items="normalizationProfilesSafe"
                placeholder="Select a normalization profile"
                class="w-full"
                :disabled="isSubmitting || !!normalizationProfilesError || normalizationProfilesSafe.length === 0"
              />
            </UFormField>
            <UFormField
              label="Validation Ruleset"
              name="validationRulesetId"
              help="Flags suspicious transcription patterns."
            >
              <USelect
                v-model="state.validationRulesetId"
                :items="validationRulesetsSafe"
                placeholder="Select a validation ruleset"
                class="w-full"
                :disabled="isSubmitting || !!validationRulesetsError || validationRulesetsSafe.length === 0"
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
                    :disabled="isSubmitting || state.defaultGtIndexUndefined === true || !canEditTextIndexDefaults"
                  />
                  <UCheckbox
                    v-model="state.defaultGtIndexUndefined"
                    label="Undefined"
                    :disabled="isSubmitting || !canEditTextIndexDefaults"
                  />
                </div>
              </UFormField>
              <UFormField label="Default Recognition Indices" name="defaultRecognitionIndicesInput" help="Recognition indices used in the text editor; multiple values are allowed.">
                <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
                  <UInputTags
                    v-model="state.defaultRecognitionIndicesInput"
                    class="flex-1"
                    placeholder="Add indices (e.g. 1, 2)"
                    :disabled="isSubmitting || !canEditTextIndexDefaults"
                  />
                  <UCheckbox
                    v-model="state.defaultRecognitionIndicesUndefined"
                    label="Undefined"
                    :disabled="isSubmitting || !canEditTextIndexDefaults"
                  />
                </div>
              </UFormField>
              <p v-if="!canEditTextIndexDefaults" class="text-xs text-muted">
                You do not have permission to change project text-index defaults.
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
              :disabled="isSubmitting"
              @click="editorOverridesOpen = true"
            >
              Configure
            </UButton>
          </div>
        </UCard>
      </UForm>

      <UiResponsiveSlideover
        v-model:open="editorOverridesOpen"
        inset
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
          <div class="space-y-4">
            <p v-if="!canSetProjectPresets" class="rounded-lg bg-elevated px-4 py-3 text-sm text-muted">
              You do not have permission to change project tool overrides.
            </p>

            <div class="divide-y divide-default overflow-hidden rounded-lg border border-default">
              <USwitch
                v-model="state.allowCodecOverride"
                label="Allow codec switching"
                description="Editors may select a different codec."
                class="p-4"
                :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
                :disabled="isSubmitting || !canSetProjectPresets"
              />
              <USwitch
                v-model="state.allowDictionaryOverride"
                label="Allow dictionary switching"
                description="Editors may select a different dictionary."
                class="p-4"
                :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
                :disabled="isSubmitting || !canSetProjectPresets"
              />
              <USwitch
                v-model="state.allowVirtualKeyboardOverride"
                label="Allow virtual keyboard switching"
                description="Editors may select a different keyboard layout."
                class="p-4"
                :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
                :disabled="isSubmitting || !canSetProjectPresets"
              />
              <USwitch
                v-model="state.allowLabelSetOverride"
                label="Allow label set switching"
                description="Editors may select a different label set."
                class="p-4"
                :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
                :disabled="isSubmitting || !canSetProjectPresets"
              />
              <USwitch
                v-model="state.allowTagSetOverride"
                label="Allow tag set switching"
                description="Editors may select a different tag set."
                class="p-4"
                :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
                :disabled="isSubmitting || !canSetProjectPresets"
              />
              <USwitch
                v-model="state.allowNormalizationProfileOverride"
                label="Allow normalization profile switching"
                description="Editors may select a different normalization profile."
                class="p-4"
                :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
                :disabled="isSubmitting || !canSetProjectPresets"
              />
              <USwitch
                v-model="state.allowValidationRulesetOverride"
                label="Allow validation ruleset switching"
                description="Editors may select a different validation ruleset."
                class="p-4"
                :ui="{ root: 'w-full flex-row-reverse items-center justify-between gap-4', wrapper: 'ms-0 flex-1' }"
                :disabled="isSubmitting || !canSetProjectPresets"
              />
            </div>
          </div>
        </template>

        <template #footer>
          <UButton @click="editorOverridesOpen = false">
            Done
          </UButton>
        </template>
      </UiResponsiveSlideover>
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
