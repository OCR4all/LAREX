<script setup lang="ts">
import { LazyUiDeleteSlideover } from '#components'
import type { CodecSummary } from '@/types/codec'
import type { DictionarySummary } from '@/types/dictionary'
import type { LabelSetSummary } from '@/types/label-set'
import type { NormalizationProfileSummary } from '@/types/normalization-profile'
import type { TagSetSummary } from '@/types/tag-set'
import type { ValidationRulesetSummary } from '@/types/validation-ruleset'
import type { ActionAssignmentResponse, ActionDefinitionResponse } from '@/types/action'

type SelectOption = { label: string, value: string }

interface Workspace {
  id: string
  name: string
  description?: string
  avatar?: string
  ownerUserId: string
  isPersonal: boolean
  type?: 'personal' | 'team'
  codecId?: string
  labelSetId?: string
  dictionaryId?: string
  tagSetId?: string
  normalizationProfileId?: string
  validationRulesetId?: string
  defaultGtIndex?: number | null
  defaultRecognitionIndices?: number[] | null
  created?: string
  updated?: string
}

interface StorageQuota {
  availableBytesFormatted: string
  usagePercentage: number
  isQuotaExceeded: boolean
  currentUsageFormatted: string
  quotaLimitFormatted: string
  reservedBytes: number
  reservedBytesFormatted: string
}

const workspaceStore = useWorkspaceStore()
const toast = useToast()
const overlay = useOverlay()
const { refreshWorkspaceDetails, refreshWorkspaceList } = useDataRefresh()
const { allow } = useActionVisibility()

const deleteSlideover = overlay.create(LazyUiDeleteSlideover)

const selectedWorkspace = computed(() => workspaceStore.selectedWorkspaceId)
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)

const { data: workspace } = await useFetch<Workspace>(
  () => `/api/workspaces/${selectedWorkspace.value as string}`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'details')
      : globalKey('pending', 'workspace', 'details')),
    watch: [selectedWorkspace],
    immediate: !!selectedWorkspace.value
  }
)

const { data: storageQuota } = await useFetch<StorageQuota>(
  () => `/api/storage/quotas/workspace/${selectedWorkspace.value as string}`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'storage', 'quota')
      : globalKey('pending', 'storage', 'quota')),
    watch: [selectedWorkspace],
    immediate: !!selectedWorkspace.value
  }
)

const storageColor = computed(() => {
  if (!storageQuota.value) return 'primary'
  const state = getStorageQuotaAlertState(storageQuota.value)
  if (state === 'exceeded') return 'error'
  if (state === 'warning') return 'warning'
  return 'primary'
})

const storageProgressValue = computed(() => getStorageQuotaProgressValue(storageQuota.value?.usagePercentage ?? 0))
const storageAlertState = computed(() => getStorageQuotaAlertState(storageQuota.value))

const canEditWorkspaceMetadata = computed(() => allow(workspaceCapabilities.value.canEditWorkspace))
const canSetWorkspacePresets = computed(() => allow(workspaceCapabilities.value.canSetPresets))
const canEditWorkspaceSettings = computed(() => canEditWorkspaceMetadata.value || canSetWorkspacePresets.value)
const canDeleteWorkspace = computed(() => allow(workspaceCapabilities.value.canAdminWorkspace))
const isPersonalWorkspace = computed(() =>
  workspace.value?.id === selectedWorkspace.value && workspace.value?.isPersonal === true
)
const canManageWorkspaceActions = computed(() => isPersonalWorkspace.value || allow(workspaceCapabilities.value.canManageProjects))
const canEditWorkspaceTextIndexDefaults = computed(() =>
  canSetWorkspacePresets.value && allow(workspaceCapabilities.value.canEditWorkspaceTextIndexDefaults)
)

