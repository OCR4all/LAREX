<script setup lang="ts">
import type { BreadcrumbItem, DropdownMenuItem } from '@nuxt/ui'
import { CANONICAL_PAGE_CUSTOM_KEY, type LabelSet, type LabelSetCreateOrUpdateRequest, type LabelSetSummary } from '@/types/label-set'
import { DEFAULT_RESOURCE_CAPABILITIES, type ResourceCapabilities } from '@/types/capabilities'
import { LazyEditorSlideoverUnsavedProgress, LazyLabelBuilderModalImportPreview, LazyLabelBuilderSlideoverMetadata, LazyUiDeleteSlideover, LazyUiConfirmModal, LazyShareSlideover } from '#components'
import { isEditableLabelDefinition, isGroupMeta, normalizeEditableLabel, type BuilderEntry } from '@/composables/use-label-builder'
import { buildToolkitPackageFileName } from '@/utils/download-file-names'
import { buildLabelSetImportPreview } from '@/utils/label-set-import-preview'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const overlay = useOverlay()
const backgroundDownloads = useBackgroundDownloads()
const { allow } = useActionVisibility()
const shareSlideover = overlay.create(LazyShareSlideover)
const metadataSlideover = overlay.create(LazyLabelBuilderSlideoverMetadata)
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const confirmModal = overlay.create(LazyUiConfirmModal)
const unsavedProgressSlideover = overlay.create(LazyEditorSlideoverUnsavedProgress)
const importPreviewModal = overlay.create(LazyLabelBuilderModalImportPreview)

const { selectedWorkspace } = await useWorkspaceBootstrap()
const workspaceId = computed(() => selectedWorkspace.value ?? '')

const id = route.params.id as string
const isNew = id === 'new'

const labelSetsKey = computed(() => wsKey(workspaceId.value, 'label-sets', 'list'))
const labelSetKey = computed(() => wsKey(workspaceId.value, 'label-sets', id))
const loadedCapabilities = ref<ResourceCapabilities | null>(null)

const breadcrumbItems = computed<BreadcrumbItem[]>(() => [
  { label: 'Home', icon: 'i-lucide-home', to: '/' },
  { label: 'Label Sets', icon: 'i-lucide-tags', to: '/labels' },
  { label: isNew ? 'New Label Set' : (meta.name || id) }
])

const {
  meta,
  labels,
  activeLabel,
  totalErrors,
  createLabel,
  deleteLabel,
  deleteSelectedLabels,
  selectLabel,
  optimizeColors,
  selectedLabelIds,
  selectedLabels,
  clearSelection,
  groupSelectedLabels,
  moveSelectedToGroup,
  isDirty,
  markSavedState
} = useLabelBuilder()

const fileInput = ref<HTMLInputElement | null>(null)
const groupNameInput = ref('')
const showGroupDialog = ref(false)
const isSaving = ref(false)

const selectedLabelCount = computed(() => selectedLabelIds.value.size)
const groupNames = computed(() => labels.value.filter(isGroupMeta).map(group => group.name))
const moveToGroupItems = computed<DropdownMenuItem[]>(() =>
  groupNames.value.map(groupName => ({
    label: groupName,
    icon: 'i-lucide-folder-input',
    onSelect: () => handleMoveSelectedToGroup(groupName)
  }))
)

const asRecord = (value: unknown): Record<string, unknown> => {
  if (!value || typeof value !== 'object') return {}
  return value as Record<string, unknown>
}

const getString = (value: unknown, fallback = ''): string => {
  if (typeof value === 'string') return value
  if (value === null || value === undefined) return fallback
  return String(value)
}

