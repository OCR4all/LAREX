<script setup lang="ts">
import { LazyShareSlideover, LazyUiDeleteSlideover } from '#components'
import type { Dictionary, DictionaryCreateOrUpdateRequest } from '@/types/dictionary'
import { DEFAULT_RESOURCE_CAPABILITIES, type ResourceCapabilities } from '@/types/capabilities'
import type { DropdownMenuItem } from '@nuxt/ui'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const { allow } = useActionVisibility()
const { uploadFormDataWithProgress, runTrackedProcessing } = useTrackedUpload()
const overlay = useOverlay()
const shareSlideover = overlay.create(LazyShareSlideover)
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)

const { selectedWorkspace } = await useWorkspaceBootstrap()
const workspaceId = computed(() => selectedWorkspace.value ?? '')

const id = route.params.id as string
const isNew = id === 'new'
const isEmbeddedToolkitEditor = computed(() => route.query.embedded === 'toolkit-editor')
const dictionaryKey = computed(() => wsKey(workspaceId.value, 'dictionaries', id))
const dictionariesListKey = computed(() => wsKey(workspaceId.value, 'dictionaries', 'list'))
const importDictionaryInput = ref<HTMLInputElement | null>(null)
const entryBrowserRef = ref<{ refresh: (resetScroll?: boolean) => Promise<void> } | null>(null)
const isSaving = ref(false)
const isImporting = ref(false)
const isDeleting = ref(false)
const isExporting = ref(false)
const importProgress = ref<number | null>(null)
const importStage = ref<'idle' | 'uploading' | 'processing'>('idle')

const defaultDictionary: Dictionary = {
  id: '',
  name: 'New Dictionary',
  description: '',
  tags: [],
  caseSensitive: false,
  unicodeNormalization: 'NFC',
  locked: false,
  entryCount: 0,
  created: '',
  updated: ''
}

const loadedCapabilities = ref<ResourceCapabilities | null>(null)
let initial = defaultDictionary

function applyDictionaryState(dictionary: Dictionary) {
  loadedCapabilities.value = dictionary.capabilities ?? null
  name.value = dictionary.name
  description.value = dictionary.description ?? ''
  tags.value = [...(dictionary.tags ?? [])]
  caseSensitive.value = Boolean(dictionary.caseSensitive)
  unicodeNormalization.value = dictionary.unicodeNormalization || 'NFC'
  locked.value = Boolean(dictionary.locked)
  entryCount.value = Number(dictionary.entryCount ?? 0)
}

if (!isNew) {
  const { data, error } = await useFetch<Dictionary>(() => `/api/workspaces/${workspaceId.value}/dictionaries/${id}`, {
    key: dictionaryKey
  })
  if (data.value) {
    initial = data.value
  } else if (error.value) {
    toast.add({ title: 'Error loading dictionary', color: 'error' })
    router.push('/dictionaries')
  }
}

const dictionaryCapabilities = computed(() => ({
  ...DEFAULT_RESOURCE_CAPABILITIES,
  ...(loadedCapabilities.value ?? {})
}))
const canEditDictionary = computed(() => isNew || allow(dictionaryCapabilities.value.canEdit))
const canShareDictionary = computed(() => !isNew && allow(dictionaryCapabilities.value.canShare))
const canDeleteDictionary = computed(() => !isNew && allow(dictionaryCapabilities.value.canDelete))
const isBusy = computed(() => isSaving.value || isImporting.value || isDeleting.value || isExporting.value)
const hasEntries = computed(() => entryCount.value > 0)

const name = ref(initial.name)
const description = ref(initial.description ?? '')
const tags = ref<string[]>([...(initial.tags ?? [])])
const caseSensitive = ref(Boolean(initial.caseSensitive))
const unicodeNormalization = ref(initial.unicodeNormalization || 'NFC')
const locked = ref(Boolean(initial.locked))
const entryCount = ref(Number(initial.entryCount ?? 0))

if (!isNew) {
  applyDictionaryState(initial)
}

const breadcrumbItems = computed(() => [
  {
    label: 'Home',
    icon: 'i-lucide-home',
    to: '/'
  },
  {
    label: 'Dictionaries',
    icon: 'i-lucide-book-copy',
    to: '/dictionaries'
  },
  {
    label: isNew ? 'New Dictionary' : (name.value || 'Edit Dictionary')
  }
])

