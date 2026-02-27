<script setup lang="ts">
import type { LabelSet, LabelSetCreateOrUpdateRequest } from '@/types/label-set'
import { wsKey } from '@/utils/fetch-keys'
import { LazyLabelBuilderSlideoverMetadata, LazyUiDeleteSlideover, LazyUiConfirmModal, LazyShareSlideover } from '#components'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const overlay = useOverlay()
const shareSlideover = overlay.create(LazyShareSlideover)
const metadataSlideover = overlay.create(LazyLabelBuilderSlideoverMetadata)
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const confirmModal = overlay.create(LazyUiConfirmModal)

const workspace = useWorkspaceStore()

if (!workspace.hasFetched) {
  await workspace.fetchWorkspaces()
}

const selectedWorkspace = computed(() => workspace.selectedWorkspaceId as string)

const id = route.params.id as string
const isNew = id === 'new'

const labelSetsKey = computed(() => wsKey(selectedWorkspace.value, 'label-sets', 'list'))
const labelSetKey = computed(() => wsKey(selectedWorkspace.value, 'label-sets', id))

const breadcrumbItems = computed(() => [
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
  duplicateLabel,
  selectLabel,
  createMapping,
  optimizeColors
} = useLabelBuilder()

const fileInput = ref<HTMLInputElement | null>(null)

const asRecord = (value: unknown): Record<string, unknown> => {
  if (!value || typeof value !== 'object') return {}
  return value as Record<string, unknown>
}

const getString = (value: unknown, fallback = ''): string => {
  if (typeof value === 'string') return value
  if (value === null || value === undefined) return fallback
  return String(value)
}

const stripUiFields = (labelList: typeof labels.value): LabelSetCreateOrUpdateRequest['labels'] => {
  const groupNameById = new Map<string, string>()
  for (const label of labelList) {
    if (label?.isGroup && label.id) {
      groupNameById.set(label.id, label.name || label.id)
    }
  }
  return labelList
    .filter(label => !label.isGroup)
    .map((label) => {
      const mappedGroup = label.group && groupNameById.has(label.group)
        ? groupNameById.get(label.group)
        : label.group
      const pageRegionType = label.mapping?.pageXml?.regionType
      const altoBlockType = label.mapping?.altoXml?.blockType
      const normalizedHasText = label.scope === 'line' || pageRegionType === 'TextRegion'
      const normalizedIsContainer = label.scope === 'region' && altoBlockType === 'ComposedBlock'
      return {
        id: label.id,
        scope: label.scope,
        name: label.name,
        description: label.description || null,
        color: label.color,
        // TODO: Remove these persisted flags after PAGE-only label metadata is finalized.
        hasText: normalizedHasText,
        isContainer: normalizedIsContainer,
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
      tags: meta.tags || [],
      altoEnabled: meta.altoEnabled || false
    },
    labels: stripUiFields(labels.value)
  }
}

const resetToDefaults = () => {
  Object.assign(meta, {
    name: 'My Custom Label Set',
    description: 'Optimized for historical document layout analysis',
    tags: [],
    altoEnabled: false,
    isSystem: false
  })
  labels.value = []
  activeLabel.value = null
}

const ensureGroupMetas = () => {
  const groupIds = new Set<string>()
  const groupNameById = new Map<string, string>()

  for (const label of labels.value) {
    if (label?.isGroup && label.id) {
      groupNameById.set(label.id, label.name || label.id)
    }
  }

  for (const label of labels.value) {
    if (label?.isGroup) continue
    if (label?.group && groupNameById.has(label.group)) {
      label.group = groupNameById.get(label.group)
    }
    if (label?.group) {
      groupIds.add(label.group)
    }
  }

  labels.value = labels.value.filter(label => !label?.isGroup)

  for (const groupId of groupIds) {
    labels.value.push({ id: groupId, name: groupId, isGroup: true })
  }
}

const loadLabelSet = async () => {
  if (isNew) {
    resetToDefaults()
    return
  }

  const { data, error } = await useFetch<LabelSet>(() => `/api/workspaces/${selectedWorkspace.value}/label-sets/${id}`, {
    key: labelSetKey
  })
  if (data.value) {
    Object.assign(meta, data.value.meta)
    labels.value = (data.value.labels ?? []) as unknown as typeof labels.value
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

const handleSave = async () => {
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

const triggerImport = () => fileInput.value?.click()

const handleImportFile = async (e: Event) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    const content = await file.text()
    const result = await $fetch<{
      resources?: Array<{ type: string, targetId: string, targetName: string }>
    }>(`/api/workspaces/${selectedWorkspace.value}/utilities/import`, {
      method: 'POST',
      body: { content }
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
    const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/utilities/export`, {
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

    const blob = await response.blob()
    const fallbackName = `${getString(asRecord(meta).name, 'label-set').replace(/\\s+/g, '-').toLowerCase()}.larex-utilities.json`
    const contentDisposition = response.headers.get('content-disposition')
    const match = contentDisposition?.match(/filename\*?=(?:UTF-8''|"?)([^";]+)/i)
    const fileName = match ? decodeURIComponent(match[1]!) : fallbackName

    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    toast.add({ title: 'Label set exported', color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to export label set package'
    toast.add({ title: 'Export failed', description: message, color: 'error' })
  }
}

const exportSetLocal = async () => {
  const doExport = () => {
    const data = { meta, labels: labels.value, exportedAt: new Date().toISOString() }
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${getString(asRecord(meta).name, 'label-set').replace(/\s+/g, '-').toLowerCase()}-labelset.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  }

  if (totalErrors.value > 0) {
    const instance = confirmModal.open({
      title: 'Export with Errors?',
      message: 'There are validation errors in the label set. Export anyway?',
      confirmLabel: 'Export Anyway',
      confirmColor: 'warning'
    })
    const confirmed = await instance.result
    if (confirmed) doExport()
  } else {
    doExport()
  }
}

const handleOptimize = async () => {
  const instance = confirmModal.open({
    title: 'Auto-Assign Colors?',
    message: 'This will overwrite all label colors with optimized values for visual distinction.',
    confirmLabel: 'Optimize Colors'
  })
  const confirmed = await instance.result

  if (confirmed) {
    optimizeColors()
    toast.add({ title: 'Colors optimized', color: 'success' })
  }
}

const handleDelete = async (labelId: string) => {
  const label = labels.value.find(l => l.id === labelId)
  const instance = confirmModal.open({
    title: 'Delete Label?',
    message: `Are you sure you want to delete "${label?.name || 'this label'}"?`,
    confirmLabel: 'Delete',
    confirmColor: 'error'
  })
  const confirmed = await instance.result

  if (confirmed) {
    deleteLabel(labelId)
    toast.add({ title: 'Label deleted', color: 'success' })
  }
}

const handleScopeSwitch = async (targetScope: string) => {
  if (isSystemLabelSet.value) return
  if (!activeLabel.value) return

  const instance = confirmModal.open({
    title: 'Change Scope?',
    message: 'This will reset the label mapping configurations. Continue?',
    confirmLabel: 'Change Scope',
    confirmColor: 'warning'
  })
  const confirmed = await instance.result

  if (confirmed) {
    activeLabel.value.scope = targetScope
    activeLabel.value.mapping = createMapping(activeLabel.value.name, targetScope)
    activeLabel.value.hasText = true
    activeLabel.value.isContainer = false
  }
}

const openSettings = () => {
  metadataSlideover.open({ onSave: handleSave })
}
</script>

<template>
  <UDashboardPanel :ui="{ body: 'p-0 sm:p-0' }">
    <template #header>
      <LabelBuilderHeader
        :is-new="isNew"
        :is-system="isSystemLabelSet"
        :breadcrumb-items="breadcrumbItems"
        @import="triggerImport"
        @export="exportSet"
        @save="handleSave"
        @optimize="handleOptimize"
        @open-settings="openSettings"
      />
    </template>
    <template #body>
      <div class="h-full flex overflow-hidden">
        <LabelBuilderSidebar
          :is-system="isSystemLabelSet"
          @create="createLabel"
          @select="selectLabel"
          @delete="handleDelete"
          @duplicate="duplicateLabel"
        />

        <section class="flex-1 bg-neutral-50/70 dark:bg-neutral-900 flex flex-col relative">
          <div v-if="activeLabel" class="flex-1 flex flex-col lg:flex-row h-full">
            <LabelBuilderEditor :is-system="isSystemLabelSet" @change-scope="handleScopeSwitch" />
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
              v-if="!isSystemLabelSet"
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

            <div v-if="!isNew && !isSystemLabelSet" class="mt-8 space-y-2 flex flex-col">
              <UButton
                label="Share Label Set"
                color="neutral"
                variant="soft"
                icon="i-lucide-share-2"
                @click="shareSlideover.open({ resourceId: id, resourceName: meta.name, resourceType: 'LABEL_SET', currentWorkspaceId: selectedWorkspace })"
              />
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