const isEditing = ref(false)
const isSaving = ref(false)
const loadingActions = ref(false)
const assigningAction = ref(false)
const actionDefinitions = ref<ActionDefinitionResponse[]>([])
const actionAssignments = ref<ActionAssignmentResponse[]>([])
const workspaceActionProjects = ref<Array<{ id: string, name: string }>>([])
const selectedActionDefinitionIds = ref<string[]>([])
const selectedActionProjectId = ref('')

const form = reactive({
  name: '',
  description: '',
  codecId: '',
  labelSetId: '',
  dictionaryId: '',
  tagSetId: '',
  normalizationProfileId: '',
  validationRulesetId: '',
  defaultGtIndexInput: '0',
  defaultRecognitionIndicesInput: '1'
})

function formatRecognitionIndices(value?: number[] | null): string {
  return Array.isArray(value) && value.length > 0 ? value.join(', ') : '1'
}

function parseDefaultGtIndex(value: string | undefined): number {
  const parsed = Number.parseInt(String(value ?? '').trim(), 10)
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error('Default GT index must be a non-negative integer.')
  }
  return parsed
}

const actionDefinitionOptions = computed(() => actionDefinitions.value
  .filter(definition => !definition.global)
  .filter(definition => !actionAssignments.value.some(assignment => assignment.processor.id === definition.id))
  .map(definition => ({
    label: definition.name,
    value: definition.id
  })))

const inheritedGlobalActionDefinitions = computed(() => actionDefinitions.value.filter(definition => definition.global))

const scopedActionDefinitions = computed(() => actionDefinitions.value.filter(definition => !definition.global))

const actionProjectOptions = computed(() => [
  { label: 'Workspace default', value: '' },
  ...workspaceActionProjects.value.map(project => ({ label: project.name, value: project.id }))
])

async function loadWorkspaceActions() {
  if (!selectedWorkspace.value || !canManageWorkspaceActions.value) return
  loadingActions.value = true
  try {
    const [definitions, assignments] = await Promise.all([
      $fetch<ActionDefinitionResponse[]>(`/api/workspaces/${selectedWorkspace.value}/actions/processors/available`),
      $fetch<ActionAssignmentResponse[]>(`/api/workspaces/${selectedWorkspace.value}/actions/assignments`, {
        query: selectedActionProjectId.value ? { projectId: selectedActionProjectId.value } : undefined
      })
    ])
    actionDefinitions.value = definitions
    actionAssignments.value = assignments
    if (selectedActionDefinitionIds.value.length === 0 && actionDefinitionOptions.value[0]) {
      selectedActionDefinitionIds.value = [actionDefinitionOptions.value[0].value]
    }
    selectedActionDefinitionIds.value = selectedActionDefinitionIds.value.filter(id =>
      actionDefinitionOptions.value.some(option => option.value === id)
    )
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not load Actions.'
    toast.add({ title: 'Failed to load Actions', description: message, color: 'error' })
  } finally {
    loadingActions.value = false
  }
}