function toolkitEditorRoute(path: string) {
  return isEmbeddedToolkitEditor.value ? { path, query: { embedded: 'toolkit-editor' } } : path
}

function notifyToolkitEditorSaved(resourceId: string) {
  if (!import.meta.client || !isEmbeddedToolkitEditor.value) return
  window.parent?.postMessage({
    type: 'larex:toolkit-resource-saved',
    resourceType: 'dictionary',
    id: resourceId
  }, window.location.origin)
}

async function refreshDictionaryState() {
  if (isNew) return
  const dictionary = await $fetch<Dictionary>(`/api/workspaces/${workspaceId.value}/dictionaries/${id}`)
  applyDictionaryState(dictionary)
}

async function saveDictionary() {
  if (isSaving.value || isImporting.value || isDeleting.value) return

  const body: DictionaryCreateOrUpdateRequest = {
    name: name.value.trim(),
    description: description.value.trim() || null,
    tags: tags.value,
    caseSensitive: caseSensitive.value,
    unicodeNormalization: unicodeNormalization.value,
    locked: locked.value
  }

  const progressToast = toast.add({
    title: isNew ? 'Creating dictionary' : 'Saving dictionary',
    description: name.value.trim() || 'Dictionary',
    color: 'neutral',
    icon: 'i-lucide-loader-circle',
    ui: { icon: 'animate-spin' },
    close: false,
    progress: false,
    duration: 0
  })

  try {
    isSaving.value = true
    if (isNew) {
      const created = await $fetch<Dictionary>(`/api/workspaces/${workspaceId.value}/dictionaries`, {
        method: 'POST',
        body
      })
      await refreshNuxtData(dictionariesListKey.value)
      toast.add({ title: 'Dictionary created', description: `Created "${created.name}"`, color: 'success' })
      notifyToolkitEditorSaved(created.id)
      await navigateTo(toolkitEditorRoute(`/dictionaries/${created.id}`))
      return
    }

    await $fetch(`/api/workspaces/${workspaceId.value}/dictionaries/${id}`, {
      method: 'PUT',
      body
    })
    await refreshDictionaryState()
    toast.add({ title: 'Dictionary saved', color: 'success' })
    await Promise.all([
      refreshNuxtData(dictionaryKey.value),
      refreshNuxtData(dictionariesListKey.value)
    ])
    notifyToolkitEditorSaved(id)
  } catch (error: unknown) {
    toast.add({ title: 'Failed to save dictionary', description: extractApiErrorMessage(error, 'Failed to save dictionary'), color: 'error' })
  } finally {
    toast.remove(progressToast.id)
    isSaving.value = false
  }
}

async function deleteDictionary() {
  if (isDeleting.value) return
  const instance = deleteSlideover.open({
    name: name.value,
    entityType: 'Dictionary',
    warningMessage: 'This action cannot be undone.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const progressToast = toast.add({
    title: 'Deleting dictionary',
    description: name.value || 'Dictionary',
    color: 'neutral',
    icon: 'i-lucide-loader-circle',
    ui: { icon: 'animate-spin' },
    close: false,
    progress: false,
    duration: 0
  })

  try {
    isDeleting.value = true
    await $fetch(`/api/workspaces/${workspaceId.value}/dictionaries/${id}`, { method: 'DELETE' })
    await Promise.all([
      refreshNuxtData(dictionaryKey.value),
      refreshNuxtData(dictionariesListKey.value)
    ])
    toast.add({ title: 'Dictionary deleted', color: 'success' })
    await navigateTo('/dictionaries')
  } catch (error: unknown) {
    toast.add({ title: 'Failed to delete dictionary', description: extractApiErrorMessage(error, 'Failed to delete dictionary'), color: 'error' })
  } finally {
    toast.remove(progressToast.id)
    isDeleting.value = false
  }
}

