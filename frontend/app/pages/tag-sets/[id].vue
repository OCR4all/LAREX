<script setup lang="ts">
import type { BreadcrumbItem } from '@nuxt/ui'
import type { TagSet, TagSetCreateOrUpdateRequest } from '@/types/tag-set'
import { DEFAULT_RESOURCE_CAPABILITIES, type ResourceCapabilities } from '@/types/capabilities'
import {
  LazyShareSlideover,
  LazyTagSetBuilderSlideoverMetadata,
  LazyUiDeleteSlideover,
  LazyUiConfirmSlideover
} from '#components'
import { buildToolkitPackageFileName } from '@/utils/download-file-names'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const overlay = useOverlay()
const backgroundDownloads = useBackgroundDownloads()
const { allow } = useActionVisibility()

const metadataSlideover = overlay.create(LazyTagSetBuilderSlideoverMetadata)
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)
const shareSlideover = overlay.create(LazyShareSlideover)

const { selectedWorkspace } = await useWorkspaceBootstrap()
const workspaceId = computed(() => selectedWorkspace.value ?? '')

const id = route.params.id as string
const isNew = id === 'new'

const tagSetsKey = computed(() => wsKey(workspaceId.value, 'tag-sets', 'list'))
const tagSetKey = computed(() => wsKey(workspaceId.value, 'tag-sets', id))
const loadedCapabilities = ref<ResourceCapabilities | null>(null)

const breadcrumbItems = computed<BreadcrumbItem[]>(() => [
  { label: 'Home', icon: 'i-lucide-home', to: '/' },
  { label: 'Tags', icon: 'i-lucide-network', to: '/tag-sets' },
  { label: isNew ? 'New Tag Set' : (meta.name || id) }
])

const {
  meta,
  tags,
  activeTag,
  totalErrors,
  createTag,
  deleteTag,
  duplicateTag,
  selectTag,
  optimizeColors,
  reset
} = useTagSetBuilder()

const fileInput = ref<HTMLInputElement | null>(null)

const resetToDefaults = () => {
  reset()
  Object.assign(meta, {
    name: 'My Tag Set',
    description: '',
    tags: []
  })
}

const loadTagSet = async () => {
  if (isNew) {
    resetToDefaults()
    loadedCapabilities.value = null
    return
  }

  const { data, error } = await useFetch<TagSet>(() => `/api/workspaces/${selectedWorkspace.value}/tag-sets/${id}`, {
    key: tagSetKey
  })

  if (data.value) {
    Object.assign(meta, data.value.meta)
    tags.value = data.value.tags ?? []
    loadedCapabilities.value = data.value.capabilities ?? null
    activeTag.value = null
    return
  }

  if (error.value) {
    toast.add({ title: 'Error loading tag set', color: 'error' })
    await router.push('/tag-sets')
  }
}

await loadTagSet()
const tagSetCapabilities = computed(() => ({
  ...DEFAULT_RESOURCE_CAPABILITIES,
  ...(loadedCapabilities.value ?? {})
}))
const canEditTagSet = computed(() => isNew || allow(tagSetCapabilities.value.canEdit))
const canShareTagSet = computed(() => !isNew && allow(tagSetCapabilities.value.canShare))
const canDeleteTagSet = computed(() => !isNew && allow(tagSetCapabilities.value.canDelete))

const stripUiFields = (tagList: typeof tags.value): TagSetCreateOrUpdateRequest['tags'] => {
  return tagList.map(tag => ({
    id: tag.id,
    title: tag.title,
    description: tag.description || null,
    color: tag.color,
    children: tag.children ? stripUiFields(tag.children as typeof tags.value) : undefined
  }))
}

const toStrictPayload = (): TagSetCreateOrUpdateRequest => {
  return {
    meta: {
      name: meta.name,
      description: meta.description || '',
      tags: meta.tags || []
    },
    tags: stripUiFields(tags.value)
  }
}