const stripUiFields = (labelList: BuilderEntry[]): LabelSetCreateOrUpdateRequest['labels'] => {
  const groupNameById = new Map<string, string>()
  for (const label of labelList) {
    if (isGroupMeta(label) && label.id) {
      groupNameById.set(label.id, label.name || label.id)
    }
  }
  return labelList
    .filter(isEditableLabelDefinition)
    .map((label) => {
      const mappedGroup = label.group && groupNameById.has(label.group)
        ? groupNameById.get(label.group)
        : label.group
      const pageRegionType = label.mapping?.pageXml?.regionType
      const normalizedHasText = pageRegionType === 'TextRegion'
      return {
        id: label.id,
        scope: label.scope,
        name: label.name,
        description: label.description || null,
        color: label.color,
        // TODO: Remove these persisted flags after PAGE-only label metadata is finalized.
        hasText: normalizedHasText,
        isContainer: label.isContainer,
        group: mappedGroup || null,
        mapping: {
          pageXml: {
            ...label.mapping.pageXml,
            customKey: CANONICAL_PAGE_CUSTOM_KEY,
            customData: ''
          }
        }
      }
    })
}

const toStrictPayload = (): LabelSetCreateOrUpdateRequest => {
  return {
    meta: {
      name: meta.name,
      description: meta.description || '',
      tags: meta.tags || [],
      defaultLabelId: meta.defaultLabelId || null
    },
    labels: stripUiFields(labels.value)
  }
}

const resetToDefaults = () => {
  Object.assign(meta, {
    name: 'My Custom Label Set',
    description: 'Optimized for historical document layout analysis',
    tags: [],
    isSystem: false,
    defaultLabelId: null
  })
  labels.value = []
  activeLabel.value = null
}

const ensureGroupMetas = () => {
  const groupIds = new Set<string>()
  const groupNameById = new Map<string, string>()

  for (const label of labels.value) {
    if (isGroupMeta(label) && label.id) {
      groupNameById.set(label.id, label.name || label.id)
    }
  }

  for (const label of labels.value) {
    if (isGroupMeta(label)) continue
    if (label?.group && groupNameById.has(label.group)) {
      label.group = groupNameById.get(label.group) ?? label.group
    }
    if (label?.group) {
      groupIds.add(label.group)
    }
  }

  labels.value = labels.value.filter(isEditableLabelDefinition)

  for (const groupId of groupIds) {
    labels.value.push({ id: groupId, name: groupId, isGroup: true })
  }
}

const loadLabelSet = async () => {
  if (isNew) {
    resetToDefaults()
    loadedCapabilities.value = null
    markSavedState()
    return
  }

  const { data, error } = await useFetch<LabelSet>(() => `/api/workspaces/${selectedWorkspace.value}/label-sets/${id}`, {
    key: labelSetKey
  })
  if (data.value) {
    Object.assign(meta, data.value.meta, {
      defaultLabelId: data.value.meta.defaultLabelId ?? null
    })
    labels.value = (data.value.labels ?? []).map(normalizeEditableLabel)
    loadedCapabilities.value = data.value.capabilities ?? null
    activeLabel.value = null
    ensureGroupMetas()
    markSavedState()
    return
  }

  if (error.value) {
    toast.add({ title: 'Error loading label set', color: 'error' })
    await router.push('/labels')
  }
}

await loadLabelSet()

const isSystemLabelSet = computed(() => meta?.isSystem ?? false)
const labelSetCapabilities = computed(() => ({
  ...DEFAULT_RESOURCE_CAPABILITIES,
  ...(loadedCapabilities.value ?? {})
}))
const canEditLabelSet = computed(() => isNew || allow(labelSetCapabilities.value.canEdit))
const canShareLabelSet = computed(() => !isNew && allow(labelSetCapabilities.value.canShare))
const canDeleteLabelSet = computed(() => !isNew && allow(labelSetCapabilities.value.canDelete))
const isReadOnlyLabelSet = computed(() => isSystemLabelSet.value || !canEditLabelSet.value)

