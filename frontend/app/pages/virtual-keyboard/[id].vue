<script setup lang="ts">
import type { KeyboardLayout } from '@/types/virtual-keyboard'
import type { DropdownMenuItem } from '@nuxt/ui'
import { DEFAULT_RESOURCE_CAPABILITIES, type ResourceCapabilities } from '@/types/capabilities'
import { LazyUiDeleteSlideover, LazyShareSlideover } from '#components'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const overlay = useOverlay()
const backgroundDownloads = useBackgroundDownloads()
const shareSlideover = overlay.create(LazyShareSlideover)
const { allow } = useActionVisibility()

const { selectedWorkspace } = await useWorkspaceBootstrap()
const workspaceId = computed(() => selectedWorkspace.value ?? '')

const id = route.params.id as string
const isNew = id === 'new'
const isEmbeddedToolkitEditor = computed(() => route.query.embedded === 'toolkit-editor')

const keyboardsKey = computed(() => wsKey(workspaceId.value, 'virtual-keyboards', 'list'))
const keyboardKey = computed(() => wsKey(workspaceId.value, 'virtual-keyboards', id))
const loadedCapabilities = ref<ResourceCapabilities | null>(null)

const breadcrumbItems = computed(() => [
  {
    label: 'Home',
    icon: 'i-lucide-home',
    to: '/'
  },
  {
    label: 'Virtual Keyboards',
    icon: 'i-lucide-keyboard',
    to: '/virtual-keyboard'
  },
  {
    label: isNew ? 'New Keyboard' : (builderState.layoutName.value || id)
  }
])

function toolkitEditorRoute(path: string) {
  return isEmbeddedToolkitEditor.value ? { path, query: { embedded: 'toolkit-editor' } } : path
}

function notifyToolkitEditorSaved(resourceId: string) {
  if (!import.meta.client || !isEmbeddedToolkitEditor.value) return
  window.parent?.postMessage({
    type: 'larex:toolkit-resource-saved',
    resourceType: 'virtual-keyboard',
    id: resourceId
  }, window.location.origin)
}

const defaultLayout: KeyboardLayout = {
  id: '',
  name: 'New Keyboard',
  description: '',
  tags: [],
  cols: 10,
  rows: 4,
  items: []
}

let initialLayout = defaultLayout

if (!isNew) {
  const { data, error } = await useFetch<KeyboardLayout>(() => `/api/workspaces/${selectedWorkspace.value}/virtual-keyboards/${id}`, {
    key: keyboardKey
  })
  if (data.value) {
    initialLayout = data.value
    loadedCapabilities.value = data.value.capabilities ?? null
  } else if (error.value) {
    toast.add({ title: 'Error loading keyboard', color: 'error' })
    router.push('/virtual-keyboard')
  }
}
const keyboardCapabilities = computed(() => ({
  ...DEFAULT_RESOURCE_CAPABILITIES,
  ...(loadedCapabilities.value ?? {})
}))
const canEditKeyboard = computed(() => isNew || allow(keyboardCapabilities.value.canEdit))
const canShareKeyboard = computed(() => !isNew && allow(keyboardCapabilities.value.canShare))
const canDeleteKeyboard = computed(() => !isNew && allow(keyboardCapabilities.value.canDelete))

const builderState = useVirtualKeyboardBuilder(initialLayout)

const activeTab = ref<'builder' | 'preview'>('builder')

const tabs = [
  {
    label: 'Builder',
    icon: 'i-lucide-blocks',
    value: 'builder'
  },
  {
    label: 'Preview',
    icon: 'i-lucide-scan-eye',
    value: 'preview'
  }
]

const availableLayouts = computed(() => [builderState.currentLayout.value])

