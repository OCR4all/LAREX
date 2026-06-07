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
const configurationPanelItems = [
  { label: 'Presets', value: 'presets', slot: 'presets', icon: 'i-lucide-sliders-horizontal' },
  { label: 'Text Variants', value: 'text-variants', slot: 'text-variants', icon: 'i-lucide-text' }
]

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
    @close="emit('close', false)"
  >
    <template #header>
      <UiSlideoverHeader title="Edit Project" icon="i-lucide-edit" />
    </template>

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
                  placeholder="Select a tag set (or use free-form tags)"
                  class="w-full"
                  :disabled="isSubmitting || !!tagSetsError || tagSetsSafe.length === 0"
                />
              </UFormField>
              <UFormField label="Tags" name="tags">
                <TagSetTagSelector
                  :model-value="state.tags ?? []"
                  :tag-set-id="effectiveTagSetId"
                  :workspace-id="workspace.selectedWorkspaceId!"
                  :disabled="isSubmitting"
                  class="w-full"
                  @update:model-value="state.tags = $event"
                />
              </UFormField>
              <UFormField label="Primary Codec" name="codecId" hint="Codec to use for this project">
                <USelect
                  v-model="state.codecId"
                  :items="codecsSafe"
                  placeholder="Select a codec"
                  class="w-full"
                  :disabled="isSubmitting || !!codecsError || codecsSafe.length === 0"
                />
              </UFormField>
              <UFormField label="Label Set" name="labelSetId" hint="Label set to use for this project">
                <USelect
                  v-model="state.labelSetId"
                  :items="labelSetsSafe"
                  placeholder="Select a label set"
                  class="w-full"
                  :disabled="isSubmitting || !!labelSetsError || labelSetsSafe.length === 0"
                />
              </UFormField>
              <UFormField label="Dictionary" name="dictionaryId" hint="Dictionary to validate project GT text against">
                <USelect
                  v-model="state.dictionaryId"
                  :items="dictionariesSafe"
                  placeholder="Select a dictionary"
                  class="w-full"
                  :disabled="isSubmitting || !!dictionariesError || dictionariesSafe.length === 0"
                />
              </UFormField>
              <UFormField label="Normalization Profile" name="normalizationProfileId" hint="Normalize text before search, QA, and export">
                <USelect
                  v-model="state.normalizationProfileId"
                  :items="normalizationProfilesSafe"
                  placeholder="Select a normalization profile"
                  class="w-full"
                  :disabled="isSubmitting || !!normalizationProfilesError || normalizationProfilesSafe.length === 0"
                />
              </UFormField>
              <UFormField label="Validation Ruleset" name="validationRulesetId" hint="QA ruleset for suspicious transcription patterns">
                <USelect
                  v-model="state.validationRulesetId"
                  :items="validationRulesetsSafe"
                  placeholder="Select a validation ruleset"
                  class="w-full"
                  :disabled="isSubmitting || !!validationRulesetsError || validationRulesetsSafe.length === 0"
                />
              </UFormField>
              <UFormField label="Virtual Keyboard" name="virtualKeyboardId" hint="Default virtual keyboard for text editing">
                <USelect
                  v-model="state.virtualKeyboardId"
                  :items="virtualKeyboardsSafe"
                  placeholder="Select a virtual keyboard"
                  class="w-full"
                  :disabled="isSubmitting || !!virtualKeyboardsError || virtualKeyboardsSafe.length === 0"
                />
              </UFormField>
              <div class="rounded-sm border border-default divide-y divide-default">
                <div class="px-3 py-2">
                  <p class="text-sm font-medium">Editor Tool Overrides</p>
                  <p class="text-xs text-muted">Allow editors to temporarily use a different resource than the project default.</p>
                </div>
                <div class="p-3 grid gap-3">
                  <USwitch
                    v-model="state.allowCodecOverride"
                    label="Allow codec switching"
                    :disabled="isSubmitting || !canSetProjectPresets"
                  />
                  <USwitch
                    v-model="state.allowDictionaryOverride"
                    label="Allow dictionary switching"
                    :disabled="isSubmitting || !canSetProjectPresets"
                  />
                  <USwitch
                    v-model="state.allowVirtualKeyboardOverride"
                    label="Allow virtual keyboard switching"
                    :disabled="isSubmitting || !canSetProjectPresets"
                  />
                  <USwitch
                    v-model="state.allowLabelSetOverride"
                    label="Allow label set switching"
                    :disabled="isSubmitting || !canSetProjectPresets"
                  />
                  <USwitch
                    v-model="state.allowTagSetOverride"
                    label="Allow tag set switching"
                    :disabled="isSubmitting || !canSetProjectPresets"
                  />
                  <USwitch
                    v-model="state.allowNormalizationProfileOverride"
                    label="Allow normalization profile switching"
                    :disabled="isSubmitting || !canSetProjectPresets"
                  />
                  <USwitch
                    v-model="state.allowValidationRulesetOverride"
                    label="Allow validation ruleset switching"
                    :disabled="isSubmitting || !canSetProjectPresets"
                  />
                </div>
              </div>
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
                <div class="flex items-center gap-3">
                  <UInputTags
                    v-model="state.defaultRecognitionIndicesInput"
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
  </UiResponsiveSlideover>
</template>
