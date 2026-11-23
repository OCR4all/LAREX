<script setup lang="ts">
import { LazyUiConfirmSlideover, LazyUiDeleteSlideover } from '#components'
import WorkspaceModalProjectDefaultPropagation from '~/components/workspace/modal/project-default-propagation.vue'
import type { CodecSummary } from '@/types/codec'
import type { DictionarySummary } from '@/types/dictionary'
import type { LabelSetSummary } from '@/types/label-set'
import type { NormalizationProfileSummary } from '@/types/normalization-profile'
import type { TagSetSummary } from '@/types/tag-set'
import type { ValidationRulesetSummary } from '@/types/validation-ruleset'
import type {
  ProjectDefaultKey,
  ProjectDefaultPropagationScope,
  ProjectDefaultsPreview
} from '~/types/workspace-project-defaults'

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
  allowEditorsToEditPageName?: boolean
  allowEditorsToEditPageDescription?: boolean
  allowEditorsToEditPageTags?: boolean
  defaultReviewRequired?: boolean
  recordPageXmlAuthors?: boolean
  pageXmlCreatorFromUser?: boolean
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
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)
const projectDefaultsPropagationModal = overlay.create(WorkspaceModalProjectDefaultPropagation)

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
const canEditWorkspaceTextIndexDefaults = computed(() =>
  canSetWorkspacePresets.value && allow(workspaceCapabilities.value.canEditWorkspaceTextIndexDefaults)
)

const canEditName = computed(() => !workspace.value?.isPersonal && canEditWorkspaceMetadata.value)
const canEditDescription = computed(() => workspace.value?.isPersonal
  ? canEditWorkspaceSettings.value
  : canEditWorkspaceMetadata.value)
const canEditTextIndices = computed(() => workspace.value?.isPersonal
  ? canEditWorkspaceSettings.value
  : canEditWorkspaceTextIndexDefaults.value)
const showWorkspaceRules = computed(() => workspace.value?.isPersonal
  ? canEditWorkspaceSettings.value
  : canSetWorkspacePresets.value)

function formFromWorkspace(ws?: Workspace | null) {
  return {
    name: ws?.name || '',
    description: ws?.description || '',
    codecId: ws?.codecId || '',
    labelSetId: ws?.labelSetId || '',
    dictionaryId: ws?.dictionaryId || '',
    tagSetId: ws?.tagSetId || '',
    normalizationProfileId: ws?.normalizationProfileId || '',
    validationRulesetId: ws?.validationRulesetId || '',
    defaultGtIndexInput: ws?.defaultGtIndex === UNINDEXED_TEXT_INDEX ? '' : String(ws?.defaultGtIndex ?? 0),
    defaultGtIndexUndefined: ws?.defaultGtIndex === UNINDEXED_TEXT_INDEX,
    defaultRecognitionIndicesInput: recognitionIndexTags(ws?.defaultRecognitionIndices),
    defaultRecognitionIndicesUndefined: effectiveRecognitionIndices(ws?.defaultRecognitionIndices).includes(UNDEFINED_RECOGNITION_INDEX),
    allowEditorsToEditPageName: ws?.allowEditorsToEditPageName ?? false,
    allowEditorsToEditPageDescription: ws?.allowEditorsToEditPageDescription ?? false,
    allowEditorsToEditPageTags: ws?.allowEditorsToEditPageTags ?? false,
    defaultReviewRequired: ws?.defaultReviewRequired ?? false,
    recordPageXmlAuthors: ws?.recordPageXmlAuthors ?? true,
    pageXmlCreatorFromUser: ws?.pageXmlCreatorFromUser ?? true
  }
}

const isSaving = ref(false)
const form = reactive(formFromWorkspace())
const baseline = ref(JSON.stringify(form))
const isDirty = computed(() => JSON.stringify(form) !== baseline.value)
const canSave = computed(() => !!form.name.trim() || !!workspace.value?.isPersonal)

function resetForm() {
  const next = formFromWorkspace(workspace.value)
  Object.assign(form, next)
  baseline.value = JSON.stringify(next)
}