const handleSave = async () => {
  if (!canEditTagSet.value) return
  if (totalErrors.value > 0) {
    toast.add({ title: 'Fix tag configuration errors before saving', color: 'warning' })
    return
  }

  try {
    const payload = toStrictPayload()

    if (isNew) {
      const saved = await $fetch<TagSet>(`/api/workspaces/${selectedWorkspace.value}/tag-sets`, {
        method: 'POST',
        body: payload
      })
      toast.add({ title: 'Tag set created', color: 'success' })
      await refreshNuxtData(tagSetsKey.value)
      await router.push(`/tag-sets/${saved.id}`)
    } else {
      await $fetch<TagSet>(`/api/workspaces/${selectedWorkspace.value}/tag-sets/${id}`, {
        method: 'PUT',
        body: payload
      })
      toast.add({ title: 'Tag set updated', color: 'success' })
      await refreshNuxtData(tagSetKey.value)
      await refreshNuxtData(tagSetsKey.value)
    }
  } catch (e: unknown) {
    const description = e instanceof Error ? e.message : undefined
    toast.add({ title: 'Error saving tag set', description, color: 'error' })
  }
}

const handleDeleteTagSet = async () => {
  if (!canDeleteTagSet.value) return
  if (isNew) return

  const instance = deleteSlideover.open({
    name: meta.name,
    entityType: 'Tag Set',
    warningMessage: 'This action cannot be undone! All projects using this tag set will lose their tag structure reference.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/tag-sets/${id}`, { method: 'DELETE' })
    toast.add({ title: 'Tag set deleted', color: 'success' })
    await refreshNuxtData(tagSetsKey.value)
    await router.push('/tag-sets')
  } catch {
    toast.add({ title: 'Error deleting tag set', color: 'error' })
  }
}

const triggerImport = () => {
  if (!canEditTagSet.value) return
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
      title: 'Importing tag set package',
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

    const imported = result.resources?.find(r => r.type === 'TAG_SET')
    await refreshNuxtData(tagSetsKey.value)
    if (imported?.targetId) {
      await router.push(`/tag-sets/${imported.targetId}`)
    }

    toast.add({
      title: 'Tag set imported',
      description: imported?.targetName ? `Imported as "${imported.targetName}"` : undefined,
      color: 'success'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to import tag set package'
    toast.add({ title: 'Import failed', description: message, color: 'error' })
  } finally {
    ;(e.target as HTMLInputElement).value = ''
  }
}

const exportTagSet = async () => {
  if (isNew) {
    await exportTagSetLocal()
    return
  }

  try {
    const fallbackName = buildToolkitPackageFileName(meta.name, 'tag-set')
    const target = await backgroundDownloads.prepareDownload(fallbackName)
    if (!target) return

    await backgroundDownloads.runBackgroundJob({
      title: 'Exporting tag set',
      subtitle: meta.name || 'Tag set',
      statusLabel: 'Generating',
      completedLabel: 'Exported',
      icon: 'i-lucide-network',
      task: async (job) => {
        const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/toolkit/export`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            includeAll: false,
            selectors: [{ type: 'TAG_SET', ids: [id] }]
          })
        })

        if (!response.ok) {
          throw new Error(`Export failed (${response.status})`)
        }

        await backgroundDownloads.downloadBlobResponse(response, fallbackName, job, target)
      }
    })

    toast.add({ title: 'Tag set exported', color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to export tag set package'
    toast.add({ title: 'Export failed', description: message, color: 'error' })
  }
}

const exportTagSetLocal = async () => {
  const doExport = async () => {
    const fileName = `${meta.name || 'tag-set'}.json`
    const target = await backgroundDownloads.prepareDownload(fileName)
    if (!target) return

    await backgroundDownloads.runBackgroundJob({
      title: 'Downloading tag set',
      subtitle: meta.name || 'Tag set',
      statusLabel: 'Preparing',
      completedLabel: 'Downloaded',
      icon: 'i-lucide-download',
      task: async (job) => {
        const data = { meta, tags: tags.value, exportedAt: new Date().toISOString() }
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
        await backgroundDownloads.downloadBlob(blob, fileName, job, target)
      }
    }).catch((error: unknown) => {
      const message = error instanceof Error ? error.message : 'Failed to export tag set'
      toast.add({ title: 'Export failed', description: message, color: 'error' })
    })
  }

  if (totalErrors.value > 0) {
    const instance = confirmSlideover.open({
      title: 'Export with Errors?',
      message: 'There are validation errors in the tag set. Export anyway?',
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
  if (!canEditTagSet.value) return
  const instance = confirmSlideover.open({
    title: 'Auto-Assign Colors?',
    message: 'This will overwrite all tag colors with optimized values for visual distinction.',
    confirmLabel: 'Optimize Colors'
  })
  const confirmed = await instance.result

  if (confirmed) {
    optimizeColors()
    toast.add({ title: 'Colors optimized', color: 'success' })
  }
}

async function handleShareTagSet() {
  if (!canShareTagSet.value) return

  const instance = shareSlideover.open({
    resourceId: id,
    resourceName: meta.name,
    resourceType: 'TAG_SET',
    currentWorkspaceId: workspaceId.value
  })
  const transferred = await instance.result
  if (transferred) {
    await refreshNuxtData(tagSetsKey.value)
  }
}

const handleDeleteTag = async (tagId: string) => {
  if (!canEditTagSet.value) return
  const tag = tags.value.find(t => t.id === tagId)
    || tags.value.flatMap(function findDeep(t): typeof tags.value { return [t, ...(t.children?.flatMap(findDeep) || [])] }).find(t => t.id === tagId)

  const instance = confirmSlideover.open({
    title: 'Delete Tag?',
    message: `Are you sure you want to delete "${tag?.title || 'this tag'}"? This will also delete all child tags.`,
    confirmLabel: 'Delete',
    confirmColor: 'error'
  })
  const confirmed = await instance.result

  if (confirmed) {
    deleteTag(tagId)
    toast.add({ title: 'Tag deleted', color: 'success' })
  }
}

const openSettings = () => {
  if (!canEditTagSet.value) return
  metadataSlideover.open({ onSave: handleSave })
}
</script>

<template>
  <UDashboardPanel :ui="{ body: 'p-0 sm:p-0' }">
    <template #header>
      <TagSetBuilderHeader
        :is-new="isNew"
        :is-read-only="!canEditTagSet"
        :can-share="canShareTagSet"
        :breadcrumb-items="breadcrumbItems"
        help-title="About Tag Sets"
        help-description="Tag sets define reusable hierarchical taxonomies for classification, review, and downstream filtering across projects."
        :help-items="[
          'Build nested tag structures to reflect editorial or workflow-specific categories.',
          'Use color deliberately so related tags stay legible in dense interfaces.',
          'Import, export, and share tag sets like the other workspace toolkit resources.'
        ]"
        @import="triggerImport"
        @export="exportTagSet"
        @save="handleSave"
        @share="handleShareTagSet"
        @optimize="handleOptimize"
        @open-settings="openSettings"
      />
    </template>

    <template #body>
      <div class="h-full flex overflow-hidden">
        <TagSetBuilderSidebar
          @create="() => { if (canEditTagSet) createTag() }"
          @select="selectTag"
          @delete="(tagId) => { if (canEditTagSet) handleDeleteTag(tagId) }"
          @duplicate="(tagId) => { if (canEditTagSet) duplicateTag(tagId) }"
        />

        <section class="flex-1 bg-neutral-50/70 dark:bg-neutral-900 flex flex-col relative">
          <div v-if="activeTag" class="flex-1 flex flex-col lg:flex-row h-full">
            <TagSetBuilderEditor />
            <TagSetBuilderPreview />
          </div>

          <div v-else class="flex-1 flex flex-col items-center justify-center text-neutral-500">
            <div class="w-20 h-20 bg-neutral-100/60 dark:bg-neutral-800 rounded-sm mb-4 flex items-center justify-center shadow-inner">
              <UIcon name="i-lucide-network" class="w-10 h-10 text-neutral-600" />
            </div>
            <h2 class="text-lg font-bold text-black dark:text-white mb-2">
              No Tag Selected
            </h2>
            <p class="text-sm max-w-xs text-center">
              Select a tag from the sidebar or create a new one.
            </p>
            <UButton
              v-if="canEditTagSet"
              variant="solid"
              size="lg"
              class="mt-4"
              @click="() => { createTag() }"
            >
              Create Tag
            </UButton>

            <div v-if="canDeleteTagSet" class="mt-8 space-y-2 flex flex-col">
              <UButton
                label="Delete Tag Set"
                color="error"
                variant="soft"
                icon="i-lucide-trash"
                @click="handleDeleteTagSet"
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