const handleSave = async () => {
  if (!canEditKeyboard.value) return
  try {
    const layout = builderState.currentLayout.value
    if (isNew) {
      const saved = await $fetch<KeyboardLayout>(`/api/workspaces/${selectedWorkspace.value}/virtual-keyboards`, {
        method: 'POST',
        body: layout
      })
      toast.add({ title: 'Keyboard created', color: 'success' })
      refreshNuxtData(keyboardsKey.value)
      notifyToolkitEditorSaved(saved.id)
      await router.push(toolkitEditorRoute(`/virtual-keyboard/${saved.id}`))
    } else {
      await $fetch<KeyboardLayout>(`/api/workspaces/${selectedWorkspace.value}/virtual-keyboards/${id}`, {
        method: 'PUT',
        body: layout
      })
      toast.add({ title: 'Keyboard updated', color: 'success' })
      refreshNuxtData(keyboardsKey.value)
      refreshNuxtData(keyboardKey.value)
      notifyToolkitEditorSaved(id)
    }
  } catch {
    toast.add({ title: 'Error saving keyboard', color: 'error' })
  }
}

const handleExportLayout = () => {
  if (isNew) {
    void backgroundDownloads.runBackgroundJob({
      title: 'Downloading keyboard',
      subtitle: builderState.currentLayout.value.name,
      statusLabel: 'Preparing',
      completedLabel: 'Downloaded',
      icon: 'i-lucide-download',
      task: async (job) => {
        const data = builderState.currentLayout.value
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
        await backgroundDownloads.downloadBlob(blob, `${builderState.currentLayout.value.name.replace(/\s+/g, '-').toLowerCase()}-${Date.now()}.json`, job)
      }
    }).catch((error: unknown) => {
      const message = error instanceof Error ? error.message : 'Failed to export keyboard'
      toast.add({ title: 'Export failed', description: message, color: 'error' })
    })
    return
  }

  void (async () => {
    try {
      await backgroundDownloads.runBackgroundJob({
        title: 'Exporting keyboard',
        subtitle: builderState.layoutName.value,
        statusLabel: 'Generating',
        completedLabel: 'Exported',
        icon: 'i-lucide-keyboard',
        task: async (job) => {
          const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/toolkit/export`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              includeAll: false,
              selectors: [{ type: 'VIRTUAL_KEYBOARD', ids: [id] }]
            })
          })

          if (!response.ok) {
            throw new Error(`Export failed (${response.status})`)
          }

          await backgroundDownloads.downloadBlobResponse(response, `${builderState.layoutName.value.replace(/\s+/g, '-').toLowerCase()}.larex-toolkit.json`, job)
        }
      })

      toast.add({ title: 'Keyboard exported', color: 'success' })
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Failed to export keyboard package'
      toast.add({ title: 'Export failed', description: message, color: 'error' })
    }
  })()
}

const importFileInput = ref<HTMLInputElement | null>(null)

const openImportDialog = () => {
  if (!canEditKeyboard.value) return
  importFileInput.value?.click()
}

const handleImportLayout = async (event: Event) => {
  if (!canEditKeyboard.value) return
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  try {
    const { runTrackedProcessing } = useTrackedUpload()
    const result = await runTrackedProcessing<{
      resources?: Array<{ type: string, targetId: string, targetName: string }>
    }>({
      title: 'Importing keyboard package',
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

    const imported = result.resources?.find(r => r.type === 'VIRTUAL_KEYBOARD')
    await refreshNuxtData(keyboardsKey.value)
    if (imported?.targetId) {
      notifyToolkitEditorSaved(imported.targetId)
      await router.push(toolkitEditorRoute(`/virtual-keyboard/${imported.targetId}`))
    }

    toast.add({
      title: 'Keyboard imported',
      description: imported?.targetName ? `Imported as "${imported.targetName}"` : undefined,
      color: 'success'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to import keyboard package'
    toast.add({ title: 'Import failed', description: message, color: 'error' })
  } finally {
    input.value = ''
  }
}

const deleteConfirmSlideover = overlay.create(LazyUiDeleteSlideover)

const handleDelete = async () => {
  if (!canDeleteKeyboard.value) return
  if (isNew) return

  const instance = deleteConfirmSlideover.open({
    name: builderState.layoutName.value,
    entityType: 'Keyboard'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/virtual-keyboards/${id}`, {
      method: 'DELETE'
    })
    toast.add({ title: 'Keyboard deleted', color: 'success' })
    refreshNuxtData(keyboardsKey.value)
    await router.push('/virtual-keyboard')
  } catch {
    toast.add({ title: 'Error deleting keyboard', color: 'error' })
  }
}

async function handleShare() {
  if (!canShareKeyboard.value) return

  const instance = shareSlideover.open({
    resourceId: id,
    resourceName: builderState.layoutName.value,
    resourceType: 'VIRTUAL_KEYBOARD',
    currentWorkspaceId: workspaceId.value
  })
  const transferred = await instance.result
  if (transferred) {
    await refreshNuxtData(keyboardsKey.value)
  }
}

const actionItems = computed<DropdownMenuItem[]>(() => {
  const items: DropdownMenuItem[] = [
    {
      label: 'Export layout package',
      icon: 'i-lucide-download',
      onSelect: handleExportLayout
    }
  ]

  if (canEditKeyboard.value) {
    items.unshift({
      label: 'Import layout package',
      icon: 'i-lucide-upload',
      onSelect: openImportDialog
    })
  }

  if (!isNew) {
    if (canShareKeyboard.value) {
      items.push({
        label: 'Share keyboard',
        icon: 'i-lucide-share-2',
        onSelect: () => { void handleShare() }
      })
    }
    if (canDeleteKeyboard.value) {
      items.push({
        label: 'Delete keyboard',
        icon: 'i-lucide-trash',
        color: 'error' as const,
        onSelect: handleDelete
      })
    }
  }

  return items
})
</script>

<template>
  <UDashboardPanel
    :class="isEmbeddedToolkitEditor ? 'h-screen min-h-0 overflow-hidden' : undefined"
    :ui="{ body: isEmbeddedToolkitEditor ? 'p-0 sm:p-0 min-h-0 overflow-hidden' : 'p-0 sm:p-0' }"
  >
    <template #header>
      <UDashboardNavbar data-tour="vk-builder-header" :title="isEmbeddedToolkitEditor ? undefined : (isNew ? 'Create Keyboard' : 'Edit Keyboard')">
        <template #right>
          <input
            ref="importFileInput"
            type="file"
            class="hidden"
            accept=".json,application/json"
            @change="handleImportLayout"
          >
          <div class="flex items-center gap-2">
            <ToolkitHelpPopover
              title="About Virtual Keyboards"
              description="Virtual keyboards provide reusable on-screen input layouts for transcription and special-character entry."
              :items="[
                'Design key layouts for the character repertoire your project needs most often.',
                'Use the preview tab to test typing behavior before assigning a keyboard to projects.',
                'Import, export, and share layouts as reusable workspace toolkit resources.'
              ]"
            />
            <UFieldGroup>
              <UButton
                label="Save"
                color="neutral"
                variant="outline"
                icon="i-lucide-save"
                :disabled="!canEditKeyboard"
                @click="handleSave"
              />

              <UDropdownMenu :items="actionItems" :content="{ align: 'end' }">
                <UButton
                  color="neutral"
                  variant="outline"
                  icon="i-lucide-chevron-down"
                />
              </UDropdownMenu>
            </UFieldGroup>
          </div>
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UBreadcrumb v-if="!isEmbeddedToolkitEditor" :items="breadcrumbItems" />
        </template>
        <template #right>
          <UTabs
            v-model="activeTab"
            :items="tabs"
            :content="false"
            :ui="{
              root: 'gap-0'
            }"
          />
        </template>
      </UDashboardToolbar>
    </template>
    <template #body>
      <div class="h-full min-h-0 flex overflow-hidden">
        <VirtualKeyboardBuilderSidebar
          v-if="activeTab === 'builder'"
          :state="builderState"
        />

        <section class="flex-1 min-h-0 bg-neutral-50/70 dark:bg-neutral-900 flex flex-col relative">
          <VirtualKeyboardBuilder
            v-if="activeTab === 'builder'"
            :state="builderState"
          />

          <div v-else-if="activeTab === 'preview'" class="flex-1 p-4">
            <div class="space-y-4">
              <h2 class="text-xl tracking-wider font-bold">
                Test Input
              </h2>
              <UInput type="text" placeholder="Focus me..." class="font-junicode" />
              <UTextarea placeholder="Or me..." class="font-junicode" />
            </div>

            <VirtualKeyboard
              :layout="builderState.currentLayout.value"
              :layouts="availableLayouts"
              @update:layout-id="builderState.layoutId.value = $event"
            />
          </div>
        </section>
      </div>
    </template>
  </UDashboardPanel>
</template>