async function exportDictionary() {
  if (isExporting.value) return

  const progressToast = toast.add({
    title: 'Exporting dictionary',
    description: name.value || 'Dictionary',
    color: 'neutral',
    icon: 'i-lucide-loader-circle',
    ui: { icon: 'animate-spin' },
    close: false,
    progress: false,
    duration: 0
  })

  try {
    isExporting.value = true
    const response = await fetch(`/api/workspaces/${workspaceId.value}/toolkit/export`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ selectors: [{ type: 'DICTIONARY', ids: [id] }] })
    })
    if (!response.ok) {
      throw new Error(`Export failed (${response.status})`)
    }
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${name.value || 'dictionary'}.larex-toolkit.json`
    link.click()
    window.URL.revokeObjectURL(url)
    toast.add({ title: 'Dictionary exported', color: 'success' })
  } catch (error: unknown) {
    toast.add({ title: 'Failed to export dictionary', description: extractApiErrorMessage(error, 'Failed to export dictionary'), color: 'error' })
  } finally {
    toast.remove(progressToast.id)
    isExporting.value = false
  }
}

function openImportDictionaryDialog() {
  if (!canEditDictionary.value || isBusy.value) return
  importDictionaryInput.value?.click()
}

async function importEntries(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  if (isImporting.value || isDeleting.value) {
    target.value = ''
    return
  }

  isImporting.value = true
  importProgress.value = null
  importStage.value = 'uploading'

  try {
    if (file.name.toLowerCase().endsWith('.larex-toolkit.json')) {
      const result = await runTrackedProcessing<{
        resources?: Array<{ type: string, targetId: string, targetName: string }>
      }>({
        title: 'Importing dictionary package',
        workspaceId: workspaceId.value,
        files: [{ file }],
        onStageChange: stage => (importStage.value = stage),
        onProgress: progress => (importProgress.value = progress),
        task: async () => {
          const content = await file.text()
          return await $fetch<{
            resources?: Array<{ type: string, targetId: string, targetName: string }>
          }>(`/api/workspaces/${workspaceId.value}/toolkit/import`, {
            method: 'POST',
            body: { content }
          })
        }
      })

      const imported = result.resources?.find(resource => resource.type === 'DICTIONARY')
      await refreshNuxtData(dictionariesListKey.value)
      if (imported?.targetId) {
        toast.add({
          title: 'Dictionary imported',
          description: imported.targetName ? `Imported as "${imported.targetName}"` : undefined,
          color: 'success'
        })
        notifyToolkitEditorSaved(imported.targetId)
        await navigateTo(toolkitEditorRoute(`/dictionaries/${imported.targetId}`))
      }
      return
    }

    const form = new FormData()
    form.append('file', file)

    if (isNew) {
      form.append('name', name.value.trim() || 'Imported Dictionary')
      if (description.value.trim()) {
        form.append('description', description.value.trim())
      }
      for (const tag of tags.value.filter(tag => tag.trim().length > 0)) {
        form.append('tags', tag.trim())
      }
      form.append('caseSensitive', String(caseSensitive.value))
      form.append('unicodeNormalization', unicodeNormalization.value)
      form.append('locked', String(locked.value))

      const created = await uploadFormDataWithProgress<Dictionary>({
        title: 'Importing dictionary',
        workspaceId: workspaceId.value,
        files: [{ file }],
        url: `/api/upload-proxy/workspaces/${workspaceId.value}/dictionaries/import`,
        formData: form,
        onProgress: (progress) => {
          importProgress.value = progress
        },
        onStageChange: (stage) => {
          importStage.value = stage
        }
      })
      await Promise.all([
        refreshNuxtData(dictionariesListKey.value),
        refreshNuxtData(dictionaryKey.value)
      ])
      toast.add({
        title: 'Dictionary imported',
        description: `Created "${created.name}"`,
        color: 'success'
      })
      notifyToolkitEditorSaved(created.id)
      await navigateTo(toolkitEditorRoute(`/dictionaries/${created.id}`))
      return
    }

    form.append('mode', 'APPEND')
    await uploadFormDataWithProgress({
      title: 'Importing dictionary entries',
      workspaceId: workspaceId.value,
      files: [{ file }],
      url: `/api/upload-proxy/workspaces/${workspaceId.value}/dictionaries/${id}/import`,
      formData: form,
      onProgress: (progress) => {
        importProgress.value = progress
      },
      onStageChange: (stage) => {
        importStage.value = stage
      }
    })
    await Promise.all([
      entryBrowserRef.value?.refresh(false) ?? Promise.resolve(),
      refreshDictionaryState(),
      refreshNuxtData(dictionaryKey.value),
      refreshNuxtData(dictionariesListKey.value)
    ])
    toast.add({ title: 'Dictionary import complete', color: 'success' })
    notifyToolkitEditorSaved(id)
  } catch (error: unknown) {
    toast.add({ title: 'Failed to import dictionary entries', description: extractApiErrorMessage(error, 'Failed to import dictionary entries'), color: 'error' })
  } finally {
    importProgress.value = null
    importStage.value = 'idle'
    isImporting.value = false
    target.value = ''
  }
}

const actionItems = computed<DropdownMenuItem[]>(() => {
  const items: DropdownMenuItem[] = []

  if (canEditDictionary.value) {
    items.push({
      label: isNew ? 'Import dictionary file' : 'Import entries',
      icon: 'i-lucide-upload',
      disabled: isBusy.value,
      onSelect: openImportDictionaryDialog
    })
  }

  if (!isNew) {
    items.push({
      label: 'Export package',
      icon: 'i-lucide-download',
      disabled: isBusy.value,
      onSelect: exportDictionary
    })

    if (canShareDictionary.value) {
      items.push({
        label: 'Share dictionary',
        icon: 'i-lucide-share-2',
        disabled: isBusy.value,
        onSelect: () => shareSlideover.open({
          resourceId: id,
          resourceName: name.value,
          resourceType: 'DICTIONARY',
          currentWorkspaceId: workspaceId.value
        })
      })
    }

    if (canDeleteDictionary.value) {
      items.push({
        label: 'Delete dictionary',
        icon: 'i-lucide-trash',
        color: 'error' as const,
        disabled: isBusy.value,
        onSelect: deleteDictionary
      })
    }
  }

  return items
})

async function handleEntriesChanged() {
  await Promise.all([
    refreshDictionaryState(),
    refreshNuxtData(dictionaryKey.value),
    refreshNuxtData(dictionariesListKey.value)
  ])
}

function handleEntryStats(payload: { totalEntries: number }) {
  entryCount.value = Number(payload.totalEntries ?? 0)
}

const emptyStateActions = computed<Array<{ label: string, icon: string, color: 'neutral', variant: 'solid', onClick: () => void }>>(() => {
  if (!canEditDictionary.value || isBusy.value) return []

  return [{
    label: isNew ? 'Import Dictionary' : 'Import Entries',
    icon: 'i-lucide-upload',
    color: 'neutral',
    variant: 'solid',
    onClick: openImportDictionaryDialog
  }]
})
</script>

<template>
  <UDashboardPanel
    :id="`dictionary-${id}`"
    :class="isEmbeddedToolkitEditor ? 'h-screen min-h-0 overflow-hidden' : undefined"
    :ui="{ body: isEmbeddedToolkitEditor ? 'p-0 sm:p-0 min-h-0 overflow-hidden' : 'p-0 sm:p-0' }"
  >
    <template #header>
      <UDashboardNavbar :title="isEmbeddedToolkitEditor ? undefined : (isNew ? 'Create Dictionary' : 'Edit Dictionary')">
        <template #leading>
          <LazyUDashboardSidebarCollapse v-if="!isEmbeddedToolkitEditor" />
        </template>
        <template #right>
          <input
            ref="importDictionaryInput"
            type="file"
            class="hidden"
            accept=".txt,.csv,.tsv,.json,.xml,.tei,text/plain,text/csv,application/json,application/xml,text/xml"
            @change="importEntries"
          >
          <div class="flex items-center gap-2">
            <ToolkitHelpPopover
              title="About Dictionaries"
              description="Dictionaries store accepted surface forms for QA, editor suggestions, and project-specific spelling control."
              :items="[
                'Import TXT, CSV, TSV, JSON, or TEI source data into the dictionary entry browser.',
                'Tune case sensitivity and Unicode normalization so lookup behavior matches your corpus.',
                'Share and export dictionaries as reusable workspace toolkit resources.'
              ]"
            />
            <UFieldGroup>
              <UButton
                label="Save"
                color="neutral"
                variant="outline"
                icon="i-lucide-save"
                :loading="isSaving"
                :disabled="!canEditDictionary || isBusy"
                @click="saveDictionary"
              />

              <UDropdownMenu :items="actionItems" :content="{ align: 'end' }">
                <UButton
                  color="neutral"
                  variant="outline"
                  icon="i-lucide-chevron-down"
                  :loading="isDeleting || isExporting"
                  :disabled="isBusy"
                />
              </UDropdownMenu>
            </UFieldGroup>
          </div>
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar v-if="!isEmbeddedToolkitEditor">
        <template #left>
          <UBreadcrumb :items="breadcrumbItems" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="h-full min-h-0 flex overflow-hidden">
        <aside class="w-80 shrink-0 border-r border-neutral-200 dark:border-neutral-700 bg-neutral-50/30 dark:bg-neutral-800/50 overflow-y-auto">
          <div class="p-4 lg:p-5 space-y-5">
            <div class="space-y-1">
              <h2 class="text-sm font-semibold">
                Metadata
              </h2>
              <p class="text-xs text-muted">
                Configure the dictionary definition and editor behavior.
              </p>
            </div>

            <UFormField label="Name">
              <UInput v-model="name" :disabled="!canEditDictionary" />
            </UFormField>

            <UFormField label="Description">
              <UTextarea v-model="description" :disabled="!canEditDictionary" :rows="4" />
            </UFormField>

            <UFormField label="Tags">
              <UInputTags v-model="tags" :disabled="!canEditDictionary" />
            </UFormField>

            <UFormField label="Unicode Normalization">
              <USelect v-model="unicodeNormalization" :items="['NFC', 'NFD', 'NFKC', 'NFKD']" :disabled="!canEditDictionary" />
            </UFormField>

            <div class="grid gap-4 sm:grid-cols-2">
              <UFormField label="Case Sensitive">
                <USwitch v-model="caseSensitive" :disabled="!canEditDictionary" />
              </UFormField>
              <UFormField label="Lock Editor Additions">
                <USwitch v-model="locked" :disabled="!canEditDictionary" />
              </UFormField>
            </div>

            <p class="text-xs text-muted">
              Prevent adding new words to this dictionary from within the editor.
            </p>

            <div class="border-t border-neutral-200 dark:border-neutral-700 pt-4 space-y-3 text-sm">
              <div class="flex items-center justify-between gap-3">
                <span class="text-muted">Entries</span>
                <span class="font-medium tabular-nums">{{ entryCount }}</span>
              </div>
              <div class="flex items-center justify-between gap-3">
                <span class="text-muted">State</span>
                <UBadge :color="hasEntries ? 'success' : 'warning'" variant="soft">
                  {{ hasEntries ? 'Loaded' : 'Empty' }}
                </UBadge>
              </div>
            </div>
          </div>
        </aside>

        <section class="flex-1 bg-neutral-50/70 dark:bg-neutral-900 flex flex-col min-w-0 overflow-hidden">
          <div
            v-if="hasEntries && !isNew"
            class="flex items-center justify-between gap-3 border-b border-default px-4 py-3 lg:px-5"
          >
            <div class="min-w-0">
              <h2 class="text-base font-semibold truncate">
                Dictionary Entries
              </h2>
              <p class="text-sm text-muted">
                Browse, search, and maintain accepted surface forms.
              </p>
            </div>
            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-upload"
              :loading="isImporting"
              :disabled="!canEditDictionary || isBusy"
              @click="openImportDictionaryDialog"
            >
              {{
                isImporting
                  ? (importStage === 'processing'
                    ? 'Processing...'
                    : (importProgress !== null ? `Uploading ${importProgress}%` : 'Uploading...'))
                  : 'Import'
              }}
            </UButton>
          </div>

          <div class="flex-1 min-h-0 overflow-hidden">
            <div v-if="!hasEntries" class="h-full flex items-center justify-center p-6">
              <UEmpty
                icon="i-lucide-book-copy"
                :title="isNew ? 'Import a Dictionary to Get Started' : 'No Dictionary Entries Yet'"
                :description="isNew
                  ? 'Fill in the metadata in the sidebar, then import a TXT, CSV, TSV, JSON, or TEI file.'
                  : 'This dictionary exists, but it does not contain any accepted forms yet.'"
                :actions="emptyStateActions"
              />
            </div>

            <div v-else-if="!isNew" class="h-full p-4 lg:p-5 overflow-hidden">
              <DictionaryEntryBrowser
                ref="entryBrowserRef"
                :workspace-id="workspaceId"
                :dictionary-id="id"
                :editable="canEditDictionary"
                height-class="h-full"
                @changed="handleEntriesChanged"
                @stats="handleEntryStats"
              />
            </div>
          </div>
        </section>
      </div>
    </template>
  </UDashboardPanel>
</template>