watch(workspace, (ws, previous) => {
  if (!ws) return
  const keepEdits = isDirty.value && ws.id === previous?.id
  baseline.value = JSON.stringify(formFromWorkspace(ws))
  if (!keepEdits) Object.assign(form, formFromWorkspace(ws))
}, { immediate: true })

const UNDEFINED_RECOGNITION_INDEX = -1

function effectiveRecognitionIndices(value?: number[] | null): number[] {
  return Array.isArray(value) && value.length > 0 ? value : [1]
}

function recognitionIndexTags(value?: number[] | null): string[] {
  return effectiveRecognitionIndices(value)
    .filter(index => index !== UNDEFINED_RECOGNITION_INDEX)
    .map(String)
}

const defaultLabels: Record<ProjectDefaultKey, string> = {
  CODEC: 'Codec',
  LABEL_SET: 'Label set',
  DICTIONARY: 'Dictionary',
  TAG_SET: 'Tag set',
  NORMALIZATION_PROFILE: 'Normalization profile',
  VALIDATION_RULESET: 'Validation ruleset',
  TEXT_INDICES: 'Text indices',
  REVIEW_REQUIRED: 'Page review'
}

function normalizeId(value?: string | null): string | null {
  return value?.trim() || null
}

function optionLabel(options: SelectOption[], id?: string | null): string {
  if (!id) return 'Not set'
  return options.find(option => option.value === id)?.label || id
}

function changedWorkspaceDefaults(defaultGtIndex: number, defaultRecognitionIndices: number[]) {
  if (!workspace.value) return []

  const currentRecognitionIndices = effectiveRecognitionIndices(workspace.value.defaultRecognitionIndices)
  const changedKeys = changedProjectDefaultKeys(
    {
      codecId: workspace.value.codecId,
      labelSetId: workspace.value.labelSetId,
      dictionaryId: workspace.value.dictionaryId,
      tagSetId: workspace.value.tagSetId,
      normalizationProfileId: workspace.value.normalizationProfileId,
      validationRulesetId: workspace.value.validationRulesetId,
      defaultGtIndex: workspace.value.defaultGtIndex ?? 0,
      defaultRecognitionIndices: currentRecognitionIndices
    },
    {
      codecId: form.codecId,
      labelSetId: form.labelSetId,
      dictionaryId: form.dictionaryId,
      tagSetId: form.tagSetId,
      normalizationProfileId: form.normalizationProfileId,
      validationRulesetId: form.validationRulesetId,
      defaultGtIndex,
      defaultRecognitionIndices
    }
  )
  const changed: Array<{ key: ProjectDefaultKey, label: string, before: string, after: string }> = []
  const resources: Array<{ key: Exclude<ProjectDefaultKey, 'TEXT_INDICES' | 'REVIEW_REQUIRED'>, current?: string | null, next: string, options: SelectOption[] }> = [
    { key: 'CODEC', current: workspace.value.codecId, next: form.codecId, options: codecsSafe.value },
    { key: 'LABEL_SET', current: workspace.value.labelSetId, next: form.labelSetId, options: labelSetsSafe.value },
    { key: 'DICTIONARY', current: workspace.value.dictionaryId, next: form.dictionaryId, options: dictionariesSafe.value },
    { key: 'TAG_SET', current: workspace.value.tagSetId, next: form.tagSetId, options: tagSetsSafe.value },
    { key: 'NORMALIZATION_PROFILE', current: workspace.value.normalizationProfileId, next: form.normalizationProfileId, options: normalizationProfilesSafe.value },
    { key: 'VALIDATION_RULESET', current: workspace.value.validationRulesetId, next: form.validationRulesetId, options: validationRulesetsSafe.value }
  ]

  for (const resource of resources) {
    if (!changedKeys.includes(resource.key)) continue
    const before = normalizeId(resource.current)
    const after = normalizeId(resource.next)
    changed.push({
      key: resource.key,
      label: defaultLabels[resource.key],
      before: optionLabel(resource.options, before),
      after: optionLabel(resource.options, after)
    })
  }

  const currentGtIndex = workspace.value.defaultGtIndex ?? 0
  if (changedKeys.includes('TEXT_INDICES')) {
    changed.push({
      key: 'TEXT_INDICES',
      label: defaultLabels.TEXT_INDICES,
      before: formatWorkspaceTextIndexDefault({ defaultGtIndex: currentGtIndex, defaultRecognitionIndices: currentRecognitionIndices }),
      after: formatWorkspaceTextIndexDefault({ defaultGtIndex, defaultRecognitionIndices })
    })
  }

  if ((workspace.value.defaultReviewRequired ?? false) !== form.defaultReviewRequired) {
    changed.push({
      key: 'REVIEW_REQUIRED',
      label: defaultLabels.REVIEW_REQUIRED,
      before: workspace.value.defaultReviewRequired ? 'Required' : 'Optional',
      after: form.defaultReviewRequired ? 'Required' : 'Optional'
    })
  }

  return changed
}