async function assignWorkspaceAction() {
  if (!selectedWorkspace.value || selectedActionDefinitionIds.value.length === 0) return
  assigningAction.value = true
  try {
    await Promise.all(selectedActionDefinitionIds.value.map(processorDefinitionId =>
      $fetch(`/api/workspaces/${selectedWorkspace.value}/actions/assignments`, {
        method: 'POST',
        body: {
          processorDefinitionId,
          projectId: selectedActionProjectId.value || null,
          enabled: true
        }
      })
    ))
    selectedActionDefinitionIds.value = []
    await loadWorkspaceActions()
    toast.add({ title: 'Action assigned', color: 'success', icon: 'i-lucide-circle-play' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not assign Action.'
    toast.add({ title: 'Assignment failed', description: message, color: 'error' })
  } finally {
    assigningAction.value = false
  }
}

async function unassignWorkspaceAction(assignmentId: string) {
  if (!selectedWorkspace.value) return
  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/actions/assignments/${assignmentId}`, {
      method: 'DELETE'
    })
    await loadWorkspaceActions()
    toast.add({ title: 'Action unassigned', color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not unassign Action.'
    toast.add({ title: 'Unassign failed', description: message, color: 'error' })
  }
}

async function loadWorkspaceActionProjects() {
  if (!selectedWorkspace.value || !canManageWorkspaceActions.value) return
  try {
    workspaceActionProjects.value = await $fetch<Array<{ id: string, name: string }>>(`/api/workspaces/${selectedWorkspace.value}/projects`)
  } catch {
    workspaceActionProjects.value = []
  }
}

watch([selectedWorkspace, canManageWorkspaceActions], () => {
  if (canManageWorkspaceActions.value) {
    void loadWorkspaceActionProjects()
    void loadWorkspaceActions()
  }
}, { immediate: true })

watch(selectedActionProjectId, () => {
  selectedActionDefinitionIds.value = []
  void loadWorkspaceActions()
})

function parseRecognitionIndices(value: string | undefined, gtIndex: number): number[] {
  const parsed = String(value ?? '')
    .split(',')
    .map(v => Number.parseInt(v.trim(), 10))
    .filter(v => Number.isFinite(v) && v >= 0)
  const unique = [...new Set(parsed)].sort((a, b) => a - b).filter(v => v !== gtIndex)
  if (unique.length === 0) {
    throw new Error('Provide at least one recognition index different from the GT index.')
  }
  return unique
}

const { data: codecs, error: codecsError } = await useFetch<CodecSummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value as string}/codecs`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'codecs', 'list')
      : globalKey('pending', 'codecs', 'list')),
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)

const { data: labelSets, error: labelSetsError } = await useFetch<LabelSetSummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value as string}/label-sets`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'label-sets', 'list')
      : globalKey('pending', 'label-sets', 'list')),
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)

const { data: dictionaries, error: dictionariesError } = await useFetch<DictionarySummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value as string}/dictionaries`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'dictionaries', 'list')
      : globalKey('pending', 'dictionaries', 'list')),
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)

const { data: tagSets, error: tagSetsError } = await useFetch<TagSetSummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value as string}/tag-sets`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'tag-sets', 'list')
      : globalKey('pending', 'tag-sets', 'list')),
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)

const { data: normalizationProfiles, error: normalizationProfilesError } = await useFetch<NormalizationProfileSummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value as string}/normalization-profiles`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'normalization-profiles', 'list')
      : globalKey('pending', 'normalization-profiles', 'list')),
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)

const { data: validationRulesets, error: validationRulesetsError } = await useFetch<ValidationRulesetSummary[]>(
  () => `/api/workspaces/${selectedWorkspace.value as string}/validation-rulesets`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'validation-rulesets', 'list')
      : globalKey('pending', 'validation-rulesets', 'list')),
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)

