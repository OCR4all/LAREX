<script setup lang="ts">
import type { BreadcrumbItem, DropdownMenuItem } from '@nuxt/ui'
import type { LabelMapping, LabelScope, LabelSet, LabelSetCreateOrUpdateRequest } from '@/types/label-set'
import { DEFAULT_RESOURCE_CAPABILITIES, type ResourceCapabilities } from '@/types/capabilities'
import { LazyLabelBuilderSlideoverMetadata, LazyUiDeleteSlideover, LazyUiConfirmModal, LazyShareSlideover } from '#components'
import { isEditableLabelDefinition, isGroupMeta, type BuilderEntry } from '@/composables/use-label-builder'
import { buildToolkitPackageFileName } from '@/utils/download-file-names'

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
  duplicateLabel,
  selectLabel,
  createMapping,
  optimizeColors,
  selectedLabelIds,
  selectedLabels,
  clearSelection,
  groupSelectedLabels,
  moveSelectedToGroup
} = useLabelBuilder()

const fileInput = ref<HTMLInputElement | null>(null)
const groupNameInput = ref('')
const showGroupDialog = ref(false)

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

const toEditableMapping = (mapping: LabelMapping) => ({
  pageXml: {
    ...(mapping.pageXml.regionType ? { regionType: mapping.pageXml.regionType } : {}),
    ...(mapping.pageXml.textType ? { textType: mapping.pageXml.textType } : {}),
    customSubType: mapping.pageXml.customSubType ?? '',
    customKey: mapping.pageXml.customKey,
    customData: mapping.pageXml.customData ?? ''
  }
})

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
      const normalizedHasText = label.scope === 'line' || pageRegionType === 'TextRegion'
      return {
        id: label.id,
        scope: label.scope,
        name: label.name,
        description: label.description || null,
        color: label.color,
        // TODO: Remove these persisted flags after PAGE-only label metadata is finalized.
        hasText: normalizedHasText,
        isContainer: label.scope === 'region' && label.isContainer,
        group: mappedGroup || null,
        mapping: label.mapping
      }
    })
}

const toStrictPayload = (): LabelSetCreateOrUpdateRequest => {
  return {
    meta: {
      name: meta.name,
      description: meta.description || '',
      tags: meta.tags || []
    },
    labels: stripUiFields(labels.value)
  }
}

const resetToDefaults = () => {
  Object.assign(meta, {
    name: 'My Custom Label Set',
    description: 'Optimized for historical document layout analysis',
    tags: [],
    isSystem: false
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
    return
  }

  const { data, error } = await useFetch<LabelSet>(() => `/api/workspaces/${selectedWorkspace.value}/label-sets/${id}`, {
    key: labelSetKey
  })
  if (data.value) {
    Object.assign(meta, data.value.meta)
    labels.value = (data.value.labels ?? []) as unknown as typeof labels.value
    loadedCapabilities.value = data.value.capabilities ?? null
    activeLabel.value = null
    ensureGroupMetas()
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

const handleSave = async () => {
  if (!canEditLabelSet.value) return
  if (totalErrors.value > 0) {
    toast.add({ title: 'Fix label configuration errors before saving', color: 'warning' })
    return
  }

  try {
    const payload = toStrictPayload()

    if (isNew) {
      const saved = await $fetch<LabelSet>(`/api/workspaces/${selectedWorkspace.value}/label-sets`, {
        method: 'POST',
        body: payload
      })
      toast.add({ title: 'Label set created', color: 'success' })
      await refreshNuxtData(labelSetsKey.value)
      await router.push(`/labels/${saved.id}`)
    } else {
      await $fetch<LabelSet>(`/api/workspaces/${selectedWorkspace.value}/label-sets/${id}`, {
        method: 'PUT',
        body: payload
      })
      toast.add({ title: 'Label set updated', color: 'success' })
      await refreshNuxtData(labelSetKey.value)
      await refreshNuxtData(labelSetsKey.value)
    }
  } catch (e: unknown) {
    const description = e instanceof Error ? e.message : undefined
    toast.add({ title: 'Error saving label set', description, color: 'error' })
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
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    const { runTrackedProcessing } = useTrackedUpload()
    const result = await runTrackedProcessing<{
      resources?: Array<{ type: string, targetId: string, targetName: string }>
    }>({
      title: 'Importing label set package',
      workspaceId: selectedWorkspace.value || 'workspace',
      files: [{ file }],
      task: async () => {
        const content = await file.text()
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
    ;(e.target as HTMLInputElement).value = ''
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

const handleScopeSwitch = async (targetScope: LabelScope) => {
  if (isReadOnlyLabelSet.value) return
  if (!activeLabel.value) return

  const instance = confirmModal.open({
    title: 'Change Scope?',
    description: 'This will reset the label mapping configurations. Continue?',
    confirmLabel: 'Change Scope',
    confirmColor: 'warning'
  })
  const confirmed = await instance.result

  if (confirmed) {
    activeLabel.value.scope = targetScope
    activeLabel.value.mapping = toEditableMapping(createMapping(activeLabel.value.name, targetScope))
    activeLabel.value.hasText = true
    activeLabel.value.isContainer = false
  }
}

const openSettings = () => {
  if (isReadOnlyLabelSet.value) return
  metadataSlideover.open({ onSave: handleSave })
}
</script>

<template>
  <UDashboardPanel :ui="{ body: 'p-0 sm:p-0' }">
    <template #header>
      <LabelBuilderHeader
        :is-new="isNew"
        :is-system="isReadOnlyLabelSet"
        :can-share="canShareLabelSet"
        :breadcrumb-items="breadcrumbItems"
        help-title="About Label Sets"
        help-description="Label sets define structural annotation vocabularies and their export mappings for region and line annotation workflows."
        :help-items="[
          'Use scopes and mappings to align labels with your PAGE XML model.',
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
          @duplicate="duplicateLabel"
        />

        <section class="flex-1 bg-neutral-50/70 dark:bg-neutral-900 flex flex-col relative">
          <div v-if="activeLabel" class="flex-1 flex flex-col lg:flex-row h-full">
            <LabelBuilderEditor :is-system="isReadOnlyLabelSet" @change-scope="handleScopeSwitch" />
            <LabelBuilderPreview />
          </div>

          <div v-else class="flex-1 flex flex-col items-center justify-center text-neutral-500">
            <div class="w-20 h-20 bg-neutral-100/60 dark:bg-neutral-800 rounded-sm mb-4 flex items-center justify-center shadow-inner">
              <UIcon name="i-lucide-tags" class="w-10 h-10 text-neutral-600" />
            </div>
            <h2 class="text-lg font-bold text-black dark:text-white mb-2">
              No Label Selected
            </h2>
            <p class="text-sm max-w-xs text-center">
              Select a label from the sidebar or create a new one.
            </p>
            <UButton
              v-if="!isReadOnlyLabelSet"
              icon="i-mdi-tag-plus-outline"
              variant="solid"
              size="lg"
              class="mt-4"
              @click="createLabel"
            >
              Create Label
            </UButton>

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