const handleSave = async (navigateAfterCreate = true): Promise<boolean> => {
  if (!canEditLabelSet.value || isSaving.value) return false
  if (!isNew && !isDirty.value) return true
  if (totalErrors.value > 0) {
    toast.add({ title: 'Fix label configuration errors before saving', color: 'warning' })
    return false
  }

  isSaving.value = true
  try {
    const payload = toStrictPayload()

    if (isNew) {
      const saved = await $fetch<LabelSet>(`/api/workspaces/${selectedWorkspace.value}/label-sets`, {
        method: 'POST',
        body: payload
      })
      markSavedState()
      toast.add({ title: 'Label set created', color: 'success' })
      await refreshNuxtData(labelSetsKey.value)
      if (navigateAfterCreate) {
        await router.push(`/labels/${saved.id}`)
      }
    } else {
      await $fetch<LabelSet>(`/api/workspaces/${selectedWorkspace.value}/label-sets/${id}`, {
        method: 'PUT',
        body: payload
      })
      markSavedState()
      toast.add({ title: 'Label set updated', color: 'success' })
      await refreshNuxtData(labelSetKey.value)
      await refreshNuxtData(labelSetsKey.value)
    }
    return true
  } catch (e: unknown) {
    const description = e instanceof Error ? e.message : undefined
    toast.add({ title: 'Error saving label set', description, color: 'error' })
    return false
  } finally {
    isSaving.value = false
  }
}

