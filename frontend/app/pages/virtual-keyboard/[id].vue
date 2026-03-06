<script setup lang="ts">
import type { KeyboardLayout, BoardTheme } from '@/types/virtual-keyboard'
import { useVirtualKeyboardBuilder } from '@/composables/use-virtual-keyboard-builder'
import { DEFAULT_RESOURCE_CAPABILITIES } from '@/types/capabilities'
import type { DropdownMenuItem } from '@nuxt/ui'
import { wsKey } from '@/utils/fetch-keys'
import { LazyUiDeleteSlideover, LazyShareSlideover } from '#components'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const overlay = useOverlay()
const shareSlideover = overlay.create(LazyShareSlideover)
const { allow } = useActionVisibility()

const workspace = useWorkspaceStore()

if (!workspace.hasFetched) {
  await workspace.fetchWorkspaces()
}

const selectedWorkspace = computed(() => workspace.selectedWorkspaceId as string)

const id = route.params.id as string
const isNew = id === 'new'

const keyboardsKey = computed(() => wsKey(selectedWorkspace.value, 'virtual-keyboards', 'list'))
const keyboardKey = computed(() => wsKey(selectedWorkspace.value, 'virtual-keyboards', id))
const themesKey = computed(() => wsKey(selectedWorkspace.value, 'board-themes', 'list'))
const loadedCapabilities = ref<{ canEdit: boolean, canDelete: boolean } | null>(null)

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
const canDeleteKeyboard = computed(() => !isNew && allow(keyboardCapabilities.value.canDelete))

const builderState = useVirtualKeyboardBuilder(initialLayout)

const { data: themes } = await useFetch<BoardTheme[]>(() => `/api/workspaces/${selectedWorkspace.value}/board-themes`, {
  key: themesKey,
  default: () => []
})
const defaultTheme: BoardTheme = {
  name: 'Dark',
  bgClass: 'bg-neutral-900',
  borderClass: 'border-neutral-700',
  gridLineClass: 'border-neutral-800',
  keyBgClass: 'bg-neutral-800',
  keyTextClass: 'text-neutral-200',
  previewClass: 'bg-neutral-900'
}
const boardThemes = computed({
  get: () => themes.value ?? [],
  set: (val) => { themes.value = val }
})

const resolveTheme = (themeId?: string) => {
  if (themeId) {
    const found = boardThemes.value.find(t => t.id === themeId)
    if (found) return found
  }
  return boardThemes.value[0] || defaultTheme
}

const currentTheme = ref<BoardTheme>(resolveTheme(initialLayout.themeId))

watch(currentTheme, (theme) => {
  builderState.themeId.value = theme.id
})

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
      await router.push(`/virtual-keyboard/${saved.id}`)
    } else {
      await $fetch<KeyboardLayout>(`/api/workspaces/${selectedWorkspace.value}/virtual-keyboards/${id}`, {
        method: 'PUT',
        body: layout
      })
      toast.add({ title: 'Keyboard updated', color: 'success' })
      refreshNuxtData(keyboardsKey.value)
      refreshNuxtData(keyboardKey.value)
    }
  } catch {
    toast.add({ title: 'Error saving keyboard', color: 'error' })
  }
}

const handleExportLayout = () => {
  if (isNew) {
    const data = {
      ...builderState.currentLayout.value,
      boardTheme: currentTheme.value.name
    }
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${builderState.currentLayout.value.name.replace(/\s+/g, '-').toLowerCase()}-${Date.now()}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    return
  }

  void (async () => {
    try {
      const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/utilities/export`, {
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

      const blob = await response.blob()
      const fallbackName = `${builderState.layoutName.value.replace(/\s+/g, '-').toLowerCase()}.larex-utilities.json`
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
    const content = await file.text()
    const result = await $fetch<{
      resources?: Array<{ type: string, targetId: string, targetName: string }>
    }>(`/api/workspaces/${selectedWorkspace.value}/utilities/import`, {
      method: 'POST',
      body: { content }
    })

    const imported = result.resources?.find(r => r.type === 'VIRTUAL_KEYBOARD')
    await refreshNuxtData(keyboardsKey.value)
    if (imported?.targetId) {
      await router.push(`/virtual-keyboard/${imported.targetId}`)
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
    if (canEditKeyboard.value) {
      items.push({
      label: 'Share keyboard',
      icon: 'i-lucide-share-2',
      onSelect: () => shareSlideover.open({ resourceId: id, resourceName: builderState.layoutName.value, resourceType: 'VIRTUAL_KEYBOARD', currentWorkspaceId: selectedWorkspace.value })
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
  <UDashboardPanel :ui="{ body: 'p-0 sm:p-0' }">
    <template #header>
      <UDashboardNavbar data-tour="vk-builder-header" :title="isNew ? 'Create Keyboard' : 'Edit Keyboard'">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <input
            ref="importFileInput"
            type="file"
            class="hidden"
            accept=".json,application/json"
            @change="handleImportLayout"
          >
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
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UBreadcrumb :items="breadcrumbItems" />
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
      <div class="h-full flex overflow-hidden">
        <VirtualKeyboardBuilderSidebar
          v-if="activeTab === 'builder'"
          :state="builderState"
          :themes="boardThemes"
          :active-theme="currentTheme"
          @update:active-theme="currentTheme = $event"
          @update:themes="boardThemes = $event"
        />

        <section class="flex-1 bg-neutral-50/70 dark:bg-neutral-900 flex flex-col relative">
          <VirtualKeyboardBuilder
            v-if="activeTab === 'builder'"
            :state="builderState"
            :theme="currentTheme"
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
              :theme="currentTheme"
              :layouts="availableLayouts"
              @update:layout-id="builderState.layoutId.value = $event"
            />
          </div>
        </section>
      </div>
    </template>
  </UDashboardPanel>
</template>