function parseDefaultGtIndex(value: string, isUndefined: boolean): number {
  if (isUndefined) return UNINDEXED_TEXT_INDEX
  const trimmed = value.trim()
  if (!/^\d+$/.test(trimmed)) {
    throw new Error('Default GT index must be a non-negative integer.')
  }
  return Number.parseInt(trimmed, 10)
}

function parseRecognitionIndices(values: string[], includeUndefined: boolean, gtIndex: number): number[] {
  const parsed = values
    .flatMap(value => value.split(','))
    .map(value => value.trim())
    .filter(Boolean)
    .map((value) => {
      if (!/^\d+$/.test(value)) {
        throw new Error('Recognition indices must be non-negative integers.')
      }
      return Number.parseInt(value, 10)
    })
  if (includeUndefined && gtIndex === UNINDEXED_TEXT_INDEX) {
    throw new Error('Undefined cannot be selected for both GT and recognition.')
  }
  if (parsed.includes(gtIndex)) {
    throw new Error('Recognition indices must not include the GT index.')
  }
  const withUndefined = includeUndefined ? [UNDEFINED_RECOGNITION_INDEX, ...parsed] : parsed
  const unique = [...new Set(withUndefined)].sort((a, b) => a - b)
  if (unique.length === 0) {
    throw new Error('Add at least one recognition index or enable Undefined.')
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

const saveWorkspace = async () => {
  if (!canEditWorkspaceSettings.value || !canSave.value) return
  if (!selectedWorkspace.value) return

  isSaving.value = true
  try {
    const defaultGtIndex = parseDefaultGtIndex(form.defaultGtIndexInput, form.defaultGtIndexUndefined)
    const defaultRecognitionIndices = parseRecognitionIndices(form.defaultRecognitionIndicesInput, form.defaultRecognitionIndicesUndefined, defaultGtIndex)
    const changedDefaults = changedWorkspaceDefaults(defaultGtIndex, defaultRecognitionIndices)
    let propagationScope: ProjectDefaultPropagationScope = 'FUTURE_ONLY'

    if (changedDefaults.length > 0) {
      const preview = await $fetch<ProjectDefaultsPreview>(`/api/workspaces/${selectedWorkspace.value}/project-defaults/preview`, {
        method: 'POST',
        body: {
          codecId: normalizeId(form.codecId),
          labelSetId: normalizeId(form.labelSetId),
          dictionaryId: normalizeId(form.dictionaryId),
          tagSetId: normalizeId(form.tagSetId),
          normalizationProfileId: normalizeId(form.normalizationProfileId),
          validationRulesetId: normalizeId(form.validationRulesetId),
          defaultGtIndex,
          defaultRecognitionIndices,
          defaultReviewRequired: form.defaultReviewRequired
        }
      })
      const modal = projectDefaultsPropagationModal.open({
        preview,
        changedDefaults: changedDefaults.map(({ label, before, after }) => ({ label, before, after }))
      })
      const selectedScope = await modal.result
      if (!selectedScope) return
      propagationScope = selectedScope
    }

    await $fetch<Workspace>(`/api/workspaces/${selectedWorkspace.value}`, {
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
        defaultRecognitionIndices,
        projectDefaultPropagationScope: propagationScope,
        allowEditorsToEditPageName: form.allowEditorsToEditPageName,
        allowEditorsToEditPageDescription: form.allowEditorsToEditPageDescription,
        allowEditorsToEditPageTags: form.allowEditorsToEditPageTags,
        defaultReviewRequired: form.defaultReviewRequired,
        recordPageXmlAuthors: form.recordPageXmlAuthors,
        pageXmlCreatorFromUser: form.pageXmlCreatorFromUser
      }
    })

    await Promise.all([
      refreshWorkspaceDetails(selectedWorkspace.value),
      refreshWorkspaceList()
    ])

    resetForm()
    toast.add({ title: 'Workspace settings saved', color: 'success' })
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
type DefaultResourceField = 'codecId' | 'labelSetId' | 'dictionaryId' | 'tagSetId' | 'normalizationProfileId' | 'validationRulesetId'

const defaultResourceFields = computed<Array<{ field: DefaultResourceField, label: string, icon: string, placeholder: string, options: SelectOption[], unavailable: boolean }>>(() => [
  { field: 'codecId', label: 'Codec', icon: 'i-lucide-case-lower', placeholder: 'Select a codec', options: codecsSafe.value, unavailable: !!codecsError.value },
  { field: 'labelSetId', label: 'Label set', icon: 'i-lucide-tags', placeholder: 'Select a label set', options: labelSetsSafe.value, unavailable: !!labelSetsError.value },
  { field: 'dictionaryId', label: 'Dictionary', icon: 'i-lucide-book-copy', placeholder: 'Select a dictionary', options: dictionariesSafe.value, unavailable: !!dictionariesError.value },
  { field: 'tagSetId', label: 'Tag set', icon: 'i-lucide-network', placeholder: 'Select a tag set', options: tagSetsSafe.value, unavailable: !!tagSetsError.value },
  { field: 'normalizationProfileId', label: 'Normalization profile', icon: 'i-lucide-wand-sparkles', placeholder: 'Select a normalization profile', options: normalizationProfilesSafe.value, unavailable: !!normalizationProfilesError.value },
  { field: 'validationRulesetId', label: 'Validation ruleset', icon: 'i-lucide-shield-alert', placeholder: 'Select a validation ruleset', options: validationRulesetsSafe.value, unavailable: !!validationRulesetsError.value }
])

const hasStorageCard = computed(() => !!storageQuota.value)

onBeforeRouteLeave(async () => {
  if (!isDirty.value) return true
  const instance = confirmSlideover.open({
    title: 'Discard workspace changes?',
    message: 'Your unsaved workspace settings will be lost if you leave this page.',
    confirmLabel: 'Discard and leave',
    cancelLabel: 'Keep editing',
    confirmColor: 'warning',
    confirmIcon: 'i-lucide-log-out'
  })
  return await instance.result === true
})

function beforeUnload(event: BeforeUnloadEvent) {
  if (isDirty.value) event.preventDefault()
}

onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <div class="grid grid-cols-1 xl:grid-cols-12 gap-4 lg:gap-6">
    <UPageCard
      data-tour="workspace-general-panel"
      title="General"
      :description="workspace?.isPersonal
        ? 'Your personal workspace. It cannot be renamed, left, or deleted.'
        : 'How this workspace appears to its members.'"
      variant="subtle"
      :class="hasStorageCard ? 'xl:col-span-8' : 'xl:col-span-12'"
    >
      <div class="flex flex-col gap-4">
        <UFormField label="Name" :required="canEditName">
          <UInput
            v-if="canEditName"
            v-model="form.name"
            placeholder="Workspace name"
            class="w-full"
          />
          <p v-else class="text-sm text-highlighted">
            {{ workspace?.name }}
          </p>
        </UFormField>
        <UFormField label="Description">
          <UTextarea
            v-if="canEditDescription"
            v-model="form.description"
            placeholder="Optional description"
            :rows="3"
            autoresize
            class="w-full"
          />
          <p v-else class="text-sm" :class="workspace?.description ? 'text-highlighted whitespace-pre-line' : 'text-muted'">
            {{ workspace?.description || 'No description' }}
          </p>
        </UFormField>
        <p v-if="!workspace?.isPersonal && canSetWorkspacePresets && !canEditWorkspaceMetadata" class="text-xs text-muted">
          Only workspace owners can change the name and description.
        </p>
      </div>
    </UPageCard>

    <UPageCard
      v-if="storageQuota"
      title="Storage"
      variant="subtle"
      class="xl:col-span-4"
    >
      <div class="flex flex-col gap-3 w-full">
        <div>
          <p class="text-2xl font-semibold text-highlighted tabular-nums">
            {{ storageQuota.currentUsageFormatted }}
          </p>
          <p class="text-sm text-muted tabular-nums">
            of {{ storageQuota.quotaLimitFormatted }} used ({{ storageQuota.usagePercentage.toFixed(1) }}%)
          </p>
        </div>
        <UProgress
          v-model="storageProgressValue"
          :max="100"
          :color="storageColor"
          size="sm"
        />
        <dl class="flex flex-col gap-1 text-sm">
          <div v-if="storageQuota.reservedBytes > 0" class="flex items-center justify-between gap-3">
            <dt class="text-muted">
              Reserved
            </dt>
            <dd class="tabular-nums">
              {{ storageQuota.reservedBytesFormatted }}
            </dd>
          </div>
          <div class="flex items-center justify-between gap-3">
            <dt class="text-muted">
              Available for uploads/imports
            </dt>
            <dd class="tabular-nums">
              {{ storageQuota.availableBytesFormatted }}
            </dd>
          </div>
        </dl>
        <UAlert
          v-if="storageAlertState === 'exceeded'"
          color="error"
          variant="subtle"
          icon="i-lucide-alert-triangle"
          title="Storage quota exceeded"
          :description="`You're using ${storageQuota.usagePercentage.toFixed(0)}% of your storage quota. Uploads and imports are blocked until you remove data or an admin increases the quota.`"
        />
        <UAlert
          v-else-if="storageAlertState === 'warning'"
          color="warning"
          variant="subtle"
          icon="i-lucide-alert-triangle"
          title="Storage quota approaching limit"
          :description="`You're using ${storageQuota.usagePercentage.toFixed(0)}% of your storage quota.`"
        />
      </div>
    </UPageCard>

    <template v-if="showWorkspaceRules">
      <UPageCard
        data-tour="workspace-general-presets"
        variant="subtle"
        class="xl:col-span-8"
      >
        <template #title>
          <span class="inline-flex items-center gap-2">
            Project defaults
            <ProjectWorkspaceDefaultsInfo context="workspace" />
          </span>
        </template>
        <template #description>
          Starting point for new projects in this workspace.
        </template>

        <div class="flex flex-col gap-6">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <UFormField v-for="resource in defaultResourceFields" :key="resource.field" :label="resource.label">
              <USelectMenu
                v-model="form[resource.field]"
                :items="resource.options"
                value-key="value"
                :icon="resource.icon"
                clear
                :search-input="false"
                :disabled="resource.unavailable || resource.options.length === 0"
                :placeholder="resource.options.length === 0 ? 'None available' : resource.placeholder"
                class="w-full"
              />
            </UFormField>
          </div>

          <USeparator />

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <UFormField label="GT index" description="Text index that holds ground truth.">
              <div class="flex items-center gap-3">
                <UInput
                  v-model="form.defaultGtIndexInput"
                  :disabled="!canEditTextIndices || form.defaultGtIndexUndefined"
                  inputmode="numeric"
                  :placeholder="form.defaultGtIndexUndefined ? 'No index' : '0'"
                  class="flex-1 min-w-0"
                />
                <UCheckbox
                  v-model="form.defaultGtIndexUndefined"
                  label="Undefined"
                  :disabled="!canEditTextIndices || form.defaultRecognitionIndicesUndefined"
                />
              </div>
            </UFormField>
            <UFormField label="Recognition indices" description="Comma-separated or one tag per index.">
              <div class="flex items-center gap-3">
                <UInputTags
                  v-model="form.defaultRecognitionIndicesInput"
                  :disabled="!canEditTextIndices"
                  placeholder="Add indices (e.g. 1, 2)"
                  class="flex-1 min-w-0"
                />
                <UCheckbox
                  v-model="form.defaultRecognitionIndicesUndefined"
                  label="Undefined"
                  :disabled="!canEditTextIndices || form.defaultGtIndexUndefined"
                />
              </div>
            </UFormField>
          </div>
          <p class="-mt-3 text-xs text-muted">
            Undefined uses text without an <code>index</code> attribute. Only one of GT and recognition can use it.
          </p>
          <p v-if="!canEditTextIndices" class="-mt-3 text-xs text-muted">
            You do not have permission to change default text-index settings.
          </p>

          <template v-if="!workspace?.isPersonal">
            <USeparator />
            <WorkspaceSettingsToggle
              v-model="form.defaultReviewRequired"
              label="Require page review"
              description="Editors submit finished pages, and only curators can mark them as done."
            />
          </template>
        </div>
      </UPageCard>

      <UPageCard
        v-if="!workspace?.isPersonal"
        title="Editor permissions"
        description="Page metadata editors may change in the editor."
        variant="subtle"
        class="xl:col-span-4"
      >
        <div class="flex flex-col gap-4">
          <WorkspaceSettingsToggle
            v-model="form.allowEditorsToEditPageName"
            label="Page name"
            description="Editors can rename pages."
          />
          <WorkspaceSettingsToggle
            v-model="form.allowEditorsToEditPageDescription"
            label="Page description"
            description="Editors can edit page descriptions."
          />
          <WorkspaceSettingsToggle
            v-model="form.allowEditorsToEditPageTags"
            label="Page tags"
            description="Editors can assign and remove page tags."
          />
        </div>
      </UPageCard>

      <UPageCard
        title="PAGE XML attribution"
        description="Which names saved PAGE XML files and their exports include."
        variant="subtle"
        :class="workspace?.isPersonal ? 'xl:col-span-4' : 'xl:col-span-8'"
      >
        <div class="flex flex-col gap-4">
          <WorkspaceSettingsToggle
            v-model="form.pageXmlCreatorFromUser"
            label="Name the creator"
            description="New annotations use the creating user's name followed by &quot;(LAREX)&quot; as Creator. Off: just &quot;LAREX&quot;."
          />
          <WorkspaceSettingsToggle
            v-model="form.recordPageXmlAuthors"
            label="Record editors"
            description="Each save adds the user to the file's author entries."
          />
        </div>
      </UPageCard>
    </template>

    <UPageCard
      v-if="!workspace?.isPersonal"
      data-tour="workspace-danger-zone"
      title="Danger zone"
      :description="canDeleteWorkspace ? 'Permanently delete this workspace and all its data.' : 'Remove yourself from this workspace.'"
      variant="subtle"
      class="bg-linear-to-tl from-error/5 from-5% to-default"
      :class="showWorkspaceRules ? 'xl:col-span-4' : 'xl:col-span-12'"
    >
      <div>
        <UButton
          v-if="canDeleteWorkspace"
          label="Delete workspace"
          color="error"
          variant="solid"
          icon="i-lucide-trash-2"
          @click="openDeleteSlideover"
        />
        <UButton
          v-else
          label="Leave workspace"
          color="error"
          variant="outline"
          icon="i-lucide-log-out"
          @click="leaveWorkspace"
        />
      </div>
    </UPageCard>

    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 translate-y-2"
      leave-active-class="transition duration-150 ease-in"
      leave-to-class="opacity-0 translate-y-2"
    >
      <div
        v-if="isDirty && canEditWorkspaceSettings"
        class="sticky bottom-4 z-10 xl:col-span-12 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-default bg-elevated/90 backdrop-blur px-4 py-3 shadow-lg"
        role="status"
      >
        <p class="text-sm text-highlighted">
          You have unsaved changes.
        </p>
        <div class="flex gap-2">
          <UButton
            label="Discard"
            color="neutral"
            variant="outline"
            :disabled="isSaving"
            @click="resetForm"
          />
          <UButton
            label="Save changes"
            icon="i-lucide-save"
            :loading="isSaving"
            :disabled="!canSave"
            @click="saveWorkspace"
          />
        </div>
      </div>
    </Transition>
  </div>
</template>