const codecsSafe = computed<SelectOption[]>(() => (codecs.value ?? []).map(codec => ({
  label: codec.name,
  value: codec.id
})))
const labelSetsSafe = computed<SelectOption[]>(() => (labelSets.value ?? []).map(labelSet => ({
  label: labelSet.meta.name,
  value: labelSet.id
})))
const dictionariesSafe = computed<SelectOption[]>(() => (dictionaries.value ?? []).map(dictionary => ({
  label: dictionary.name,
  value: dictionary.id
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

watchEffect(() => {
  if (workspace.value) {
    form.name = workspace.value.name || ''
    form.description = workspace.value.description || ''
    form.codecId = workspace.value.codecId || ''
    form.labelSetId = workspace.value.labelSetId || ''
    form.dictionaryId = workspace.value.dictionaryId || ''
    form.tagSetId = workspace.value.tagSetId || ''
    form.normalizationProfileId = workspace.value.normalizationProfileId || ''
    form.validationRulesetId = workspace.value.validationRulesetId || ''
    form.defaultGtIndexInput = String(workspace.value.defaultGtIndex ?? 0)
    form.defaultRecognitionIndicesInput = formatRecognitionIndices(workspace.value.defaultRecognitionIndices)
  }
})

const startEditing = () => {
  if (!canEditWorkspaceSettings.value) return
  isEditing.value = true
}

const cancelEditing = () => {
  if (workspace.value) {
    form.name = workspace.value.name || ''
    form.description = workspace.value.description || ''
    form.codecId = workspace.value.codecId || ''
    form.labelSetId = workspace.value.labelSetId || ''
    form.dictionaryId = workspace.value.dictionaryId || ''
    form.tagSetId = workspace.value.tagSetId || ''
    form.normalizationProfileId = workspace.value.normalizationProfileId || ''
    form.validationRulesetId = workspace.value.validationRulesetId || ''
    form.defaultGtIndexInput = String(workspace.value.defaultGtIndex ?? 0)
    form.defaultRecognitionIndicesInput = formatRecognitionIndices(workspace.value.defaultRecognitionIndices)
  }
  isEditing.value = false
}

const saveWorkspace = async () => {
  if (!canEditWorkspaceSettings.value) return
  if (!selectedWorkspace.value) return

  isSaving.value = true
  try {
    const defaultGtIndex = parseDefaultGtIndex(form.defaultGtIndexInput)
    const defaultRecognitionIndices = parseRecognitionIndices(form.defaultRecognitionIndicesInput, defaultGtIndex)
    await $fetch(`/api/workspaces/${selectedWorkspace.value}`, {
      method: 'PUT',
      body: {
        name: form.name.trim(),
        description: form.description.trim() || null,
        codecId: form.codecId || null,
        labelSetId: form.labelSetId || null,
        dictionaryId: form.dictionaryId || null,
        tagSetId: form.tagSetId || null,
        normalizationProfileId: form.normalizationProfileId || null,
        validationRulesetId: form.validationRulesetId || null,
        defaultGtIndex,
        defaultRecognitionIndices
      }
    })

    await Promise.all([
      refreshWorkspaceDetails(selectedWorkspace.value),
      refreshWorkspaceList()
    ])

    isEditing.value = false

    toast.add({
      title: 'Workspace updated',
      description: 'Workspace settings have been saved',
      color: 'success'
    })
  } catch (err: unknown) {
    toast.add({
      title: 'Failed to update workspace',
      description: err instanceof Error ? err.message : 'An error occurred',
      color: 'error'
    })
  } finally {
    isSaving.value = false
  }
}

const leaveWorkspace = async () => {
  if (canDeleteWorkspace.value) return
  if (!selectedWorkspace.value || workspace.value?.isPersonal) return

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/leave`, {
      method: 'POST'
    })

    toast.add({
      title: 'Left workspace',
      description: 'You have left the workspace',
      color: 'success'
    })

    await refreshWorkspaceList()
    await workspaceStore.validateAndSelectWorkspace()
    navigateTo('/')
  } catch (err: unknown) {
    toast.add({
      title: 'Failed to leave workspace',
      description: err instanceof Error ? err.message : 'You may be the last administrator',
      color: 'error'
    })
  }
}

async function openDeleteSlideover() {
  if (!canDeleteWorkspace.value) return
  if (!workspace.value) return
  const instance = deleteSlideover.open({
    name: workspace.value.name,
    entityType: 'Workspace',
    warningMessage: 'This action cannot be undone. This will permanently delete the workspace, all projects, and remove all member associations.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${workspace.value.id}`, { method: 'DELETE' })
    toast.add({ title: 'Workspace deleted', color: 'success' })
    await refreshWorkspaceList()
    await workspaceStore.validateAndSelectWorkspace()
    navigateTo('/')
  } catch (err: unknown) {
    toast.add({ title: 'Failed to delete', description: err instanceof Error ? err.message : 'An error occurred', color: 'error' })
  }
}
</script>

<template>
  <div class="flex flex-col gap-y-6">
    <UPageCard
      v-if="storageQuota"
      title="Storage"
      description="Storage usage for this workspace."
      variant="subtle"
    >
      <div class="flex flex-col gap-2 w-full">
        <div class="flex items-center justify-between text-sm">
          <span class="text-muted">Used</span>
          <span>{{ storageQuota.currentUsageFormatted }} / {{ storageQuota.quotaLimitFormatted }}</span>
        </div>
        <div v-if="storageQuota.reservedBytes > 0" class="flex items-center justify-between text-sm">
          <span class="text-muted">Reserved</span>
          <span>{{ storageQuota.reservedBytesFormatted }}</span>
        </div>
        <div class="flex items-center justify-between text-sm">
          <span class="text-muted">Available for uploads/imports</span>
          <span>{{ storageQuota.availableBytesFormatted }}</span>
        </div>
        <UProgress
          v-model="storageProgressValue"
          :max="100"
          :color="storageColor"
          size="sm"
        />
        <p class="text-xs text-muted mt-1">
          {{ storageQuota.usagePercentage.toFixed(1) }}% of quota used
        </p>
        <UAlert
          v-if="storageAlertState === 'exceeded'"
          color="error"
          variant="subtle"
          icon="i-lucide-alert-triangle"
          title="Storage quota exceeded"
          :description="`You're using ${storageQuota.usagePercentage.toFixed(0)}% of your storage quota. Uploads and imports are blocked until you remove data or an admin increases the quota.`"
          class="mt-2"
        />
        <UAlert
          v-else-if="storageAlertState === 'warning'"
          color="warning"
          variant="subtle"
          icon="i-lucide-alert-triangle"
          title="Storage quota approaching limit"
          :description="`You're using ${storageQuota.usagePercentage.toFixed(0)}% of your storage quota.`"
          class="mt-2"
        />
      </div>
    </UPageCard>

    <UPageCard
      v-if="workspace?.isPersonal"
      data-tour="workspace-general-panel"
      title="Personal Workspace"
      description="This is your personal workspace. It cannot be renamed or deleted."
      variant="subtle"
    >
      <div class="flex flex-col gap-4">
        <UiFormSectionHeader title="Metadata" />
        <UFormField label="Name">
          <UInput :model-value="workspace?.name" disabled class="max-w-md" />
        </UFormField>
        <UFormField label="Description">
          <UTextarea
            v-model="form.description"
            :disabled="!isEditing"
            placeholder="Optional description"
            class="max-w-md"
          />
        </UFormField>
        <UiFormSectionHeader data-tour="workspace-general-presets" title="Presets" />
        <UFormField label="Default Codec" hint="Default Codec for all newly created projects in this workspace">
          <USelect
            v-model="form.codecId"
            :items="codecsSafe"
            icon="i-lucide-case-lower"
            :disabled="!isEditing || !!codecsError"
            placeholder="Select a codec"
            class="max-w-md"
          />
        </UFormField>
        <UFormField label="Default Label Set" hint="Default Label Set for all newly created projects in this workspace">
          <USelect
            v-model="form.labelSetId"
            :items="labelSetsSafe"
            icon="i-lucide-tags"
            :disabled="!isEditing || !!labelSetsError"
            placeholder="Select a label set"
            class="max-w-md"
          />
        </UFormField>
        <UFormField label="Default Dictionary" hint="Default Dictionary for all newly created projects in this workspace">
          <USelect
            v-model="form.dictionaryId"
            :items="dictionariesSafe"
            icon="i-lucide-book-copy"
            :disabled="!isEditing || !!dictionariesError"
            placeholder="Select a dictionary"
            class="max-w-md"
          />
        </UFormField>
        <UFormField label="Default Tag Set" hint="Default Tag Set for all newly created projects in this workspace">
          <USelect
            v-model="form.tagSetId"
            :items="tagSetsSafe"
            icon="i-lucide-network"
            :disabled="!isEditing || !!tagSetsError"
            placeholder="Select a tag set"
            class="max-w-md"
          />
        </UFormField>
        <UFormField label="Default Normalization Profile" hint="Default text normalization profile for new projects in this workspace">
          <USelect
            v-model="form.normalizationProfileId"
            :items="normalizationProfilesSafe"
            icon="i-lucide-wand-sparkles"
            :disabled="!isEditing || !!normalizationProfilesError"
            placeholder="Select a normalization profile"
            class="max-w-md"
          />
        </UFormField>
        <UFormField label="Default Validation Ruleset" hint="Default QA ruleset for new projects in this workspace">
          <USelect
            v-model="form.validationRulesetId"
            :items="validationRulesetsSafe"
            icon="i-lucide-shield-alert"
            :disabled="!isEditing || !!validationRulesetsError"
            placeholder="Select a validation ruleset"
            class="max-w-md"
          />
        </UFormField>
        <UFormField label="Default GT Index" hint="Single Ground Truth index used in the text editor for new projects.">
          <UInput
            v-model="form.defaultGtIndexInput"
            :disabled="!isEditing"
            placeholder="0"
            class="max-w-md"
          />
        </UFormField>
        <UFormField label="Default Recognition Indices" hint="Comma-separated indices for recognition variants (e.g. 1, 2).">
          <UInput
            v-model="form.defaultRecognitionIndicesInput"
            :disabled="!isEditing"
            placeholder="1"
            class="max-w-md"
          />
        </UFormField>

        <div class="flex gap-2">
          <template v-if="isEditing">
            <UButton
              label="Save changes"
              icon="i-lucide-save"
              color="primary"
              variant="solid"
              :loading="isSaving"
              @click="saveWorkspace"
            />
            <UButton
              label="Cancel"
              color="neutral"
              variant="subtle"
              @click="cancelEditing"
            />
          </template>
          <UButton
            v-else
            label="Edit"
            color="neutral"
            variant="outline"
            icon="i-lucide-pencil"
            @click="startEditing"
          />
        </div>
      </div>
    </UPageCard>

    <template v-else>
      <UPageCard
        data-tour="workspace-general-panel"
        title="Workspace Details"
        :description="canEditWorkspaceSettings ? 'Update workspace settings based on your role permissions.' : 'View workspace details.'"
        variant="subtle"
      >
        <div class="flex flex-col gap-4">
          <UFormField label="Name" :hint="isEditing ? 'Required' : ''">
            <UInput
              v-model="form.name"
              :disabled="!isEditing || !canEditWorkspaceMetadata"
              placeholder="Workspace name"
            />
          </UFormField>
          <UFormField label="Description">
            <UTextarea
              v-model="form.description"
              :disabled="!isEditing || !canEditWorkspaceMetadata"
              placeholder="Optional description"
            />
          </UFormField>

          <p v-if="isEditing && canSetWorkspacePresets && !canEditWorkspaceMetadata" class="text-xs text-muted">
            Only workspace owners can edit workspace name and description.
          </p>

          <USeparator />

          <UFormField
            v-if="canSetWorkspacePresets"
            data-tour="workspace-general-presets"
            label="Default Codec"
            hint="Default codec for new projects"
          >
            <USelect
              v-model="form.codecId"
              :items="codecsSafe"
              :disabled="!isEditing || !canSetWorkspacePresets || !!codecsError || codecsSafe.length === 0"
              placeholder="Select a codec"
            />
          </UFormField>
          <UFormField v-if="canSetWorkspacePresets" label="Default Label Set" hint="Default label set for new projects">
            <USelect
              v-model="form.labelSetId"
              :items="labelSetsSafe"
              :disabled="!isEditing || !canSetWorkspacePresets || !!labelSetsError || labelSetsSafe.length === 0"
              placeholder="Select a label set"
            />
          </UFormField>
          <UFormField v-if="canSetWorkspacePresets" label="Default Dictionary" hint="Default dictionary for new projects">
            <USelect
              v-model="form.dictionaryId"
              :items="dictionariesSafe"
              :disabled="!isEditing || !canSetWorkspacePresets || !!dictionariesError || dictionariesSafe.length === 0"
              placeholder="Select a dictionary"
            />
          </UFormField>
          <UFormField v-if="canSetWorkspacePresets" label="Default Tag Set" hint="Default tag set for new projects">
            <USelect
              v-model="form.tagSetId"
              :items="tagSetsSafe"
              :disabled="!isEditing || !canSetWorkspacePresets || !!tagSetsError || tagSetsSafe.length === 0"
              placeholder="Select a tag set"
            />
          </UFormField>
          <UFormField v-if="canSetWorkspacePresets" label="Default Normalization Profile" hint="Default normalization profile for new projects">
            <USelect
              v-model="form.normalizationProfileId"
              :items="normalizationProfilesSafe"
              :disabled="!isEditing || !canSetWorkspacePresets || !!normalizationProfilesError || normalizationProfilesSafe.length === 0"
              placeholder="Select a normalization profile"
            />
          </UFormField>
          <UFormField v-if="canSetWorkspacePresets" label="Default Validation Ruleset" hint="Default validation ruleset for new projects">
            <USelect
              v-model="form.validationRulesetId"
              :items="validationRulesetsSafe"
              :disabled="!isEditing || !canSetWorkspacePresets || !!validationRulesetsError || validationRulesetsSafe.length === 0"
              placeholder="Select a validation ruleset"
            />
          </UFormField>
          <UFormField v-if="canSetWorkspacePresets" label="Default GT Index" hint="Single Ground Truth index for new projects">
            <UInput
              v-model="form.defaultGtIndexInput"
              :disabled="!isEditing || !canEditWorkspaceTextIndexDefaults"
              placeholder="0"
            />
          </UFormField>
          <UFormField v-if="canSetWorkspacePresets" label="Default Recognition Indices" hint="Comma-separated recognition indices for new projects">
            <UInput
              v-model="form.defaultRecognitionIndicesInput"
              :disabled="!isEditing || !canEditWorkspaceTextIndexDefaults"
              placeholder="1"
            />
          </UFormField>
          <p v-if="canSetWorkspacePresets && !canEditWorkspaceTextIndexDefaults" class="text-xs text-muted">
            You do not have permission to change default text-index settings.
          </p>

          <div class="flex gap-2">
            <template v-if="canEditWorkspaceSettings">
              <template v-if="isEditing">
                <UButton
                  label="Save changes"
                  icon="i-lucide-save"
                  color="primary"
                  variant="solid"
                  :loading="isSaving"
                  :disabled="!form.name.trim()"
                  @click="saveWorkspace"
                />
                <UButton
                  label="Cancel"
                  color="neutral"
                  variant="outline"
                  @click="cancelEditing"
                />
              </template>
              <UButton
                v-else
                label="Edit"
                color="neutral"
                variant="outline"
                icon="i-lucide-pencil"
                @click="startEditing"
              />
            </template>
          </div>
        </div>
      </UPageCard>

      <UPageCard
        v-if="canManageWorkspaceActions"
        title="Actions"
        description="Manage inherited, workspace-default, and project-specific Actions."
        variant="subtle"
      >
        <div class="flex flex-col gap-4">
          <div v-if="inheritedGlobalActionDefinitions.length > 0" class="rounded-sm border border-default p-3">
            <div class="mb-2 flex items-center justify-between gap-2">
              <div>
                <p class="text-sm font-medium">
                  Inherited Global Actions
                </p>
                <p class="text-xs text-muted">
                  These Actions are available in every workspace and project.
                </p>
              </div>
              <UBadge size="sm" variant="soft" color="primary">
                Read-only
              </UBadge>
            </div>
            <div class="divide-y divide-default rounded-sm border border-default">
              <div
                v-for="definition in inheritedGlobalActionDefinitions"
                :key="definition.id"
                class="flex items-center justify-between gap-3 p-3"
              >
                <div class="min-w-0">
                  <p class="truncate text-sm font-medium">
                    {{ definition.name }}
                  </p>
                  <p class="truncate text-xs text-muted">
                    {{ definition.processorKey }} · {{ definition.executeRole }} · {{ definition.lockMode }}
                  </p>
                </div>
                <UBadge size="sm" variant="soft" color="primary">
                  Global
                </UBadge>
              </div>
            </div>
          </div>

          <div class="grid gap-3 lg:grid-cols-[220px_minmax(0,1fr)_auto] lg:items-end">
            <UFormField label="Assignment Scope">
              <USelectMenu
                v-model="selectedActionProjectId"
                :items="actionProjectOptions"
                value-key="value"
                searchable
              />
            </UFormField>
            <UFormField label="Available Actions">
              <USelectMenu
                v-model="selectedActionDefinitionIds"
                :items="actionDefinitionOptions"
                value-key="value"
                multiple
                searchable
                :disabled="loadingActions || actionDefinitionOptions.length === 0"
                placeholder="Select Actions"
              />
            </UFormField>
            <UButton
              label="Assign"
              icon="i-lucide-plus"
              :loading="assigningAction"
              :disabled="selectedActionDefinitionIds.length === 0"
              @click="assignWorkspaceAction"
            />
          </div>

          <p v-if="!loadingActions && scopedActionDefinitions.length === 0" class="text-sm text-muted">
            No workspace-scoped Actions are available. A global administrator can make Actions available from the Actions admin page.
          </p>

          <div v-if="loadingActions" class="space-y-2">
            <USkeleton class="h-10 w-full" />
            <USkeleton class="h-10 w-full" />
          </div>

          <div v-else-if="actionAssignments.length === 0" class="rounded-sm border border-default p-3 text-sm text-muted">
            No Actions are assigned for this scope.
          </div>

          <div v-else class="divide-y divide-default rounded-sm border border-default">
            <div
              v-for="assignment in actionAssignments"
              :key="assignment.id"
              class="flex items-center justify-between gap-3 p-3"
            >
              <div class="min-w-0">
                <p class="truncate text-sm font-medium">
                  {{ assignment.processor.name }}
                </p>
                <p class="truncate text-xs text-muted">
                  {{ assignment.processor.processorKey }} · {{ assignment.processor.executeRole }} · {{ assignment.processor.lockMode }}
                </p>
              </div>
              <UButton
                color="error"
                variant="ghost"
                icon="i-lucide-x"
                @click="unassignWorkspaceAction(assignment.id)"
              />
            </div>
          </div>
        </div>
      </UPageCard>

      <UPageCard
        data-tour="workspace-danger-zone"
        title="Danger Zone"
        description="Irreversible actions for this workspace."
        variant="subtle"
        class="bg-linear-to-tl from-error/5 from-5% to-default"
      >
        <template #footer>
          <div class="flex flex-col gap-4">
            <div v-if="!canDeleteWorkspace" class="flex items-center justify-between">
              <div>
                <h4 class="font-medium">
                  Leave workspace
                </h4>
                <p class="text-sm text-muted">
                  Remove yourself from this workspace.
                </p>
              </div>
              <UButton
                label="Leave"
                color="error"
                variant="outline"
                icon="i-lucide-log-out"
                @click="leaveWorkspace"
              />
            </div>

            <div v-if="canDeleteWorkspace" class="flex flex-col gap-y-2 items-start justify-between">
              <div>
                <h4 class="font-medium">
                  Delete workspace
                </h4>
                <p class="text-sm text-muted">
                  Permanently delete this workspace and all its data.
                </p>
              </div>
              <UButton
                label="Delete workspace"
                color="error"
                variant="solid"
                icon="i-lucide-trash-2"
                @click="openDeleteSlideover"
              />
            </div>
          </div>
        </template>
      </UPageCard>
    </template>
  </div>
</template>