const handleDeleteSet = async () => {
  if (!canDeleteLabelSet.value) return
  if (isNew) return

  const instance = deleteSlideover.open({
    name: meta.name,
    entityType: 'Label Set',
    warningMessage: 'This action cannot be undone! All projects using this label set will lose their label configuration.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/label-sets/${id}`, { method: 'DELETE' })
    markSavedState()
    toast.add({ title: 'Label set deleted', color: 'success' })
    await refreshNuxtData(labelSetsKey.value)
    await router.push('/labels')
  } catch {
    toast.add({ title: 'Error deleting label set', color: 'error' })
  }
}

const triggerImport = () => {
  if (!canEditLabelSet.value) return
  fileInput.value?.click()
}

const handleImportFile = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const content = await file.text()
    const existingLabelSets = await $fetch<LabelSetSummary[]>(`/api/workspaces/${selectedWorkspace.value}/label-sets`)
    const preview = buildLabelSetImportPreview(content, {
      fileName: file.name,
      existingNames: existingLabelSets.map(labelSet => labelSet.meta.name),
      current: toStrictPayload()
    })
    const previewInstance = importPreviewModal.open({ preview })
    const confirmed = await previewInstance.result
    if (!confirmed) return

    const { runTrackedProcessing } = useTrackedUpload()
    const result = await runTrackedProcessing<{
      resources?: Array<{ type: string, targetId: string, targetName: string }>
    }>({
      title: 'Importing label set package',
      workspaceId: selectedWorkspace.value || 'workspace',
      files: [{ file }],
      task: async () => {
        return await $fetch<{
          resources?: Array<{ type: string, targetId: string, targetName: string }>
        }>(`/api/workspaces/${selectedWorkspace.value}/toolkit/import`, {
          method: 'POST',
          body: { content }
        })
      }
    })

    const imported = result.resources?.find(r => r.type === 'LABEL_SET')
    await refreshNuxtData(labelSetsKey.value)
    if (imported?.targetId) {
      await router.push(`/labels/${imported.targetId}`)
    }

    toast.add({
      title: 'Label set imported',
      description: imported?.targetName ? `Imported as "${imported.targetName}"` : undefined,
      color: 'success'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to import label set package'
    toast.add({ title: 'Import failed', description: message, color: 'error' })
  } finally {
    input.value = ''
  }
}

const exportSet = async () => {
  if (isNew) {
    await exportSetLocal()
    return
  }

  try {
    const labelSetName = getString(asRecord(meta).name, 'label-set')
    const fallbackName = buildToolkitPackageFileName(labelSetName, 'label-set')
    const target = await backgroundDownloads.prepareDownload(fallbackName)
    if (!target) return

    await backgroundDownloads.runBackgroundJob({
      title: 'Exporting label set',
      subtitle: getString(asRecord(meta).name, 'Label set'),
      statusLabel: 'Generating',
      completedLabel: 'Exported',
      icon: 'i-lucide-tags',
      task: async (job) => {
        const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/toolkit/export`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            includeAll: false,
            selectors: [{ type: 'LABEL_SET', ids: [id] }]
          })
        })

        if (!response.ok) {
          throw new Error(`Export failed (${response.status})`)
        }

        await backgroundDownloads.downloadBlobResponse(response, fallbackName, job, target)
      }
    })

    toast.add({ title: 'Label set exported', color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to export label set package'
    toast.add({ title: 'Export failed', description: message, color: 'error' })
  }
}

const exportSetLocal = async () => {
  const doExport = async () => {
    const labelSetName = getString(asRecord(meta).name, 'label-set')
    const fileName = `${labelSetName}-labelset.json`
    const target = await backgroundDownloads.prepareDownload(fileName)
    if (!target) return

    await backgroundDownloads.runBackgroundJob({
      title: 'Downloading label set',
      subtitle: getString(asRecord(meta).name, 'Label set'),
      statusLabel: 'Preparing',
      completedLabel: 'Downloaded',
      icon: 'i-lucide-download',
      task: async (job) => {
        const data = { meta, labels: labels.value, exportedAt: new Date().toISOString() }
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
        await backgroundDownloads.downloadBlob(blob, fileName, job, target)
      }
    }).catch((error: unknown) => {
      const message = error instanceof Error ? error.message : 'Failed to export label set'
      toast.add({ title: 'Export failed', description: message, color: 'error' })
    })
  }

  if (totalErrors.value > 0) {
    const instance = confirmModal.open({
      title: 'Export with Errors?',
      description: 'There are validation errors in the label set. Export anyway?',
      confirmLabel: 'Export Anyway',
      confirmColor: 'warning'
    })
    const confirmed = await instance.result
    if (confirmed) await doExport()
  } else {
    await doExport()
  }
}

const handleOptimize = async () => {
  if (isReadOnlyLabelSet.value) return
  const instance = confirmModal.open({
    title: 'Auto-Assign Colors?',
    description: 'This will overwrite all label colors with optimized values for visual distinction.',
    confirmLabel: 'Optimize Colors'
  })
  const confirmed = await instance.result

  if (confirmed) {
    optimizeColors()
    toast.add({ title: 'Colors optimized', color: 'success' })
  }
}

async function handleShareLabelSet() {
  if (!canShareLabelSet.value) return

  const instance = shareSlideover.open({
    resourceId: id,
    resourceName: meta.name,
    resourceType: 'LABEL_SET',
    currentWorkspaceId: workspaceId.value
  })
  const transferred = await instance.result
  if (transferred) {
    await refreshNuxtData(labelSetsKey.value)
  }
}

const handleDelete = async (labelId: string) => {
  if (isReadOnlyLabelSet.value) return
  const label = labels.value.find(l => l.id === labelId)
  const instance = confirmModal.open({
    title: 'Delete Label?',
    description: `Are you sure you want to delete "${label?.name || 'this label'}"?`,
    confirmLabel: 'Delete',
    confirmColor: 'error'
  })
  const confirmed = await instance.result

  if (confirmed) {
    deleteLabel(labelId)
    toast.add({ title: 'Label deleted', color: 'success' })
  }
}

const openGroupSelectedDialog = () => {
  if (isReadOnlyLabelSet.value || selectedLabelCount.value < 2) return
  groupNameInput.value = ''
  showGroupDialog.value = true
}

const confirmGroupSelected = () => {
  if (isReadOnlyLabelSet.value || selectedLabelCount.value < 2) return
  const count = selectedLabelCount.value
  const groupName = groupNameInput.value.trim() || 'Group'
  const groupId = groupSelectedLabels(groupName)
  groupNameInput.value = ''
  showGroupDialog.value = false
  if (groupId) {
    toast.add({ title: 'Labels grouped', description: `${count} label${count === 1 ? '' : 's'} moved to "${groupId}".`, color: 'success' })
  }
}

const cancelGroupSelected = () => {
  groupNameInput.value = ''
  showGroupDialog.value = false
}

const handleMoveSelectedToGroup = (groupName: string) => {
  if (isReadOnlyLabelSet.value || selectedLabelCount.value === 0) return
  const count = selectedLabelCount.value
  moveSelectedToGroup(groupName)
  toast.add({ title: 'Labels moved', description: `${count} label${count === 1 ? '' : 's'} moved to "${groupName}".`, color: 'success' })
}

const handleDeleteSelected = async () => {
  if (isReadOnlyLabelSet.value || selectedLabelCount.value === 0) return

  const count = selectedLabelCount.value
  const names = selectedLabels.value.map(label => label.name || 'Untitled')
  const instance = confirmModal.open({
    title: count === 1 ? 'Delete Selected Label?' : 'Delete Selected Labels?',
    description: count === 1
      ? `Are you sure you want to delete "${names[0] ?? 'this label'}"?`
      : `Delete ${count} selected labels? This action cannot be undone.`,
    confirmLabel: count === 1 ? 'Delete Label' : 'Delete Labels',
    confirmColor: 'error'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  deleteSelectedLabels()
  toast.add({ title: count === 1 ? 'Label deleted' : 'Labels deleted', description: `${count} label${count === 1 ? '' : 's'} removed.`, color: 'success' })
}

const openSettings = () => {
  if (isReadOnlyLabelSet.value) return
  metadataSlideover.open({ isNew, onSave: () => handleSave() })
}

const confirmNavigationAway = async (): Promise<boolean> => {
  if (!isDirty.value) return true

  const instance = unsavedProgressSlideover.open({
    title: 'Unsaved label set changes',
    message: 'This label set has changes that have not been saved. What would you like to do?',
    confirmLabel: 'Save and leave',
    discardLabel: 'Discard changes',
    cancelLabel: 'Keep editing',
    confirmColor: 'primary',
    discardColor: 'warning'
  })
  const action = await instance.result

  if (action === 'save') {
    return await handleSave(false)
  }
  if (action === 'discard') {
    return true
  }
  return false
}

if (import.meta.client) {
  const onBeforeUnload = (event: BeforeUnloadEvent) => {
    if (!isDirty.value) return
    event.preventDefault()
  }
  onMounted(() => window.addEventListener('beforeunload', onBeforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))
}

onBeforeRouteLeave(async () => {
  if (await confirmNavigationAway()) return
  return false
})
</script>

<template>
  <UDashboardPanel :ui="{ body: 'p-0 sm:p-0' }">
    <template #header>
      <LabelBuilderHeader
        :is-new="isNew"
        :is-system="isReadOnlyLabelSet"
        :is-dirty="isDirty"
        :is-saving="isSaving"
        :can-share="canShareLabelSet"
        :breadcrumb-items="breadcrumbItems"
        help-title="About Label Sets"
        help-description="Label sets define structural annotation vocabularies and their PAGE XML region mappings."
        :help-items="[
          'Use mappings to align labels with your PAGE XML region model.',
          'Keep labels visually distinct so annotators can read segmentation state quickly.',
          'Import, export, and share label sets as reusable workspace toolkit resources.'
        ]"
        @import="triggerImport"
        @export="exportSet"
        @save="handleSave"
        @share="handleShareLabelSet"
        @optimize="handleOptimize"
        @open-settings="openSettings"
      />
    </template>
    <template #body>
      <div class="h-full flex overflow-hidden">
        <LabelBuilderSidebar
          :is-system="isReadOnlyLabelSet"
          @create="createLabel"
          @select="selectLabel"
          @delete="handleDelete"
        />

        <section class="relative flex flex-1 flex-col bg-muted/10">
          <div v-if="activeLabel" class="flex-1 flex flex-col lg:flex-row h-full">
            <LabelBuilderEditor :is-system="isReadOnlyLabelSet" />
            <LabelBuilderPreview />
          </div>

          <div v-else class="flex flex-1 flex-col items-center justify-center px-6 text-muted">
            <div class="mb-4 flex size-12 items-center justify-center rounded-lg border border-default bg-default">
              <UIcon name="i-lucide-tags" class="size-5" />
            </div>
            <h2 class="mb-1 text-base font-semibold text-highlighted">
              Select a label
            </h2>
            <p class="max-w-xs text-center text-sm">
              Choose a label from the sidebar to edit its mapping and appearance.
            </p>

            <UPageCard
              v-if="isSystemLabelSet"
              class="mt-6"
              title="System Label Set"
              description="This is a system-provided label set. Changes are not allowed."
              variant="subtle"
              color="warning"
              spotlight
              spotlight-color="warning"
              icon="i-lucide-lock"
            />

            <div v-if="!isNew && canDeleteLabelSet" class="mt-8 space-y-2 flex flex-col">
              <UButton
                label="Delete Label Set"
                color="error"
                variant="subtle"
                icon="i-lucide-trash"
                @click="handleDeleteSet"
              />
            </div>
          </div>
        </section>
      </div>

      <UiFloatingSelectionMenu
        :selected-count="selectedLabelCount"
        @clear="clearSelection"
      >
        <UButton
          v-if="!isReadOnlyLabelSet && selectedLabelCount > 1"
          icon="i-lucide-folder-plus"
          color="neutral"
          variant="ghost"
          size="sm"
          class="text-neutral-50 hover:bg-white/10"
          aria-label="Group selected labels"
          @click="openGroupSelectedDialog"
        >
          <span class="hidden sm:inline">Group</span>
        </UButton>

        <UDropdownMenu
          v-if="!isReadOnlyLabelSet && moveToGroupItems.length > 0"
          :items="moveToGroupItems"
          :content="{ align: 'end' }"
        >
          <UButton
            icon="i-lucide-folder-input"
            color="neutral"
            variant="ghost"
            size="sm"
            class="text-neutral-50 hover:bg-white/10"
            aria-label="Move selected labels to group"
          >
            <span class="hidden sm:inline">Move</span>
          </UButton>
        </UDropdownMenu>

        <UButton
          v-if="!isReadOnlyLabelSet"
          icon="i-lucide-trash-2"
          color="error"
          variant="ghost"
          size="sm"
          class="hover:bg-white/10"
          aria-label="Delete selected labels"
          @click="handleDeleteSelected"
        >
          <span class="hidden sm:inline">Delete</span>
        </UButton>
      </UiFloatingSelectionMenu>

      <div v-if="showGroupDialog" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4" @click="cancelGroupSelected">
        <UCard class="w-80 max-w-full" @click.stop>
          <template #header>
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-folder-plus" class="w-5 h-5" />
              <span class="font-semibold">Create Group</span>
            </div>
          </template>

          <UFormField label="Group name">
            <UInput
              v-model="groupNameInput"
              placeholder="Enter group name"
              autofocus
              @keyup.enter="confirmGroupSelected"
            />
          </UFormField>

          <template #footer>
            <div class="flex justify-end gap-2">
              <UButton color="neutral" variant="ghost" @click="cancelGroupSelected">
                Cancel
              </UButton>
              <UButton color="primary" @click="confirmGroupSelected">
                Create
              </UButton>
            </div>
          </template>
        </UCard>
      </div>

      <input
        ref="fileInput"
        type="file"
        class="hidden"
        accept=".json"
        @change="handleImportFile"
      >
    </template>
  </UDashboardPanel>
</template>
